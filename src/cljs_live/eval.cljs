(ns cljs-live.eval
  (:require [cljs.js :as cljs]
            [cljs.tools.reader :as r]
            [cljs.analyzer :refer [*cljs-warning-handlers*]]
            [cljs-live.compiler :as c]
            [cljs.repl :refer [print-doc]]
            [cljs.tools.reader.reader-types :as rt]
            [clojure.string :as string])
  (:require-macros [cljs-live.eval :refer [defspecial]]))

(def ^:dynamic *cljs-warnings* nil)

(defonce c-state (cljs/empty-state))
(defonce c-env (atom {:ns (symbol "cljs.user")}))

(def repl-specials {}
  #_{'ns       wrap-ns
     'in-ns    in-ns
     'with-ns  with-ns
     'doc      doc
     'defmacro wrap-defmacro})

(defn swap-repl-specials!
  "Mutate repl specials available to the eval fns in this namespace."
  [f & args]
  (set! repl-specials (apply f repl-specials args)))

(defn c-opts
  [c-state env]
  {:load          (partial c/load-fn c-state)
   :eval          cljs/js-eval
   :ns            (:ns @env)
   :context       :expr
   :source-map    true
   :def-emits-var true})

(defn read-string-indexed
  "Read string using indexing-push-back-reader, for errors with location information."
  [s]
  (when (and s (not= "" s))
    (let [reader (rt/indexing-push-back-reader s)]
      (loop [forms []]
        (let [form (r/read {:eof ::eof} reader)]
          (if (= form ::eof)
            forms
            (recur (conj forms form))))))))

(defn get-ns [c-state ns] (get-in @c-state [:cljs.analyzer/namespaces ns]))

(defn toggle-macros-ns [sym]
  (let [s (str sym)]
    (symbol
      (if (string/ends-with? s "$macros")
        (string/replace s "$macros" "")
        (str s "$macros")))))

(defn resolve-var
  ([sym] (resolve-var c-state c-env sym))
  ([c-state c-env sym]
   (binding [cljs.env/*compiler* c-state]
     (cljs.analyzer/resolve-var (assoc @cljs.env/*compiler* :ns (get-ns c-state (or (:ns @c-env) 'cljs.user))) sym))))

(defn resolve-symbol
  ([sym] (resolve-symbol c-state c-env sym))
  ([c-state c-env sym]
   (:name (resolve-var c-state c-env sym))))

(declare eval eval-forms)

(defn ensure-ns
  "Create namespace if it doesn't exist"
  [c-state c-env ns]
  (when-not (contains? (get @c-state :cljs.analyzer/namespaces) ns)
    (let [prev-ns (:ns @c-env)]
      (eval c-state c-env `(~'ns ~ns))
      (swap! c-env assoc :ns prev-ns))))

(defn ->macro-sym [sym]
  (let [ns? (namespace sym)
        ns (or ns? (name sym))
        name (if ns? (name ns) nil)]
    (if (string/ends-with? ns "$macros")
      ns
      (if ns? (symbol (str ns "$macros") name)
              (symbol (str ns "$macros"))))))

(defspecial ns
            "Wrap ns statements to include :ns key in result.
            (May become unnecessary if cljs.js/eval returns :ns in result.)"
            [c-state c-env & body]
            (let [result (eval c-state c-env (with-meta (cons 'ns body) {::skip-repl-special true}))]
              (cond-> result
                      (contains? result :value) (assoc :ns (first body)))))

(defspecial in-ns
            "Switch to a different namespace"
            [c-state c-env ns]
            (when-not (symbol? ns) (throw (js/Error. "`in-ns` must be passed a symbol.")))
            (ensure-ns c-state c-env ns)
            (swap! c-env assoc :ns ns)
            {:value nil
             :ns    ns})

(defspecial with-ns
            "Execute body within temp-ns namespace, then return to previous namespace. Create namespace if it doesn't exist."
            [c-state c-env temp-ns & body]
            (ensure-ns c-state c-env temp-ns)
            (let [ns (:ns @c-env)
                  _ (swap! c-env assoc :ns temp-ns)
                  result (eval-forms c-state c-env body)]
              (swap! c-env assoc :ns ns)
              result))

(defspecial doc
            "Show doc for symbol"
            [c-state c-env name]
            (let [[namespace name] (let [name (resolve-symbol name)]
                                     (map symbol [(namespace name) (clojure.core/name name)]))]
              {:value
               (with-out-str
                 (some-> (get-in @c-state [:cljs.analyzer/namespaces namespace :defs name])
                         (select-keys [:name :doc :arglists])
                         print-doc)
                 "Not found")}))

(defn repl-special [c-state c-env body]
  (when (not (::skip-repl-special (meta body)))
    (when-let [f (get repl-specials (first body))]
      (when-not (contains? (meta body) ::skip-repl-special)
        (try (f c-state c-env body)
             (catch js/Error e {:error e}))))))

(defn warning-handler
  "Collect warnings in a dynamic var"
  [form warning-type env extra]
  (some-> *cljs-warnings*
          (swap! conj {:type        warning-type
                       :env         env
                       :extra       extra
                       :source-form form})))

(defn eval
  "Eval a single form, keeping track of current ns in c-env"
  ([form] (eval c-state c-env form))
  ([c-state c-env form] (eval c-state c-env form {}))
  ([c-state c-env form opts]
   (let [is-seq? (seq? form)
         {:keys [value ns] :as result} (or (and is-seq? (repl-special c-state c-env form))
                                           (let [result (atom)]
                                             (binding [*cljs-warning-handlers* [(partial warning-handler form)]
                                                       r/*data-readers* (conj r/*data-readers* {'js identity})]
                                               (try (cljs/eval c-state form (merge (c-opts c-state c-env) opts) (partial swap! result merge))
                                                    (catch js/Error e (swap! result assoc :error e))))
                                             @result))]

     (when (and (contains? result :ns) (not= ns (:ns @c-env)))
       (swap! c-env assoc :ns ns))

     result)))

(defn read-src
  "Read src using default tools.reader. If an error is encountered,
  re-read an unwrapped version of src using indexed reader to return
  a correct error location."
  [c-state c-env src]
  (binding [r/resolve-symbol #(resolve-symbol c-state c-env %)
            r/*data-readers* (conj r/*data-readers* {'js identity})]
    (try {:value (read-string-indexed src)}
         (catch js/Error e
           {:error e}))))

(defn eval-forms
  "Eval a list of forms"
  [c-state c-env forms]
  (binding [*cljs-warnings* (or *cljs-warnings* (atom []))]
    (loop [forms forms]
      (let [{:keys [error] :as result} (eval c-state c-env (first forms))
            remaining (rest forms)]
        (if (or error (empty? remaining))
          (assoc result :warnings @*cljs-warnings*)
          (recur remaining))))))

(defn eval-str
  "Eval string by first reading all top-level forms, then eval'ing them one at a time."
  ([src] (eval-str c-state c-env src))
  ([c-state c-env src]
   (let [{:keys [error value] :as result} (read-src c-state c-env src)]
     (if error
       result
       (eval-forms c-state c-env value)))))