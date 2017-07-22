(ns cljs-live.scratch.analyze
  (:refer-clojure :exclude [ensure])
  (:require [cljs.env :as env :refer [ensure]]
            [cljs.analyzer :as ana]
            [cljs.util :as util]
            [cljs.closure :as cljsc]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [cljs-live.build-util :refer [macroize-ns demacroize-ns demacroize]])
  (:import (java.io Reader File)))


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

(defn ast-deps [include-macros? ast]
  (merge (:uses ast)
         (:requires ast)
         (when include-macros?
           (macroize-dep-map (:use-macros ast)))
         (when include-macros?
           (macroize-dep-map (:require-macros ast)))
         (:imports ast)))
(def ^:dynamic *seen* nil)
(def ^:dynamic *dependencies* nil)

#_(defn ns-deps
  ([namespaces ns] (ns-deps namespaces ns #{}))
  ([namespaces ns the-deps]
   (prn :the-ns (get namespaces ns))
   (prn :all-nses (keys namespaces))
   (let [next-deps (set (vals (ast-deps (get namespaces ns))))
         new-deps (set/difference next-deps the-deps)]
     (if (empty? new-deps)
       the-deps
       (into the-deps (mapcat #(ns-deps namespaces % the-deps) new-deps))))))
(def ^:dynamic *no-follow* #{})
(def dep-namespaces
  "Recursively analyze a namespace for dependencies, optionally including macros."
  ;; should use `parse-ns` to get the ast / ijs,
  ;; so that we have that on hand.
  (memoize (fn [{:keys [include-macros?
                        recursive?]
                 :or {include-macros? false
                      recursive? true}
                 :as dep-opts} ns]
             (if-not *seen*
               (binding [*seen* (atom #{} #_(set (keys (:cljs.analyzer/namespaces @env/*compiler*))))]
                 (dep-namespaces dep-opts ns))
               (do (assert (symbol? ns))
                   (swap! *seen* conj ns)
                   (if-let [src (some-> (cljs-source-for-namespace include-macros? ns)
                                        :relative-path
                                        (io/resource))]
                     (let [opts {:analyze-deps false
                                 :load-macros  include-macros?
                                 :restore      false}
                           next-deps (:requires (binding [ana/*cljs-ns* 'cljs.user
                                                          ana/*cljs-file* src
                                                          ana/*macro-infer* true
                                                          ana/*analyze-deps* (:analyze-deps opts)
                                                          ana/*load-macros* (:load-macros opts)]
                                                  (let [rdr (when-not (sequential? src) (io/reader src))]
                                                    (try
                                                      (loop [forms (if rdr
                                                                     (ana/forms-seq* rdr (source-path src))
                                                                     src)
                                                             ret (merge
                                                                   {:source-file  (when rdr src)
                                                                    :source-forms (when-not rdr src)
                                                                    :requires     (cond-> #{'cljs.core}
                                                                                          (get-in @env/*compiler* [:options :emit-constants])
                                                                                          (conj ana/constants-ns-sym))})]
                                                        (if (seq forms)
                                                          (let [env (ana/empty-env)
                                                                ast (ana/no-warn (ana/analyze env (first forms) nil))]
                                                            (cond
                                                              (= :ns (:op ast))
                                                              (let [ns-name (:name ast)
                                                                    ns-name (if (and (= 'cljs.core ns-name)
                                                                                     (= "cljc" (util/ext src)))
                                                                              'cljs.core$macros
                                                                              ns-name)
                                                                    deps (ast-deps include-macros? ast)]
                                                                (merge
                                                                  {:ns           (or ns-name 'cljs.user)
                                                                   :provides     [ns-name]
                                                                   :requires     (if (= 'cljs.core ns-name)
                                                                                   (set (vals deps))
                                                                                   (cond-> (conj (set (vals deps)) 'cljs.core)
                                                                                           (get-in @env/*compiler* [:options :emit-constants])
                                                                                           (conj ana/constants-ns-sym)))
                                                                   :source-file  (when rdr src)
                                                                   :source-forms (when-not rdr src)
                                                                   :ast          ast
                                                                   :macros-ns    (string/ends-with? (str ns-name) "$macros")}))

                                                              (= :ns* (:op ast))
                                                              (let [deps (ast-deps include-macros? ast)]
                                                                (recur (rest forms)
                                                                       (cond-> (update-in ret [:requires] into (set (vals deps)))
                                                                               ;; we need to defer generating the user namespace
                                                                               ;; until we actually need or it will break when
                                                                               ;; `src` is a sequence of forms - António Monteiro
                                                                               (not (:ns ret))
                                                                               (assoc :ns (ana/gen-user-ns src) :provides [(ana/gen-user-ns src)]))))

                                                              :else ret))
                                                          ret))
                                                      (finally
                                                        (when rdr
                                                          (.close ^Reader rdr)))))))]

                       (when-let [fresh-deps (and recursive?
                                                  (not (contains? *no-follow* ns))
                                                  (set/difference next-deps @*seen*))]
                         (mapv (partial dep-namespaces dep-opts) fresh-deps))
                       (swap! *seen* into next-deps)
                       @*seen*)
                     #_(println "No source found for: " ns)))))))

(defn cljs-resource->ijs [rsrc opts]
  (ana/parse-ns rsrc opts))
