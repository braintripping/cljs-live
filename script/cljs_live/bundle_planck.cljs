(ns cljs-live.bundle-planck
  (:require [planck.core :refer [transfer-ns init-empty-state file-seq *command-line-args* slurp spit line-seq *in*]]
            [planck.repl :as repl]
            [planck.js-deps :as js-deps]
            [planck.shell :refer [sh]]
            [planck.io :as planck-io]
            [clojure.string :as string]
            [cljs.js :as cljsjs]
            [clojure.string :as string]
            [cljs.tools.reader :as r]
            [cognitect.transit :as t]
            [cljs.pprint :refer [pprint]]
            [cljs-live.goog-deps :as goog-deps]))

(defn resource
  "Loads the content for a given file. Includes planck cache path."
  [file]
  (first (or (js/PLANCK_READ_FILE file)
             (js/PLANCK_LOAD file)
             (js/PLANCK_READ_FILE (str (:cache-path @repl/app-env) "/" (munge file))))))

(defn realize-lazy-map [m]
  (reduce (fn [acc k] (assoc acc k (get m k)))
          {} (keys m)))

(def ->transit
  (memoize
    (fn [x]
      (let [w (t/writer :json)]
        (t/write w (realize-lazy-map x))))))

(def transit->clj
  (memoize
    (fn [x]
      (let [r (t/reader :json)]
        (t/read r x)))))

(defn log [& args]
  #_(.error js/console args))

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
  (symbol (str s "$macros")))

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

(defn compile-str [namespace source]
  (let [res (atom nil)
        _ (cljs.js/compile-str repl/st source (str "macro-compile-" namespace) #(reset! res %))]
    @res))

(defn clj*-sources
  [namespace]
  (let [path (string/replace (ns->path namespace) "$macros" "")
        cljs-clojure-variants (cond (string/starts-with? path "cljs/") [path (string/replace path #"^cljs/" "clojure/")]
                                    (string/starts-with? path "clojure/") [path (string/replace path #"^clojure/" "cljs/")]
                                    :else [path])]
    (for [path cljs-clojure-variants
          ext ["clj"
               "cljc"
               "cljs"]
          :let [full-path (str path "." ext)
                contents (resource full-path)]
          :when contents]
      [full-path contents])))

(defn macro-str
  ;; TODO
  ;; look up macro resolution algorithm
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
  "Instrument planck's js-eval to ignore exceptions; we are only interested in build artifacts,
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

(defn goog? [namespace]
  (= "goog" (-> namespace
                str
                (string/split ".")
                first)))

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
                             (goog? %) (if provided? :provided-goog :require-goog)
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

(defn cache-unnecessary?
  "Do not look for caches when there is a javascript source file not compiled by ClojureScript."
  [namespace]
  (or (string/starts-with? (str namespace) "goog.")
      (let [the-file (resource (ns->path namespace ".js"))]
        (and the-file (not (string/starts-with? the-file "// Compiled by ClojureScript"))))))

(defn bundle-deps [{:keys [require-source
                           require-cache
                           require-foreign-dep
                           require-goog
                           provided-goog]}]

  (let [provided-goog-files (set (mapcat goog-deps/goog-dep-files provided-goog))

        caches (reduce (fn [m namespace]
                         (if (cache-unnecessary? namespace)
                           m
                           (let [cache (cache-str namespace true)]
                             (cond-> m
                                     cache (assoc (str (ns->path namespace) ".cache.json") cache))))) {} require-cache)

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
                               (assoc goog-file (resource goog-file)))) {} (apply goog-deps/goog-dep-files require-goog))]
    (merge-with (fn [v1 v2]
                  (if (coll? v1) (into v1 v2) v2)) caches sources foreign-deps goog)))

(patch-planck-js-eval)

(defn path-join [& strs]
  (-> (string/join "/" strs)
      (string/replace #"/+" "/")))

(defn -main []
  (println "Begin planck bundle process")
  (let [{:keys [output-dir
                cljsbuild-out
                bundles
                goog-dependencies]} (-> (string/join (line-seq *in*))
                                        (r/read-string))]

    (set! goog-deps/index (goog-deps/build-index goog-dependencies))
    (set! out-path cljsbuild-out)

    (doseq [{provided :provided/transitive :as dep-spec} bundles]
      (let [{:keys [require-source require-cache] :as bundle-spec} (calculate-deps dep-spec provided)
            deps (assoc (bundle-deps bundle-spec) :provided (vec (sort provided)))
            bundle-str (js/JSON.stringify (clj->js deps))
            bundle-path (str (path-join output-dir (-> (:name dep-spec) name munge)) ".json")
            clj*-sources (mapcat clj*-sources (distinct (concat require-source require-cache)))]
        (doseq [[path source] clj*-sources]
          (let [path (path-join output-dir "sources" path)
                dir (apply path-join (drop-last (string/split path "/")))]
            (sh "mkdir" "-p" dir)
            (spit path source)))
        (spit bundle-path bundle-str)
        (println "Bundle " (:name dep-spec) ":\n")
        (pprint (reduce-kv (fn [m k v] (assoc m k (set v))) {} (dissoc bundle-spec :provided-goog)))
        (println "emitted " bundle-path)))))