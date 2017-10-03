(ns cljs-live.bundle
  (:require [cljs.js-deps :as deps]
            [cljs.closure :as cljsc]
            [clojure.pprint :as pp :refer [pprint]]
            [cljs.env :as env]
            [cljs-live.analyze :as analyze]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [cljs.build.api :as build-api]
            [cljs-live.bundle-util :as bundle-util]
            [cljs.util :as util]
            [cljs.analyzer :as ana]
            [clojure.tools.reader :as r]
            [clojure.data.json :as json]
            [clojure.java.shell :as shell]
            [cognitect.transit :as t])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)))



(def ^:dynamic *string-encoding* "UTF-8")
(def DEBUG false)
(defn log [& args]
  (when DEBUG
    (if (= 1 (count args))
      (pprint args)
      (apply prn args))))

(defn transit-read
  "Reads a value from a decoded string"
  ([s type] (transit-read s type {}))
  ([^String s type opts]
   (let [in (ByteArrayInputStream. (.getBytes s *string-encoding*))]
     (t/read (t/reader in type opts)))))

(defn transit-write
  "Writes a value to a string."
  ([o] (transit-write o :json))
  ([o type] (transit-write o type {}))
  ([o type opts]
   (let [out (ByteArrayOutputStream.)
         writer (t/writer out type opts)]
     (t/write writer o)
     (.toString out *string-encoding*))))

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
           :optimizations         :none
           :pretty-print          false
           ;:optimize-constants    true ;; causes circular dependency error
           :static-fns            true
           :preloads []

           })

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
    (contains? '#{cljs.core.constants} ns) nil
    (analyze/macros-ns? ns) :macro
    (analyze/js-exists? ns) :js
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
    :cljs [(:relative-path (analyze/cljs-source-for-namespace false ns))]
    nil))

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
        result (try (build-api/compile opts inputs)
                    (catch Exception e
                      (prn "Failed to compile: " the-ns)
                      nil))]
    (when result
      (ana/write-analysis-cache the-ns (ana/cache-file uri (output-dir)))
      (ns->compiled-js the-ns))))

(defn wrap-in-set [ls]
  (when ls
    (if (coll? ls) (set ls) (conj #{} ls))))

(defn get-macro-deps [entry]
  (doall (->> (wrap-in-set entry)
              (mapcat #(analyze/parse-ana-deps {:include-macros? true} %))
              (filter analyze/macros-ns?)
              (set))))

(def exclude-macros '#{
                       ;; including cljs.js$macros causes error:
                       ;; >> No such namespace: cljs.env.macros, could not locate cljs/env/macros.cljs, cljs/env/macros.cljc, or Closure namespace "cljs.env.macros"
                       ;; -- this was fixed, maybe try removing it with newer version of Planck
                       cljs.js$macros
                       cljs.core$macros})

(defn compile-macros
  "Delegates self-host macro compilation to Planck."
  [macro-spec]
  (let [{:keys [out
                exit
                err] :as planck-result} (shell/sh "planck"
                                                  "-c" (get-classpath)
                                                  "--auto-cache"
                                                  "-m" "cljs-live.compile-macros-planck"
                                                  :in (with-out-str (prn macro-spec))
                                                  :out-enc "UTF-8")]
    (if-let [filename (second (re-find #"___file:(.*)___" out))]
      (r/read-string (slurp filename))
      (prn :PLANCK_ERROR planck-result))))

(defn safe-slurp [f]
  (try (slurp f)
       (catch Exception e nil)))

(defn safe-slurp-resource [r]
  (try (slurp (io/resource r))
       (catch Exception e nil)))


(defn make-bundle [{:keys [entry provided entry/exclude name]
                    :or   {exclude #{}}
                    :as   bundle}]

  (let [entry-macro-deps (set/difference (set (mapcat get-macro-deps entry))
                                         exclude-macros)
        _ (log bundle)
        _ (log "Macros: ")
        _ (log entry-macro-deps)

        {:keys [macro-deps
                macro-sources
                macro-caches]} (when (seq entry-macro-deps)
                                 (compile-macros {:entry/macros         entry-macro-deps
                                                  :entry/exclude-macros (into exclude-macros exclude)}))

        _ (log {:macro-deps          macro-deps
                :macro-deps-expanded (set/difference macro-deps entry-macro-deps)})

        provided-deps (set (mapcat #(analyze/transitive-ana-deps {:include-macros? false} %) provided))
        entry-deps (-> (set (mapcat #(analyze/transitive-ana-deps {:include-macros? false} %) entry))
                       (disj 'cljs.core))

        ;; caches are bundled for all non-macro namespaces in :entry.
        ;; caches do not exist for non-Clojure(Script) namespaces.
        ;; macro namespaces must be handled by self-hosted compiler step.
        require-cljs-cache (->> entry-deps
                                (filter (complement analyze/macros-ns?))
                                (filter (complement js-exists?)))

        ;; sources are required for namespaces in :entry which are not :provided.
        require-*-source (set/difference entry-deps provided-deps)
        _ (log "Require source files: " (sort require-*-source))


        ;; source files are grouped by :js, :macro, and :cljs.
        ;; :js sources are bundled as-is
        ;; :cljs sources are bundled as precompiled js.
        ;; :macro sources are simply recorded for future processing by a self-hosted compiler step.
        {require-js-source   :js
         require-cljs-source :cljs
         _require-macros     :macro} (group-by dep-kind require-*-source)

        require-js-source-files (transitive-js-deps require-js-source)

        ;; cljs.core$macros is AOT-compiled as a special case so we manually include it here
        sources-to-copy (->> (set/union entry-deps provided-deps macro-deps #{'cljs.core$macros})
                             (mapcat dep-paths)
                             (reduce (fn [m path]
                                       (if-let [contents (or (safe-slurp-resource path)
                                                             (safe-slurp path))]
                                         (assoc m path contents)
                                         (do (prn :MISSING_FILE path)
                                             m))) {}))]

    (pp/print-table [{:kind  :cljs-sources
                      :count (count require-cljs-source)}
                     {:kind :cljs-caches
                      :count (count require-cljs-cache)}
                     {:kind  :macro-sources
                      :count (count macro-sources)}
                     {:kind  :macro-caches
                      :count (count macro-caches)}
                     {:kind :js-sources
                      :count (count require-js-source)}])
    (merge

      macro-sources
      macro-caches

      (->> require-cljs-source
           (reduce (fn [m the-ns]
                     (let [the-file (ns->compiled-js the-ns)
                           the-file (if (safe-slurp the-file)
                                      the-file
                                      (compile-single-cljs-namespace the-ns))
                           contents (when the-file (safe-slurp the-file))]
                       (cond-> m
                               contents (assoc (resource-relpath the-file)
                                               contents)))) {}))

      (->> require-cljs-cache
           (reduce (fn [m ns]
                     (let [the-cache-file (cache-file ns)
                           contents (safe-slurp the-cache-file)]
                       (cond-> m
                               contents (assoc (resource-relpath the-cache-file)
                                               contents)))) {}))

      (->> require-js-source-files
           (reduce (fn [m js-dep]
                     (log :the-js-dep js-dep)
                     (assoc m (js-dep-path js-dep)
                              ;; add goog.provide statements.
                              (str (cljsc/build-provides (map munge (:provides js-dep)))
                                   (slurp (js-dep-resource js-dep))))) {}))



      {:provided provided-deps
       :sources  sources-to-copy})))



;; write bundle index

;; write temp ns files, add to classpath

(defn delete-recursively [fname]
  ;; https://gist.github.com/edw/5128978
  (doseq [f (reverse (file-seq (clojure.java.io/file fname)))]
    (clojure.java.io/delete-file f)))

(defn compile-sources [source-paths out-dir]
  (doseq [source-path source-paths]
    (build-api/build source-path (assoc opts :output-dir out-dir))))

(defn main [bundle-spec-path]

  (let [bundle-spec (-> bundle-spec-path
                        (slurp)
                        (r/read-string))
        {:keys [bundle-out
                cljsbuild-out
                bundles] :as bundle-spec} (assoc bundle-spec :bundles (map (fn [bundle]
                                                                             (-> bundle
                                                                                 (update :entry wrap-in-set)
                                                                                 (update :entry/exclude wrap-in-set)
                                                                                 (update :entry/no-follow wrap-in-set)
                                                                                 (update :provided wrap-in-set))) (:bundles bundle-spec)))]



    (binding [env/*compiler* live-st]
      (swap! env/*compiler* assoc-in [:options :output-dir] cljsbuild-out)

      (let [{entry-macros true
             entry        false} (group-by analyze/macros-ns? (mapcat :entry bundles))
            temp-src (str bundle-out "/temp_src")
            temp-ns-path (str temp-src "/cljs_live_temp/user.cljs")]

        (io/make-parents temp-ns-path)
        (spit temp-ns-path `(~'ns ~'cljs-live-temp.user
                              (:require ~@entry)
                              (:require-macros ~@(map bundle-util/demacroize-ns entry-macros))))

        (println "\n*****\nCLJS-Live\n\n...compiling project\n")
        (time (compile-sources (->> (mapcat :source-paths bundles)
                                    (concat (:source-paths bundle-spec))
                                    (cons temp-src)
                                    (distinct)) cljsbuild-out))
        (delete-recursively temp-ns-path))

      (cljsc/maybe-install-node-deps! opts)
      (-> opts
          (cljsc/add-implicit-options)
          (cljsc/process-js-modules))


      (let [core-path (str bundle-out "/cljs.core.json")]
        (io/make-parents core-path)
        (spit core-path (json/write-str (->> ["cljs/core.cljs.cache.json"
                                              "cljs/core$macros.cljc.cache.json"]
                                             (reduce (fn [m path]
                                                       (assoc m path (-> (io/file cljsbuild-out path)
                                                                         (slurp)))) {})))))

      (let [bundle-count (count bundles)]
        (doseq [i (range bundle-count)]
          (let [{:keys [name entry/no-follow entry/exclude] :as bundle-spec} (nth bundles i)]
            (when-not name (throw (Exception. (str "Bundle requires a name: " bundle-spec))))
            (println (str "\n..." name " (" (inc i) "/" bundle-count ")"))
            (binding [analyze/*no-follow* (set no-follow)
                      analyze/*exclude* (set exclude)]
              (let [{sources :sources
                     :as     the-bundle} (make-bundle bundle-spec)]

                ;; copy sources to output-dir
                (copy-sources! sources (str bundle-out "/sources"))

                ;; TODO
                ;; - allow :entry-ns in dep-spec for inline namespace declaration
                ;; - handle :dependencies loading

                (spit (str bundle-out "/" name ".json")
                      (json/write-str (dissoc the-bundle :sources))))))))
      (println "\nFinished\n*****\n")
      (System/exit 0))))


(comment
  ;; return js-dependencies for a namespace
  (cljsc/js-dependencies {} ['maria.user])                  ;; will be nil for a cljs dependency
  (cljsc/js-dependencies {} ["goog.events"])                ;; will return dependency-files for goo.events

  ;; return source file for cljs namespace
  (cljsc/source-for-namespace 'maria.user live-st)
  (cljsc/source-for-namespace "goog.events" live-st)

  ;; return the compiled-js file for a cljs namespace
  (build-api/target-file-for-cljs-ns 'maria.user))



