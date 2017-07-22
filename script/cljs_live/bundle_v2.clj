(ns cljs-live.bundle-v2
  (:require [cljs.js-deps :as deps]
            [cljs.closure :as cljsc]
            [clojure.pprint :refer [pprint]]
            [cljs.env :as env]
            [cljs-live.analyze :as analyze]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [cljs.build.api :as build-api]
            [cljs.util :as util]
            [cljs.analyzer :as ana]
            [clojure.tools.reader :as r]
            [clojure.data.json :as json]
            [clojure.java.shell :as shell]))

(defn map-count [m]
  (reduce-kv (fn [m k v] (assoc m k (count v))) {} m))

(defn get-classpath
  "Returns the current classpath as a string."
  []
  (->> (seq (.getURLs (java.lang.ClassLoader/getSystemClassLoader)))
       (map #(.getPath %))
       (string/join ":")))

;; set up a compiler state with options
(def opts {:dump-core             false
           :parallel-build        true
           :source-map            false
           :cache-analysis        true
           :cache-analysis-format :transit
           :infer-externs         false
           :optimizations         :none})

(def live-st (env/default-compiler-env
               (cljsc/add-externs-sources opts)))

(defn transitive-js-deps [namespaces]
  (cljsc/js-dependencies {} (map str namespaces)))

(defn output-dir []
  (get-in @env/*compiler* [:options :output-dir]))

(defn cache-file [s]
  (ana/cache-file s (output-dir)))

(defn relpath [path]
  (-> path
      (string/replace (str (output-dir) "/") "")
      (string/replace #".*jar\!\/" "")))

(defn resource-relpath [f]
  (relpath (.getPath f)))

(defn ns->compiled-js [ns]
  (cljsc/target-file-for-cljs-ns ns (output-dir)))

(defn js-exists? [ns]
  (or (get-in @env/*compiler* [:js-dependency-index (str ns)])
      (deps/find-classpath-lib ns)))

(defn js-dep-resource [js-dep]
  (or (some-> (or (:file-min js-dep)
                  (:file js-dep))
              (io/resource))
      (:url-min js-dep)
      (:url js-dep)))

(defn dep-kind [ns]
  (cond
    (analyze/macros-ns? ns) :macro
    (js-exists? ns) :js
    :else :cljs))

(defn js-dep-path [js-dep]
  (or (:file-min js-dep)
      (:file js-dep)
      (some-> (:url-min js-dep) (resource-relpath))
      (some-> (:url js-dep) (resource-relpath))))

(defn dep-paths [ns]
  (case (dep-kind ns)
    :js (mapv js-dep-path (transitive-js-deps [ns]))
    :macro [(:relative-path (analyze/cljs-source-for-namespace true ns))]
    :cljs [(:relative-path (analyze/cljs-source-for-namespace false ns))]))

(defn compile-bundle-sources
  "Compile source-paths to ClojureScript.

  We do this *without* using the self-hosted compiler because :provided namespaces
  don't need to be self-host compatible, but we still need their analysis caches
  and transitive dependency graphs."

  [{:keys [source-paths bundles cljsbuild-out]}]
  (let [source-paths (->> (mapcat :source-paths bundles)
                          (concat source-paths)
                          (distinct))]
    (doseq [source-path source-paths]
      (build-api/build source-path (assoc opts :output-dir cljsbuild-out)))))

(defn copy-sources! [sources out]
  (doseq [[path content] sources]
    (let [full-path (str out "/" path)]
      (io/make-parents full-path)
      (spit full-path content))))

(defn compile-single-cljs-namespace [the-ns]
  (let [opts (assoc opts :output-dir (output-dir))
        source (cljsc/cljs-source-for-namespace the-ns)
        uri (:uri source)
        inputs (build-api/inputs uri)
        compile (partial build-api/compile opts)
        result (compile inputs)]
    (ana/write-analysis-cache the-ns (ana/cache-file uri (output-dir)))
    (ns->compiled-js the-ns)))

(defn ensure-sequential [ls]
  (if (sequential? ls) ls [ls]))

(defn get-macro-deps [entry]
  (->> (ensure-sequential entry)
       (mapcat #(analyze/dep-namespaces {:include-macros? true
                                         :recursive?      true} %))
       (filter analyze/macros-ns?)
       (set)))

(def skip-macros '#{#_clojure.template$macros
                    #_cljs.pprint$macros
                    #_cljs.js$macros
                    #_cljs.analyzer.macros$macros
                    #_cljs.analyzer$macros
                    #_cljs.tools.reader.reader-types$macros
                    #_cljs.test$macros
                    #_cljs.reader$macros
                    #_cljs.compiler.macros$macros

                    ;; including cljs.js$macros causes error:
                    ;; >> No such namespace: cljs.env.macros, could not locate cljs/env/macros.cljs, cljs/env/macros.cljc, or Closure namespace "cljs.env.macros"
                    ;; -- this was fixed, maybe try removing it with newer version of Planck
                    cljs.js$macros
                    cljs.core$macros})

(defn compile-macros
  "Delegates self-host macro compilation to Planck."
  [namespaces exclude]
  (let [{:keys [out
                exit
                err] :as planck-result} (shell/sh "planck"
                                                  "-c" (get-classpath)
                                                  "-m" "cljs-live.compile-macros-planck"
                                                  :in (with-out-str (prn {:get-macros     namespaces
                                                                          :exclude-macros (set/union skip-macros (set exclude))}))
                                                  :out-enc "UTF-8")]
    (if-let [filename (second (re-find #"___file:(.*)___" out))]
      (r/read-string (slurp filename))
      (prn :PLANCK_ERROR planck-result))))

(defn safe-slurp [f]
  (try (slurp f)
       (catch Exception e nil)))

(defn make-bundle [{:keys [entry provided exclude]}]
  (let [entry-macro-deps (set/difference (get-macro-deps entry) skip-macros)
        _ (prn :entry-macros entry-macro-deps)
        {:keys [macro-deps
                macro-sources
                macro-caches] :as compile-result} (compile-macros entry-macro-deps exclude)
        _ (prn :_entry entry :_provided provided)
        _ (pprint {:ks                  (keys compile-result)
                   :macro-deps          macro-deps
                   :expanded-macro-deps (set/difference macro-deps entry-macro-deps)
                   :macro-sources       (keys macro-sources)
                   :macro-caches        (keys macro-caches)})

        provided-deps (set (mapcat #(analyze/dep-namespaces {:include-macros? false} %) (ensure-sequential provided)))
        entry-deps (-> (set (mapcat #(analyze/dep-namespaces {:include-macros? true} %) (ensure-sequential entry)))
                       (disj 'cljs.core)
                       #_(set/union (set macro-deps)))

        _ (pprint {:entry    entry
                   :provided provided})

        ;; caches are bundled for all non-macro namespaces in :entry.
        ;; caches do not exist for non-Clojure(Script) namespaces.
        ;; macro namespaces must be handled by self-hosted compiler step.
        require-cljs-cache (->> entry-deps
                                (filter (complement analyze/macros-ns?))
                                (filter (complement js-exists?)))

        ;; sources are required for namespaces in :entry which are not :provided.
        require-*-source (set/difference entry-deps provided-deps)
        _ (prn :REQUIRE_SOURCE (sort require-*-source))


        ;; source files are grouped by :js, :macro, and :cljs.
        ;; :js sources are bundled as-is
        ;; :cljs sources are bundled as precompiled js.
        ;; :macro sources are simply recorded for future processing by a self-hosted compiler step.
        {require-js-source   :js
         require-cljs-source :cljs
         require-macros      :macro} (group-by dep-kind require-*-source)

        require-js-source-files (transitive-js-deps require-js-source)
        require-cljs-compiled-files (mapv ns->compiled-js require-cljs-source)

        sources-to-copy (->> (set/union entry-deps provided-deps macro-deps)
                             (mapcat dep-paths)
                             (reduce (fn [m path]
                                       (assoc m path (slurp (io/resource path)))) {}))]


    (merge

      macro-sources
      macro-caches

      (->> require-cljs-source
           (reduce (fn [m the-ns]
                     (let [the-file (ns->compiled-js the-ns)
                           the-file (if (safe-slurp the-file)
                                      the-file
                                      (compile-single-cljs-namespace the-ns))
                           contents (safe-slurp the-file)]
                       (assoc m (resource-relpath the-file)
                                contents))) {}))

      (->> require-cljs-cache
           (reduce (fn [m ns]
                     (let [the-cache-file (cache-file ns)
                           contents (slurp the-cache-file)]
                       (assoc m (resource-relpath the-cache-file)
                                contents))) {}))

      (->> require-js-source-files
           (reduce (fn [m js-dep]
                     (assoc m (js-dep-path js-dep)
                              (slurp (js-dep-resource js-dep)))) {}))



      {:provided provided-deps
       :sources  sources-to-copy})))


(defn main [bundle-spec-path]

  #_(binding [env/*compiler* live-st]
      (let [cljs-ns 'cljs.analyzer.api
            opts (assoc opts :output-dir (output-dir))
            ;ijs-list (cljsc/cljs-dependencies opts [cljs-ns])
            the-source (:uri (cljsc/cljs-source-for-namespace cljs-ns))
            _ (prn :the-source the-source)
            inputs (build-api/inputs the-source)
            _ (prn :inputs inputs)
            compile (partial build-api/compile opts)
            result (compile inputs)
            _ (prn :result result)
            ]
        (prn :inp inputs)
        ;(prn :the-s (cljsc/cljs-source-for-namespace cljs-ns))
        (prn :did-it-work? (ns->compiled-js cljs-ns) (safe-slurp (ns->compiled-js cljs-ns)))
        (ns->compiled-js cljs-ns)))
  (let [{:keys [bundle-out
                cljsbuild-out
                bundles] :as bundle-spec} (-> bundle-spec-path
                                              (slurp)
                                              (r/read-string))]

    (binding [env/*compiler* live-st]
      (swap! env/*compiler* assoc-in [:options :output-dir] cljsbuild-out)
      (compile-bundle-sources bundle-spec)

      (-> opts
          (cljsc/maybe-install-node-deps!)
          (cljsc/add-implicit-options)
          (cljsc/process-js-modules))

      (doseq [{:keys [name entry provided no-follow] :as bundle-spec} bundles]
        (binding [analyze/*no-follow* (set no-follow)]
          (let [{sources :sources
                 :as     the-bundle} (make-bundle bundle-spec)]

            ;; copy sources to output-dir
            (copy-sources! sources (str bundle-out "/sources"))

            ;; TODO
            ;; - allow :entry-ns in dep-spec for inline namespace declaration
            ;; - handle :dependencies loading

            (let [path (str bundle-out "/" name ".json")]
              (io/make-parents path)
              (spit path (json/write-str (dissoc the-bundle :sources))))))))))


(comment
  ;; return js-dependencies for a namespace
  (cljsc/js-dependencies {} ['maria.user])                  ;; will be nil for a cljs dependency
  (cljsc/js-dependencies {} ["goog.events"])                ;; will return dependency-files for goo.events

  ;; return source file for cljs namespace
  (cljsc/source-for-namespace 'maria.user live-st)
  (cljsc/source-for-namespace "goog.events" live-st)

  ;; return the compiled-js file for a cljs namespace
  (build-api/target-file-for-cljs-ns 'maria.user))



