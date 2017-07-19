;; Copyright (c) Rich Hickey. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns cljs-live.goog-deps
  (:require [clojure.string :as string]))

(defn distinct-by
  ([f coll]
   (let [step (fn step [xs seen]
                (lazy-seq
                  ((fn [[x :as xs] seen]
                     (when-let [s (seq xs)]
                       (let [v (f x)]
                         (if (contains? seen v)
                           (recur (rest s) seen)
                           (cons x (step (rest s) (conj seen v)))))))
                    xs seen)))]
     (step coll #{}))))

; taken from pomegranate/dynapath
; https://github.com/tobias/dynapath/blob/master/src/dynapath/util.clj


(defn parse-js-ns
  "Given the lines from a JavaScript source file, parse the provide
  and require statements and return them in a map. Assumes that all
  provide and require statements appear before the first function
  definition."
  [lines]
  (letfn [(conj-in [m k v] (update-in m [k] (fn [old] (conj old v))))]
    (->> (for [line lines x (string/split line #";")] x)
         (map string/trim)
         (take-while #(not (re-matches #".*=[\s]*function\(.*\)[\s]*[{].*" %)))
         (map #(re-matches #".*goog\.(provide|require)\(['\"](.*)['\"]\)" %))
         (remove nil?)
         (map #(drop 1 %))
         (reduce (fn [m ns]
                   (let [munged-ns (string/replace (last ns) "_" "-")]
                     (if (= (first ns) "require")
                       (conj-in m :requires munged-ns)
                       (conj-in m :provides munged-ns))))
                 {:requires [] :provides []}))))

(defprotocol IJavaScript
  (-foreign? [this] "Whether the Javascript represents a foreign
  library (a js file that not have any goog.provide statement")
  (-closure-lib? [this] "Whether the Javascript represents a Closure style
  library")
  (-url [this] "The URL where this JavaScript is located. Returns nil
  when JavaScript exists in memory only.")
  (-relative-path [this] "Relative path for this JavaScript.")
  (-provides [this] "A list of namespaces that this JavaScript provides.")
  (-requires [this] "A list of namespaces that this JavaScript requires.")
  (-source [this] "The JavaScript source string."))

(defn build-index
  "Index a list of dependencies by namespace and file name. There can
  be zero or more namespaces provided per file. Upstream foreign libraies
  will have their options merged with local foreign libraries to support
  fine-grained overriding."
  [deps]
  (reduce
    (fn [index dep]
      (let [provides (:provides dep)
            index'   (if (seq provides)
                       (reduce
                         (fn [index' provide]
                           (if (:foreign dep)
                             (update-in index' [provide] merge dep)
                             ;; when building the dependency index, we need to
                             ;; avoid overwriting a CLJS dep with a CLJC dep of
                             ;; the same namespace - António Monteiro
                             (let [file (when-let [f (or (:source-file dep) (:file dep))]
                                          (.toString f))
                                   ext (when file
                                         (.substring file (inc (.lastIndexOf file "."))))]
                               (update-in index' [provide]
                                          (fn [d]
                                            (if (and (= ext "cljc") (some? d))
                                              d
                                              dep))))))
                         index provides)
                       index)]
        (if (:foreign dep)
          (update-in index' [(:file dep)] merge dep)
          (assoc index' (:file dep) dep))))
    {} deps))

(defn dependency-order-visit
  ([state ns-name]
   (dependency-order-visit state ns-name []))
  ([state ns-name seen]
    #_(assert (not (some #{ns-name} seen))
              (str "Circular dependency detected, "
                   (apply str (interpose " -> " (conj seen ns-name)))))
   (if-not (some #{ns-name} seen)
     (let [file (get state ns-name)]
       (if (or (:visited file) (nil? file))
         state
         (let [state (assoc-in state [ns-name :visited] true)
               deps (:requires file)
               state (reduce #(dependency-order-visit %1 %2 (conj seen ns-name)) state deps)]
           (assoc state :order (conj (:order state) file)))))
     state)))

(defn- pack-string [s]
  (if (string? s)
    {:provides (-provides s)
     :requires (-requires s)
     :file (str "from_source_" (gensym) ".clj")
     ::original s}
    s))

(defn- unpack-string [m]
  (or (::original m) m))

(defn dependency-order
  "Topologically sort a collection of dependencies."
  [coll]
  (let [state (build-index (map pack-string coll))]
    (map unpack-string
         (distinct-by :provides
                      (:order (reduce dependency-order-visit (assoc state :order []) (keys state)))))))


;; Dependencies
;; ============
;;
;; Find all dependencies from files on the classpath. Eliminates the
;; need for closurebuilder. cljs dependencies will be compiled as
;; needed.

(defn js-dependency-index
  "Returns the index for all JavaScript dependencies. Lookup by
  namespace or file name."
  [goog-dependencies]
  ; (library-dependencies) will find all of the same libs returned by
  ; (goog-dependencies), but the latter returns some additional/different
  ; information (:file instead of :url, :group), so they're folded in last to
  ; take precedence in the returned index.  It is likely that
  ; (goog-dependencies), special-casing of them, goog/deps.js, etc can be
  ; removed entirely, but verifying that can be a fight for another day.
  (build-index goog-dependencies))


(def index nil)

(defn goog? [namespace]
  (= "goog" (-> namespace
                str
                (string/split ".")
                first)))

(defn transitive-dependencies
  "Given a sequence of Closure namespace strings, return the list of
  all dependencies. The returned list includes all Google and
  third-party library dependencies.

  Third-party libraries are configured using the :libs option where
  the value is a list of directories containing third-party
  libraries."
  [requires]
  (loop [requires requires
         visited (set requires)
         deps #{}]
    (if (seq requires)
      (let [node (get index (first requires))
            new-req (remove #(contains? visited %) (:requires node))]
        (recur (into (rest requires) new-req)
               (into visited new-req)
               (conj deps node)))
      (remove nil? deps))))

(defn goog-dep-files [& goog-deps]
  (map :file (transitive-dependencies goog-deps)))