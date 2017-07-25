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
  [macros-ns? ns]
  (let [path (-> (munge ns) (demacroize) (string/replace \. \/))]
    (first (for [ext (if macros-ns? [:clj :cljc] [:cljs :cljc])
                 :let [relpath (str path "." (name ext))
                       res (io/resource relpath)]
                 :when res]
             {:relative-path relpath :uri res :ext ext}))))

(defn ast-dep-map [include-macros? ast]
  (merge (:uses ast)
         (:requires ast)
         (when include-macros?
           (some-> (:use-macros ast)
                   (macroize-dep-map)))
         (when include-macros?
           (some-> (:require-macros ast)
                   (macroize-dep-map)))

         (:imports ast)))

(defn ast-deps [include-macros? ast]
  (set (vals (ast-dep-map include-macros? ast))))

(def ^:dynamic *seen* nil)
(def ^:dynamic *dependencies* nil)
(def ^:dynamic *no-follow* #{})
(def ^:dynamic *exclude* '#{cljs.core})
(def ^:dynamic *include-macros* true)
(def ^:dynamic *recursive* true)

(def cache-map
  (fn [namespace]
    (let [macro? (macros-ns? namespace)
          the-map (get-in @env/*compiler* [:cljs.analyzer/namespaces (demacroize-ns namespace)])
          exists (if macro? (seq (:macros the-map))
                            (seq (:defs the-map)))]
      (when exists
        (cond-> the-map
                macro? (set/rename-keys {:macros :defs}))))))

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

(def parse-ana-deps
  (memoize (fn [{:keys [include-macros?
                        recursive?]
                 :or   {include-macros? false
                        recursive?      true}} ns]
             (if-let [src (some-> (cljs-source-for-namespace (macros-ns? ns) ns)
                                  :relative-path
                                  (io/resource))]
               (-> (binding [ana/*cljs-ns* 'cljs.user
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
                             (let [ast (ana/no-warn (ana/analyze (ana/empty-env) (first forms) nil))]
                               (case (:op ast)
                                 :ns (ast-deps include-macros? ast)
                                 :ns* (recur (rest forms) (into ret (ast-deps include-macros? ast)))
                                 ret))
                             ret))
                         (finally
                           (when rdr
                             (.close ^Reader rdr))))))
                   (disj ns))
               (when-not (js-exists? ns)
                 (println (str "Warning: no source to analyze for dep: " ns)))))))

(def transitive-ana-deps
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
                 (transitive-ana-deps dep-opts ns))
               (do (when-not (symbol? ns)
                     (prn "What is here?" ns)
                     (assert (symbol? ns)))

                   (when-not (contains? *exclude* ns)
                     (swap! *seen* conj ns)
                     (let [next-deps (parse-ana-deps dep-opts ns)]
                       (when-let [fresh-deps (and recursive?
                                                  (not (*no-follow* ns))
                                                  (set/difference next-deps @*seen*))]
                         (mapv (partial transitive-ana-deps dep-opts) fresh-deps))
                       (swap! *seen* into next-deps)
                       (set/difference @*seen*))))))))

(defn cljs-resource->ijs [rsrc opts]
  (ana/parse-ns rsrc opts))
