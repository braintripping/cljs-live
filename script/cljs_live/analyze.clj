(ns cljs-live.analyze
  (:refer-clojure :exclude [ensure])
  (:require [cljs.env :as env :refer [ensure]]
            [cljs.analyzer :as ana]
            [cljs.util :as util]
            [cljs.closure :as cljsc]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [cljs-live.bundle-util :refer [macroize-ns demacroize-ns demacroize]]
            [cljs.js-deps :as deps])
  (:import (java.io Reader File)))

(defn js-exists? [ns]
  (or (get-in @env/*compiler* [:js-dependency-index (str ns)])
      (deps/find-classpath-lib ns)))

(defn macroize-dep-map [m]
  (reduce-kv (fn [m k v]
               (assoc m (macroize-ns k) (macroize-ns v))) {} m))

(defn macros-ns? [ns]
  (string/ends-with? (str ns) "$macros"))

(defn- source-path
  "Returns a path suitable for providing to tools.reader as a 'filename'."
  [x]
  (cond
    (instance? File x) (.getAbsolutePath ^File x)
    :default (str x)))

(defn munge-path [ss]
  (clojure.lang.Compiler/munge (str ss)))

(defn cljs-ns->resource
  "Given a namespace as a symbol return the corresponding resource if it exists."
  [ns]
  (or (if (macros-ns? ns)
        (or
          (io/resource (util/ns->relpath (demacroize-ns ns) :cljc))
          (io/resource (util/ns->relpath (demacroize-ns ns) :clj)))
        (or (io/resource (util/ns->relpath ns :cljs))
            (io/resource (util/ns->relpath ns :cljc))))
      (println "No source for: " ns))
  )

(defn cljs-source-for-namespace
  "Given a namespace return the corresponding source with either a .cljs or
  .cljc extension, or .clj if include-macros? is true.."
  ;; TODO
  ;; what if a namespace has both .cljs and .cljc non-macro content?
  ;; & what if a .cljc file contains macros?
  [include-macros? ns]
  (if (= "cljs.core$macros" (str ns))
    (let [relpath "cljs/core.cljc"]
      {:relative-path relpath :uri (io/resource relpath) :ext :cljc})
    (let [path (-> (munge ns) (demacroize) (string/replace \. \/))
          relpath (str path ".cljs")]
      (if-let [res (io/resource relpath)]
        {:relative-path relpath :uri res :ext :cljs}
        (let [relpath (str path ".cljc")]
          (if-let [res (io/resource relpath)]
            {:relative-path relpath :uri res :ext :cljc}
            (when include-macros?
              (let [relpath (str path ".clj")]
                (if-let [res (io/resource relpath)]
                  {:relative-path relpath :uri res :ext :clj})))))))))

(defn ast-dep-map [include-macros? ast]
  (merge (:uses ast)
         (:requires ast)
         (when include-macros?
           (macroize-dep-map (:use-macros ast)))
         (when include-macros?
           (macroize-dep-map (:require-macros ast)))

         (:imports ast)))

(defn ast-deps [include-macros? ast]
  (-> (vals (ast-dep-map include-macros? ast))
      (set)
      (conj 'cljs.core)))

(def ^:dynamic *seen* nil)
(def ^:dynamic *dependencies* nil)
(def ^:dynamic *no-follow* #{})
(def ^:dynamic *exclude* #{'cljs.core})
(def ^:dynamic *include-macros* true)
(def ^:dynamic *recursive* true)

(def cache-map
  (memoize (fn [namespace]
             (get-in @env/*compiler* [:cljs.analyzer/namespaces namespace]))))

(defn transitive-deps*
  "Given a dep symbol to load, returns a topologically sorted sequence of deps to load, in load order."
  ([dep-name] (transitive-deps* #{dep-name} dep-name))
  ([found dep-name]
   (let [new-deps (-> (ast-deps *include-macros* (cache-map dep-name))
                      (set/difference found *exclude*))
         found (into found new-deps)]
     (cond-> found
             (and *recursive*
                  (not (*no-follow* dep-name))) (into (mapcat (partial transitive-deps* found) new-deps))))))

(defn transitive-deps [{:keys [include-macros?
                               recursive?]
                        :or   {include-macros? false
                               recursive?      true}} ns]
  (binding [*include-macros* (or include-macros? false)]
    (transitive-deps* ns)))

(def parse-ns-deps
  (memoize (fn [{:keys [include-macros?
                        recursive?]
                 :or   {include-macros? false
                        recursive?      true}} ns]
             (if-let [src (some-> (cljs-source-for-namespace include-macros? ns)
                                    :relative-path
                                    (io/resource))]
               (binding [ana/*cljs-ns* 'cljs.user
                         ana/*cljs-file* src
                         ana/*macro-infer* true
                         ana/*analyze-deps* false
                         ana/*load-macros* include-macros?]
                 (let [rdr (when-not (sequential? src) (io/reader src))]
                   (try
                     (loop [forms (if rdr
                                    (ana/forms-seq* rdr (source-path src))
                                    src)
                            ret #{}]
                       (if (seq forms)
                         (let [ast (ana/no-warn (ana/analyze (ana/empty-env) (first forms) nil))
                               deps (ast-deps include-macros? ast)]
                           (case (:op ast)
                             :ns deps
                             :ns*
                             (recur (rest forms) (into ret deps))
                             ret))
                         ret))
                     (finally
                       (when rdr
                         (.close ^Reader rdr))))))
               (when-not (js-exists? ns)
                 (println (str "Warning: no source to analyze for dep: " ns)))))))

(def dep-namespaces
  "Recursively analyze a namespace for dependencies, optionally including macros."
  ;; should use `parse-ns` to get the ast / ijs,
  ;; so that we have that on hand.
  (memoize (fn [{:keys [include-macros?
                        recursive?]
                 :or   {include-macros? false
                        recursive?      true}
                 :as   dep-opts} ns]
             (if-not *seen*
               (binding [*seen* (atom #{})]
                 (dep-namespaces dep-opts ns))
               (do (assert (symbol? ns))
                   (when-not (*exclude* ns)
                     (swap! *seen* conj ns)
                     (let [cached-deps (when (cache-map ns)
                                         (ast-deps include-macros? (cache-map ns)))
                           next-deps (parse-ns-deps dep-opts ns)]
                       (when (and cached-deps next-deps
                                  (not= cached-deps next-deps))
                         (prn "Cached deps not equal for " ns)
                         (pprint {:ns            ns
                                  :deps-by-cache cached-deps
                                  :deps-by-ana   next-deps}))
                       (when-let [fresh-deps (and recursive?
                                                  (not (*no-follow* ns))
                                                  (set/difference next-deps @*seen*))]
                         (mapv (partial dep-namespaces dep-opts) fresh-deps))
                       (swap! *seen* into next-deps)
                       (set/difference @*seen*))))))))

(defn cljs-resource->ijs [rsrc opts]
  (ana/parse-ns rsrc opts))
