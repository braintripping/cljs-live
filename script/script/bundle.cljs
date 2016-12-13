#!/usr/bin/env planck
(ns script.bundle
  (:require [planck.core :refer [transfer-ns init-empty-state file-seq *command-line-args* slurp spit line-seq *in*]]
            [planck.repl :as repl]
            [planck.js-deps :as js-deps]
            [clojure.string :as string]
            [clojure.set :as set]
            [cljs.js :as cljsjs]
            [clojure.string :as string]
            [cljs.tools.reader :as r]
            [cljs.pprint :refer [pprint]]
            [script.goog-deps :as goog]
            [script.io :refer [->transit transit->clj realize-lazy-map]]))

(defn log [& args]
  #_(.error js/console args))

(defn get-named-arg [name]
  (second (first (filter #(= name (first %)) (partition 2 1 *command-line-args*)))))

(def extra-paths (atom []))

(def user-dir (get-named-arg "--user-dir"))

(defn resource
  "Loads the content for a given file. Includes planck cache path."
  [file]
  (first (or (js/PLANCK_READ_FILE file)
             (js/PLANCK_LOAD file)
             (first (for [prefix @extra-paths
                          :let [path (str prefix "/" file)
                                source (js/PLANCK_READ_FILE path)]
                          :when source]
                      source))
             (js/PLANCK_READ_FILE (str (:cache-path @repl/app-env) "/" (munge file))))))

(defn ns->path
  ([s] (ns->path s ""))
  ([s ext]
   (-> (str s)
       (string/replace #"\.macros$" "$macros")
       munge
       (string/replace "." "/")
       (str ext))))

(defn path->ns [path]
  (-> path
      (string/replace ".cache.json" "")
      demunge
      (string/replace "/" ".")))

(defn prune-cache
  "Lots of redundant info in the cache, which we aggressively prune. Can let some of this back in if it is needed."
  [cache-str]
  (let [cache (transit->clj cache-str)]
    (->transit (update cache :defs #(reduce-kv (fn [m k v]
                                                 (assoc m k (dissoc v
                                                                    :meta
                                                                    :file
                                                                    :line
                                                                    :column
                                                                    :end-line
                                                                    :end-column
                                                                    :arglists-meta))) {} %)))))

(defn cache-str
  "Look everywhere to find a cache file."
  [namespace]
  (some-> (or (resource (ns->path namespace ".cljs.cache.json"))
              (resource (ns->path namespace ".cljc.cache.json"))
              (first (doall (for [ext [".cljs" ".cljc" "" ".clj"] ;; planck caches don't have file extensions
                                  [format f] [["edn" (comp ->transit r/read-string)]
                                              ["json" identity]]
                                  :let [path (ns->path namespace (str ext ".cache." format))
                                        resource (some-> (resource path) f)]
                                  :when resource]
                              resource)))
              (some-> (get-in @repl/st [:cljs.analyzer/namespaces namespace])
                      realize-lazy-map
                      (->transit)))
          prune-cache))

(def cache-map
  (memoize (fn [namespace]
             (or (some-> (get-in @repl/st [:cljs.analyzer/namespaces namespace])
                         realize-lazy-map)
                 (some-> (or (resource (ns->path namespace ".cljs.cache.json"))
                             (resource (ns->path namespace ".cljc.cache.json")))
                         (transit->clj))))))

(assert (= (mapv ns->path ['my-app.core 'my-app.core$macros 'my-app.some-lib])
           ["my_app/core" "my_app/core$macros" "my_app/some_lib"]))
(defn get-deps
  [ana-ns]
  (->> (select-keys ana-ns [:requires :imports])
       vals
       (keep identity)
       (map vals)
       (apply concat)
       (concat (->> ana-ns :require-macros keys (map #(symbol (str % "$macros")))))))
(def js-index js-deps/foreign-libs-index)
;; modified, from Planck

(defn transitive-deps
  "Given a dep symbol to load, returns a topologically sorted sequence of deps to load, in load order."
  ([dep] (transitive-deps dep #{}))
  ([dep found]
   (let [requires (filter (complement found) (get-deps (cache-map dep)))]
     (set (concat (mapcat #(transitive-deps % (into found requires)) requires) [dep])))))

(defn js-files-to-load
  "Returns the files to load given and index and a foreign libs dep."
  ([dep] (js-files-to-load false dep))
  ([min? dep]
   (map #(or (when min? (:file-min (% @js-index)))
             (:file (% @js-index))) (js-deps/topo-sorted-deps @js-index dep))))

(def foreign-lib? (set (keys @js-index)))

(def js-file-names-index
  (reduce (fn [m {:keys [file provides]}]
            (assoc m file
                     (reduce (fn [m namespace]
                               (assoc m (munge (str namespace)) file)) {} provides))) {} (vals @js-index)))

(defn foreign-lib-src [dep]
  (apply str (->> (js-files-to-load dep)
                  (map resource))))

(defn macro-str [namespace]
  (or (resource (ns->path namespace ".clj"))
      (resource (ns->path namespace ".cljc"))))

(defn expose-browser-global
  "Writes contents of map to name in `window`."
  [name payload]
  (let [export-var (str "window['" name "']")]
    (str export-var " = " export-var " || {}; "
         (reduce-kv (fn [s path src]
                      (str s export-var "['" path "'] = " (js/JSON.stringify (clj->js src)) ";")) "" payload))))
(defn patch-planck-js-eval
  "Instrument planck's js-eval to ignore exceptions; we are only interested in build artefacts,
  and some runtime errors occur because Planck is not a browser environment."
  []
  (cljsjs/eval repl/st
               '(defn js-eval
                  [source source-url]
                  (if source-url
                    (let [exception (js/PLANCK_EVAL source source-url)]
                      (when exception
                        ;; ignore exceptions, we only want build artefacts
                        #_(throw exception)))
                    (try (js/eval source)
                         (catch js/Error e
                           ;; ignore exceptions, we only want build artefacts
                           nil))))
               {:ns 'planck.repl}
               #(when (:error %) (prn %))))

(def planck-require
  (fn [ns-name type n]
    (let [form (with-meta `(~'ns ~ns-name (~type ~n))
                          {:merge true :line 1 :column 1})]
      (cljs.js/eval repl/st form {} #(when-let [e (:error %)] (println "\n\nfailed: " form "\n" e))))))

(defn calculate-deps [dep-spec provided]

  ;; require target deps into Planck for AOT analysis & compilation
  (let [ns-name (symbol (str "temp." (gensym)))]

    (doall (for [[type expr] (->> (for [type [:require :require-macros :import]
                                        expr (get dep-spec type)]
                                    (case type
                                      (:import :require-macros) [[type expr]]
                                      :require (let [namespace (first (flatten (list expr)))]
                                                 (cond-> [[:require namespace]]
                                                         (some (set (flatten (list expr))) #{:include-macros :refer-macros})
                                                         (conj [:require-macros namespace])))))
                                  (apply concat))]
             (planck-require ns-name type expr)))

    (->> (set (transitive-deps ns-name))                    ; read from Planck's analysis cache to get transitive dependencies
         (#(disj % ns-name 'cljs.env))
         (group-by #(let [provided? (contains? provided %)]
                      (cond (contains? #{'cljs.core 'cljs.core$macros} %) nil ;; only include cljs.core($macros) when explicitly requested
                            (goog/goog? %) (if provided? :provided-goog :require-goog)
                            (foreign-lib? %) (when-not provided? :require-foreign-dep)
                            (not provided?) :require-source
                            :else :require-cache)))
         (#(dissoc % nil))

         ;; merge explicit requires and excludes mentioned in dep-spec
         (merge-with into (select-keys dep-spec [:require-source
                                                 :require-cache
                                                 :require-foreign-dep
                                                 :require-goog]))
         (merge-with #(remove (set %1) %2) (->> (select-keys dep-spec [:exclude-source
                                                                       :exclude-cache
                                                                       :exclude-foreign-dep
                                                                       :exclude-goog])
                                                (reduce-kv
                                                  #(assoc %1 (-> (name %2)
                                                                 (string/replace "exclude" "require")
                                                                 keyword) %3) {}))))))

(defn bundle-deps [{:keys [require-source
                           require-cache
                           require-foreign-dep
                           require-goog
                           provided-goog]}]

  (let [provided-goog-files (set (mapcat goog/goog-dep-files provided-goog))

        caches (reduce (fn [m namespace]
                         (let [cache (cache-str namespace)]
                           (cond-> m
                                   cache (assoc (str (ns->path namespace) ".cache.json") cache)))) {} require-cache)
        sources (reduce (fn [m namespace]
                          (let [path (ns->path namespace)
                                source (resource (str path ".js"))
                                cache (cache-str namespace)]
                            (cond-> m
                                    source (assoc (str path ".js") source)
                                    cache (assoc (str path ".cache.json") cache)))) {} require-source)
        foreign-deps (reduce (fn [m namespace]
                               (reduce (fn [m file]
                                         (-> m
                                             (assoc file (resource file))
                                             ;; self-host env needs to know namespace->file mappings
                                             (update :name-to-path merge (js-file-names-index file)))) m (js-files-to-load namespace))) {} require-foreign-dep)
        goog (reduce (fn [m goog-file]
                       (cond-> m
                               (not (contains? provided-goog-files goog-file))
                               (assoc goog-file (resource goog-file)))) {} (apply goog/goog-dep-files require-goog))]
    (merge-with (fn [v1 v2]
                  (if (coll? v1) (into v1 v2) v2)) caches sources foreign-deps goog)))

(patch-planck-js-eval)

(defn path-join [& strs]
  (-> (string/join "/" strs)
      (string/replace #"/+" "/")))

(let [{:keys [output-dir cljsbuild-out bundles]} (-> (get-named-arg "--deps")
                                                     (slurp)
                                                     (r/read-string))
      {provided-results :value
       error            :error} (-> (line-seq *in*)         ;; provided namespaces may be passed via stdin, as :value in a map
                                    (string/join)
                                    (r/read-string))]
  (if error
    (println error)
    (doseq [[dep-spec provided] (partition 2 (interleave bundles provided-results))]
      (let [_ (swap! extra-paths conj (str user-dir "/" cljsbuild-out))
            bundle-spec (calculate-deps dep-spec provided)
            deps (assoc (bundle-deps bundle-spec) :provided (vec (sort provided)))
            js-string (js/JSON.stringify (clj->js deps)) #_(expose-browser-global ".cljs_live_cache" deps)
            out-path (str (path-join user-dir output-dir (-> (:name dep-spec) name munge)) ".json")]
        (spit out-path js-string)
        (println "Bundle " (:name dep-spec) ":\n")
        (pprint (reduce-kv (fn [m k v] (assoc m k (set v))) {} (dissoc bundle-spec :provided-goog)))
        (println "emitted " out-path)))))