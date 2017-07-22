(ns cljs-live.scratch.bundle-lumo
  (:require
    [lumo.io :as io]
    [lumo.core :refer [eval]]
    [lumo.repl :as repl :refer [st] :rename {st c-state}]
    [lumo.js-deps :as js-deps]
    [lumo.classpath :as classpath]
    [lumo.compiler :as compiler]
    [cljs-live.scratch.analyze-lumo :as analyze]
    [cljs.js :as cljs]
    [lumo.analyzer :as lumo-ana]


    [clojure.string :as string]
    [cljs.js :as cljsjs]
    [clojure.string :as string]
    [cljs.tools.reader :as r]
    [cognitect.transit :as t]
    [cljs.pprint :refer [pprint]]
    [cljs-live.goog-deps :as goog-deps]
    [cljs.env :as env]
    [cljs.tools.reader :as reader]
    [clojure.set :as set]))

;; X slurp
;; X spit
;; X line-seq
;; X *Xin*

;; X add out-dir to lumo classpath
;; X repl/st              - compiler state
;;                        (does it use lazy-map?_
;; PLANCK_EVAL          - override eval to ignore errors
;; with-planck-excludes - ignore certain namespaces during require process
;;
;; X js-deps/foreign-libs-index
;; X js-deps/topo-sorted-deps

(defn all-names [] (set (keys (get-in @c-state [:cljs.analyzer/namespaces]))))
(def original-names (all-names))
(defn new-names []
  (set/difference (all-names) original-names))

(defn resource-str
  "Loads the content for a given file. Includes planck cache path."
  [file]
  (or (try (some-> (io/resource file) (io/slurp)) (catch js/Error e nil))
      (some-> (js/$$LUMO_GLOBALS.readCache file)
              (.-source))))

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
             (or (some-> (get-in @c-state [:cljs.analyzer/namespaces namespace])
                         realize-lazy-map
                         (->transit))
                 (resource-str (ns->path namespace ".cljs.cache.json"))
                 (resource-str (ns->path namespace ".cljc.cache.json"))
                 (first (doall (for [ext [".cljs" ".cljc" "" ".clj"] ;; planck caches don't have file extensions
                                     [format f] [["edn" #(try (-> %
                                                                  (r/read-string)
                                                                  (->transit))
                                                              (catch js/Error e
                                                                (prn :error-parsing-edn-cache e %)))]
                                                 ["json" identity]]
                                     :let [path (ns->path namespace (str ext ".cache." format))
                                           resource (some-> (resource-str path) f)]
                                     :when resource]
                                 resource)))
                 (when required?
                   (throw (js/Error (str "Could not find cache for: " namespace)))))
             #_prune-cache)))



(def cache-map
  (memoize (fn [namespace]
             (or (some-> (get-in @c-state [:cljs.analyzer/namespaces namespace])
                         realize-lazy-map)
                 (some-> (or (resource-str (ns->path namespace ".cljs.cache.json"))
                             (resource-str (ns->path namespace ".cljc.cache.json")))
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
             (:file (% @js-index))) (js-deps/topo-sort @js-index dep))))

(def foreign-lib? (set (keys @js-index)))

(def js-file-names-index
  (reduce (fn [m {:keys [file provides]}]
            (assoc m file
                     (reduce (fn [m namespace]
                               (assoc m (munge (str namespace)) file)) {} provides))) {} (vals @js-index)))

(defn compile-str [namespace source]
  (let [res (atom nil)
        _ (cljs.js/compile-str c-state source (str "macro-compile-" namespace) {:load repl/load} #(reset! res %))]
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
                contents (resource-str full-path)]
          :when contents]
      [full-path contents])))

(defn macro-str
  ;; TODO
  ;; look up macro resolution algorithm
  [out-dir namespace]
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
                       contents (resource-str full-path)]
                 :when contents
                 :let [[ext contents] (or (when (not= "js" ext)
                                            ;; this does not work here as well as in planck.
                                            (prn :compile-a-file full-path)
                                            (let [compiled-str (resource-str (str path (when $macros? "$macros") "." "js"))]
                                              (prn :have-compiled? (boolean compiled-str))
                                              (when compiled-str
                                                ["js" compiled-str]))
                                            #_(let [{:keys [value error]} (compile-str namespace contents)]
                                                (prn :COMPILE_STR namespace :WORKED? (boolean value))
                                                (prn :COMPILE_ERROR error)
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


(defn ns-form [ns-name dep-spec]
  (let [exprs (->> (for [type [:require :require-macros :import]
                         expr (get dep-spec type)]
                     (case type
                       (:import :require-macros) [[type expr]]
                       :require (let [namespace (first (flatten (list expr)))]
                                  (cond-> [[:require namespace]]
                                          (some (set (flatten (list expr))) #{:include-macros :refer-macros})
                                          (conj [:require-macros namespace])))))
                   (apply concat)
                   (reduce (fn [m [k v]] (update m k (fnil conj #{}) v)) {}))
        ns-expr `(~'ns ~ns-name ~@(for [[type expr] exprs]
                                    `(~type ~@expr)))]
    (with-meta ns-expr {:merge true :line 1 :column 1})))

(defn goog? [namespace]
  (= "goog" (-> namespace
                str
                (string/split ".")
                first)))

(def child-process (js/require "child_process"))
(def ensure-dir
  (memoize (fn [p]
             (.spawnSync child-process "mkdir" #js ["-p" p]))))

(defn eval-ignore-errors
  ([form]
   (eval-ignore-errors form (.-name *ns*)))
  ([form ns]
   (cljs/eval c-state form
              {:ns            ns
               :context       :expr
               :def-emits-var true
               :eval          (fn [s]
                                (try (js/eval s)
                                     (catch js/Error e (.log js/console "EVAL-ERROR" e))))}
              (fn [{:keys [value error]}]
                (when error
                  (prn "SAFE_EVAL_ERROR" error)
                  (.log js/console (.-stack error)))))))

(defn with-safe-eval [f]
  (let [js-eval repl/caching-node-eval]
    (set! repl/caching-node-eval (fn [x] (try (js-eval x)
                                              (catch js/Error e nil))))
    (f)
    (set! repl/caching-node-eval js-eval)))

(defn calculate-deps [out-dir dep-spec provided]
  (let [ns-namespace "temp"
        ns-name (gensym)
        the-ns (symbol (str ns-namespace "." ns-name))
        filepath (str ns-namespace "/" ns-name ".cljs")]

    (ensure-dir (str out-dir "/temp"))
    (io/spit (str out-dir "/" filepath) (ns-form the-ns dep-spec))

    ;; strategy 1: (require ..) the namespace, ignore eval errors
    ;; - this works in Planck, but not here.
    #_(with-safe-eval #(eval `(~'require '~the-ns)))

    ;; strategy 2: recursively parse-ns to figure out dependency tree
    ;; - *extremely* slow.
    #_(analyze/parse-ns (->> (concat (:exclude-source dep-spec)
                                     (:require-cache dep-spec))
                             (into #{}))
                      the-ns)

    (let [deps (transitive-deps the-ns)]
      (as-> deps deps
            (disj deps 'cljs.env)                           ; read from Planck's analysis cache to get transitive dependencies
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
                                                                    keyword) %3) {})) deps)))))

(defn cache-unnecessary?
  "Do not look for caches when there is a javascript source file not compiled by ClojureScript."
  [namespace]
  (or (string/starts-with? (str namespace) "goog.")
      (let [the-file (resource-str (ns->path namespace ".js"))]
        (and (string? the-file)
             (not (string/starts-with? the-file "// Compiled by ClojureScript"))))))

(defn bundle-deps [out-dir {:keys [require-source
                                   require-cache
                                   require-foreign-dep
                                   require-goog
                                   provided-goog] :as the-bundle-spec}]

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
                                                   (macro-str out-dir namespace)
                                                   ["js" (resource-str (str path ".js"))])
                                                 (throw (js/Error (str "Source not found: " namespace))))
                                cache (cache-str namespace false)]
                            (cond-> m
                                    source (assoc (str path "." ext) source)
                                    cache (assoc (str path ".cache.json") cache)))) {} require-source)

        foreign-deps (try (reduce (fn [m namespace]
                                    (reduce (fn [m file]
                                              (-> m
                                                  (assoc file (resource-str file))
                                                  ;; self-host env needs to know namespace->file mappings
                                                  (update :name-to-path merge (js-file-names-index file)))) m (js-files-to-load namespace))) {} require-foreign-dep)
                          (catch js/Error e (prn "Error in foreign deps" e)))
        goog (reduce (fn [m goog-file]
                       (cond-> m
                               (not (contains? provided-goog-files goog-file))
                               (assoc goog-file (resource-str goog-file)))) {} (apply goog-deps/goog-dep-files require-goog))]
    (merge-with (fn [v1 v2]
                  (if (coll? v1) (into v1 v2) v2)) caches sources foreign-deps goog)))


(defn path-join [& strs]
  (-> (string/join "/" strs)
      (string/replace #"/+" "/")))

(defn get-stdin
  ""
  []
  (io/slurp "/dev/stdin"))

(defn -main []
  (println "...begin Lumo bundle process")
  (let [{:keys [output-dir
                cljsbuild-out
                bundles
                goog-dependencies]} (-> (get-stdin)
                                        (r/read-string))]
    (println "...building google closure dependency index")
    (set! goog-deps/index (goog-deps/build-index goog-dependencies))

    (println "...adding :cljsbuild-out to classpath: " cljsbuild-out)
    (classpath/add! cljsbuild-out)

    (doseq [{provided    :provided/transitive
             bundle-name :name
             :as         bundle-spec} bundles]
      (println "START: " bundle-name)
      (let [_ (println "...calculate deps")
            {:keys [require-source require-cache] :as bundle-spec} (calculate-deps cljsbuild-out bundle-spec provided)
            deps (assoc (bundle-deps cljsbuild-out bundle-spec) :provided (vec (sort provided)))]

        (println "... " (count deps) " deps found.")

        (println "...copying source files")
        (let [clj*-sources (mapcat clj*-sources (distinct (concat require-source require-cache)))]
          (doseq [[path source] clj*-sources]
            (let [path (path-join output-dir "sources" path)
                  dir (apply path-join (drop-last (string/split path "/")))]
              (ensure-dir dir)
              (io/spit path source))))

        (println "...emitting JSON bundle")
        (let [bundle-str (js/JSON.stringify (clj->js deps))
              bundle-path (str (path-join output-dir (-> bundle-name str munge)) ".json")]
          (println "Emitting " bundle-name " to " bundle-path ":\n")
          (io/spit bundle-path bundle-str)
          (pprint (reduce-kv (fn [m k v] (assoc m k (set v))) {} (dissoc bundle-spec :provided-goog)))
          (println "Finished bundle: " bundle-name))))))