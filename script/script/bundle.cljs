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



(def user-dir (get-named-arg "--user-dir"))
(def out-path nil)



(defn safe-slurp [path]
  (try (slurp path)
       (catch js/Object e nil)))

(def resource
  "Loads the content for a given file. Includes planck cache path."
  (memoize (fn [filename]
             (or (some-> (js/PLANCK_READ_FILE filename) (first))
                 (some-> (js/PLANCK_LOAD filename) (first))
                 (safe-slurp (str out-path "/" filename))
                 (safe-slurp (str (:cache-path @repl/app-env) "/" (munge filename)))))))

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

(def cache-str
  "Look everywhere to find a cache file."
  (memoize (fn [namespace required?]
             (or (some-> (get-in @repl/st [:cljs.analyzer/namespaces namespace])
                         realize-lazy-map
                         (->transit))
                 (resource (ns->path namespace ".cljs.cache.json"))
                 (resource (ns->path namespace ".cljc.cache.json"))
                 (first (doall (for [ext [".cljs" ".cljc" "" ".clj"] ;; planck caches don't have file extensions
                                     [format f] [["edn" #(try (-> %
                                                                  (r/read-string)
                                                                  (->transit))
                                                              (catch js/Error e
                                                                (prn :error-parsing-edn-cache e)))]
                                                 ["json" identity]]
                                     :let [path (ns->path namespace (str ext ".cache." format))
                                           resource (some-> (resource path) f)]
                                     :when resource]
                                 resource)))
                 (when required?
                   (throw (js/Error (str "Could not find cache for: " namespace)))))
             #_prune-cache)))



(def cache-map
  (memoize (fn [namespace]
             (or (some-> (get-in @repl/st [:cljs.analyzer/namespaces namespace])
                         realize-lazy-map)
                 (some-> (or (resource (ns->path namespace ".cljs.cache.json"))
                             (resource (ns->path namespace ".cljc.cache.json")))
                         (transit->clj))))))

(assert (= (mapv ns->path ['my-app.core 'my-app.core$macros 'my-app.some-lib])
           ["my_app/core" "my_app/core$macros" "my_app/some_lib"]))

(defn macroize-sym [s]
  (str s "$macros"))

(def get-deps
  (memoize (fn [ana-ns]
             (->> (select-keys ana-ns [:requires :imports])
                  vals
                  (keep identity)
                  (map vals)
                  (apply concat)
                  (concat (->> ana-ns :require-macros vals (map macroize-sym)))))))


(def js-index js-deps/foreign-libs-index)
;; modified, from Planck

(defn transitive-deps
  "Given a dep symbol to load, returns a topologically sorted sequence of deps to load, in load order."
  ([dep-name] (transitive-deps dep-name #{}))
  ([dep-name found]
   (let [new-deps (filter (complement found) (get-deps (cache-map dep-name)))
         found (into found new-deps)]
     (cond-> found
             (seq new-deps) (into (mapcat #(transitive-deps % found) new-deps))))))

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
;
;(and (= name 'cljs.env.macros) macros)
;(and (= name 'cljs.analyzer.macros) macros)
;(and (= name 'cljs.compiler.macros) macros)
;(and (= name 'cljs.js) macros)
;(and (= name 'cljs.pprint) macros)
;(and (= name 'cljs.reader) macros)
;(and (= name 'cljs.tools.reader.reader-types) macros)

(defn compile-str [namespace source]
  (let [res (atom nil)
        _ (cljs.js/compile-str repl/st source (str "macro-compile-" namespace) #(reset! res %))]
    @res))

(defn macro-str
  ;; TODO
  ;; look up macro resolution algo
  [namespace]
  (let [path (string/replace (ns->path namespace) "$macros" "")
        cljs-clojure-variants (cond (string/starts-with? path "cljs/") [path (string/replace path #"^cljs/" "clojure/")]
                                    (string/starts-with? path "clojure/") [path (string/replace path #"^clojure/" "cljs/")]
                                    :else [path])]
    (first (for [path cljs-clojure-variants
                 [$macros? ext] [[true "js"]
                                 [true "clj"]
                                 [true "cljc"]
                                 [false "clj"]
                                 [false "cljc"]]
                 :let [full-path (str path (when $macros? "$macros") "." ext)
                       contents (resource full-path)]
                 :when contents
                 :let [[ext contents] (or (when (not= "js" ext)
                                            (let [{:keys [value]} (compile-str namespace contents)]
                                              (when value
                                                ["js" value])))
                                          [ext contents])]]
             [ext contents])))
  #_(or (resource (ns->path namespace ".clj"))
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
  (fn [ns-name n]
    (let [form (with-meta `(~'ns ~ns-name
                             ~@(for [[type expr] (seq n)]
                                 `(~type ~@expr)))
                          {:merge true :line 1 :column 1})]
      (cljs.js/eval repl/st form {} #(when-let [e (:error %)] (println "\n\nfailed: " form "\n" e))))))


(defn with-planck-excludes [excludes f]
  (let [skip-load? repl/skip-load?]
    ;; prevent load of :exclude and :require-cache libs in Planck
    (set! repl/skip-load? (fn [x]
                            (or (excludes (:name x))
                                (skip-load? x))))
    (f)
    (set! repl/skip-load? skip-load?)))

(defn calculate-deps [dep-spec provided]
  ;; require target deps into Planck for AOT analysis & compilation
  (let [ns-name (symbol (str "temp." (gensym)))]
    (with-planck-excludes
      (->> (concat (:exclude-source dep-spec)
                   (:require-cache dep-spec))
           (into #{}))
      #(planck-require ns-name (->> (for [type [:require :require-macros :import]
                                          expr (get dep-spec type)]
                                      (case type
                                        (:import :require-macros) [[type expr]]
                                        :require (let [namespace (first (flatten (list expr)))]
                                                   (cond-> [[:require namespace]]
                                                           (some (set (flatten (list expr))) #{:include-macros :refer-macros})
                                                           (conj [:require-macros namespace])))))
                                    (apply concat)
                                    (reduce (fn [m [k v]] (update m k (fnil conj #{}) v)) {}))))
    (as-> (transitive-deps ns-name) deps
          (disj deps 'cljs.env)                             ; read from Planck's analysis cache to get transitive dependencies
          (group-by #(let [provided? (contains? provided %)]
                       (cond (contains? #{'cljs.core 'cljs.core$macros} %) nil ;; only include cljs.core($macros) when explicitly requested
                             (goog/goog? %) (if provided? :provided-goog :require-goog)
                             (foreign-lib? %) (when-not provided? :require-foreign-dep)
                             (not provided?) :require-source
                             :else :require-cache)) deps)
          (dissoc deps nil)

          ;; merge explicit requires and excludes mentioned in dep-spec
          (merge-with into (select-keys dep-spec [:require-source
                                                  :require-cache
                                                  :require-foreign-dep
                                                  :require-goog]) deps)
          (merge-with #(remove (set %1) %2) (->> (select-keys dep-spec [:exclude-source
                                                                        :exclude-cache
                                                                        :exclude-foreign-dep
                                                                        :exclude-goog])
                                                 (reduce-kv
                                                   #(assoc %1 (-> (name %2)
                                                                  (string/replace "exclude" "require")
                                                                  keyword) %3) {})) deps))))

(defn bundle-deps [{:keys [require-source
                           require-cache
                           require-foreign-dep
                           require-goog
                           provided-goog]}]

  (let [provided-goog-files (set (mapcat goog/goog-dep-files provided-goog))

        caches (reduce (fn [m namespace]
                         (let [cache (cache-str namespace true)]
                           (cond-> m
                                   cache (assoc (str (ns->path namespace) ".cache.json") cache)))) {} require-cache)

        sources (reduce (fn [m namespace]
                          (let [path (ns->path namespace)
                                [ext source] (or (if (string/ends-with? path "$macros")
                                                   (macro-str namespace)
                                                   ["js" (resource (str path ".js"))])
                                                 (throw (js/Error (str "Source not found: " namespace))))
                                cache (cache-str namespace false)]
                            (cond-> m
                                    source (assoc (str path "." ext) source)
                                    cache (assoc (str path ".cache.json") cache)))) {} require-source)

        foreign-deps (try (reduce (fn [m namespace]
                                    (reduce (fn [m file]
                                              (-> m
                                                  (assoc file (resource file))
                                                  ;; self-host env needs to know namespace->file mappings
                                                  (update :name-to-path merge (js-file-names-index file)))) m (js-files-to-load namespace))) {} require-foreign-dep)
                          (catch js/Error e (prn "Error in foreign deps" e)))
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

(defn -main []
  (println "Main")
  (let [{:keys [output-dir cljsbuild-out bundles]} (-> (get-named-arg "--deps")
                                                       (slurp)
                                                       (r/read-string))
        stdin (string/join (line-seq *in*))
        {provided-results :value
         error            :error} (try (r/read-string stdin) ;; provided namespaces may be passed via stdin, as :value in a map
                                       (catch js/Error e
                                         (prn stdin)
                                         (println "read-string error" e)))]

    (if error
      (println "Error reading standard in" error "\n\n")
      (doseq [[dep-spec provided] (partition 2 (interleave bundles provided-results))]
        (set! out-path (str user-dir "/" cljsbuild-out))
        (let [bundle-spec (calculate-deps dep-spec provided)
              deps (assoc (bundle-deps bundle-spec) :provided (vec (sort provided)))
              js-string (js/JSON.stringify (clj->js deps))
              out-path (str (path-join user-dir output-dir (-> (:name dep-spec) name munge)) ".json")]

          (spit out-path js-string)
          (println "Bundle " (:name dep-spec) ":\n")
          (pprint (reduce-kv (fn [m k v] (assoc m k (set v))) {} (dissoc bundle-spec :provided-goog)))
          (println "emitted " out-path))))))