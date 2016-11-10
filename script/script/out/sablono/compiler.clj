(ns sablono.compiler
  (:require [cljs.compiler :as cljs]
            [sablono.normalize :as normalize]
            [sablono.util :refer :all])
  ;; TODO: Fix emit-constant exception for JSValue.
  ;; Require of cljs.tagged_literals causes trouble, but a require of
  ;; cljs.compiler seems to work. Also, switching JSValue to a record
  ;; in ClojureScript seems to fix the problem.
  (:import cljs.tagged_literals.JSValue))

(defprotocol ICompile
  (compile-react [this] "Compile a Clojure data structure into a React fn call."))

(defprotocol IJSValue
  (to-js [x]))

(defn compile-map-attr [name value]
  {name
   (if (map? value)
     (to-js value)
     `(~'clj->js ~value))})

(defmulti compile-attr (fn [name value] name))

(defmethod compile-attr :class [name value]
  {:class
   (cond
     (or (nil? value)
         (keyword? value)
         (string? value))
     value
     (and (or (sequential? value)
              (set? value))
          (every? string? value))
     (join-classes value)
     :else `(sablono.util/join-classes ~value))})

(defmethod compile-attr :style [name value]
  (compile-map-attr name (camel-case-keys value)))

(defmethod compile-attr :default [name value]
  {name (to-js value)})

(defn compile-attrs
  "Compile a HTML attribute map."
  [attrs]
  (->> (seq attrs)
       (map #(apply compile-attr %1))
       (apply merge)
       (html-to-dom-attrs)
       (to-js)))

(defn compile-merge-attrs [attrs-1 attrs-2]
  (let [empty-attrs? #(or (nil? %1) (and (map? %1) (empty? %1)))]
    (cond
      (and (empty-attrs? attrs-1)
           (empty-attrs? attrs-2))
      nil
      (empty-attrs? attrs-1)
      `(sablono.interpreter/attributes ~attrs-2)
      (empty-attrs? attrs-2)
      `(sablono.interpreter/attributes ~attrs-1)
      (and (map? attrs-1)
           (map? attrs-2))
      (normalize/merge-with-class attrs-1 attrs-2)
      :else `(sablono.interpreter/attributes
              (sablono.normalize/merge-with-class ~attrs-1 ~attrs-2)))))

(defn compile-react-element
  "Render an element vector as a HTML element."
  [element]
  (let [[tag attrs content] (normalize/element element)]
    `(~(react-fn tag)
      ~(name tag)
      ~(compile-attrs attrs)
      ~@(if content (compile-react content)))))

(defn- unevaluated?
  "True if the expression has not been evaluated."
  [expr]
  (or (symbol? expr)
      (and (seq? expr)
           (not= (first expr) `quote))))

(defn- form-name
  "Get the name of the supplied form."
  [form]
  (if (and (seq? form) (symbol? (first form)))
    (name (first form))))

(declare compile-html)

(defmulti compile-form
  "Pre-compile certain standard forms, where possible."
  {:private true}
  form-name)

(defmethod compile-form "do"
  [[_ & forms]]
  `(do ~@(butlast forms) ~(compile-html (last forms))))

(defmethod compile-form "let"
  [[_ bindings & body]]
  `(let ~bindings ~@(butlast body) ~(compile-html (last body))))

(defmethod compile-form "let*"
  [[_ bindings & body]]
  `(let* ~bindings ~@(butlast body) ~(compile-html (last body))))

(defmethod compile-form "letfn*"
  [[_ bindings & body]]
  `(letfn* ~bindings ~@(butlast body) ~(compile-html (last body))))

(defmethod compile-form "for"
  [[_ bindings body]]
  `(~'into-array (for ~bindings ~(compile-html body))))

(defmethod compile-form "if"
  [[_ condition & body]]
  `(if ~condition ~@(for [x body] (compile-html x))))

(defmethod compile-form :default
  [expr]
  `(sablono.interpreter/interpret ~expr))

(defn- not-hint?
  "True if x is not hinted to be the supplied type."
  [x type]
  (if-let [hint (-> x meta :tag)]
    (not (isa? (eval hint) type))))

(defn- hint?
  "True if x is hinted to be the supplied type."
  [x type]
  (if-let [hint (-> x meta :tag)]
    (isa? (eval hint) type)))

(defn- literal?
  "True if x is a literal value that can be rendered as-is."
  [x]
  (and (not (unevaluated? x))
       (or (not (or (vector? x) (map? x)))
           (every? literal? x))))

(defn- not-implicit-map?
  "True if we can infer that x is not a map."
  [x]
  (or (= (form-name x) "for")
      (not (unevaluated? x))
      (not-hint? x java.util.Map)))

(defn- attrs-hint?
  "True if x has :attrs metadata. Treat x as a implicit map"
  [x]
  (-> x meta :attrs))

(defn- element-compile-strategy
  "Returns the compilation strategy to use for a given element."
  [[tag attrs & content :as element]]
  (cond
    (every? literal? element)
    ::all-literal                       ; e.g. [:span "foo"]
    (and (literal? tag) (map? attrs))
    ::literal-tag-and-attributes        ; e.g. [:span {} x]
    (and (literal? tag) (not-implicit-map? attrs))
    ::literal-tag-and-no-attributes     ; e.g. [:span ^String x]
    (and (literal? tag) (attrs-hint? attrs))
    ::literal-tag-and-hinted-attributes ; e.g. [:span ^:attrs y]
    (literal? tag)
    ::literal-tag                       ; e.g. [:span x]
    :else
    ::default))                         ; e.g. [x]

(declare compile-html)

(defmulti compile-element
  "Returns an unevaluated form that will render the supplied vector as a HTML
  element."
  {:private true}
  element-compile-strategy)

(defmethod compile-element ::all-literal
  [element]
  (compile-react-element (eval element)))

(defmethod compile-element ::literal-tag-and-attributes
  [[tag attrs & content]]
  (let [[tag attrs _] (normalize/element [tag attrs])]
    `(~(react-fn tag)
      ~(name tag)
      ~(compile-attrs attrs)
      ~@(map compile-html content))))

(defmethod compile-element ::literal-tag-and-no-attributes
  [[tag & content]]
  (compile-element (apply vector tag {} content)))

(defmethod compile-element ::literal-tag-and-hinted-attributes
  [[tag attrs & content]]
  (let [[tag tag-attrs _] (normalize/element [tag])
        attrs-sym (gensym "attrs")]
    `(let [~attrs-sym ~attrs]
       (apply ~(react-fn tag)
              ~(name tag)
              ~(compile-merge-attrs tag-attrs attrs-sym)
              ~(when-not (empty? content)
                 (mapv compile-html content))))))

(defmethod compile-element ::literal-tag
  [[tag attrs & content]]
  (let [[tag tag-attrs _] (normalize/element [tag])
        attrs-sym (gensym "attrs")]
    `(let [~attrs-sym ~attrs]
       (apply ~(react-fn tag)
              ~(name tag)
              (if (map? ~attrs-sym)
                ~(compile-merge-attrs tag-attrs attrs-sym)
                ~(compile-attrs tag-attrs))
              (if (map? ~attrs-sym)
                ~(when-not (empty? content)
                   (mapv compile-html content))
                ~(when attrs
                   (mapv compile-html (cons attrs-sym content))))))))

(defmethod compile-element :default
  [element]
  `(sablono.interpreter/interpret
    [~(first element)
     ~@(for [x (rest element)]
         (if (vector? x)
           (compile-element x)
           x))]))

(defn compile-html
  "Pre-compile data structures into HTML where possible."
  [content]
  (cond
    (vector? content) (compile-element content)
    (literal? content) content
    (hint? content String) content
    (hint? content Number) content
    :else (compile-form content)))

;; TODO: Remove when landed in ClojureScript.
(defmethod print-method JSValue
  [^JSValue v, ^java.io.Writer w]
  (.write w "#js ")
  (.write w (pr-str (.val v))))

(extend-protocol ICompile
  clojure.lang.IPersistentVector
  (compile-react [this]
    (if (element? this)
      (compile-react-element this)
      (compile-react (seq this))))
  clojure.lang.ISeq
  (compile-react [this]
    (map compile-react this))
  Object
  (compile-react [this]
    this)
  nil
  (compile-react [this]
    nil))

(defn- to-js-map
  "Convert a map into a JavaScript object."
  [m]
  (JSValue.
   (zipmap (keys m)
           (map to-js (vals m)))))

(extend-protocol IJSValue
  clojure.lang.Keyword
  (to-js [x]
    (name x))
  clojure.lang.PersistentArrayMap
  (to-js [x]
    (to-js-map x))
  clojure.lang.PersistentHashMap
  (to-js [x]
    (to-js-map x))
  clojure.lang.PersistentVector
  (to-js [x]
    (JSValue. (vec (map to-js x))))
  Object
  (to-js [x]
    x)
  nil
  (to-js [_]
    nil))
