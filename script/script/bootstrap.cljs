#!/bin/sh
":"                                                         ;: \
; USER_DIR=$(pwd) \
; USER_CLASSPATH=$(lein classpath) \
; cd $(dirname $(python -c "import os,sys; print os.path.realpath(sys.argv[1])" "$0")) \
; cp $USER_DIR/src/deps.cljs opts.clj \
; exec /usr/bin/env planck -K -c $USER_CLASSPATH bootstrap.cljs "$@" --user_dir $USER_DIR

; notes on the above initialization commands:
; - [0] necessary gibberish to get the following lines to execute
; - [1, 2] USER_DIR and USER_CLASSPATH capture context in which script is run
; - [3] cd to this repo's directory, using Python to get the absolute path (following symlinks).
;       this is necessary to allow Planck to require the other scripts in this repo.
;       (http://stackoverflow.com/a/3373298/3421050)
; - [4] copy src/deps.cljs to opts.clj, because Planck does not read local deps.cljs files
; - [5] execute Planck with user classpath, passing in command line args.

(ns script.bootstrap
  (:require [planck.core :refer [transfer-ns init-empty-state file-seq *command-line-args* slurp spit]]
            [planck.repl :as repl]
            [planck.js-deps :as js-deps]
            [clojure.string :as string]
            [clojure.set :as set]
            [cljs.js :as cljsjs]
            [planck.shell :refer [sh]]
            [clojure.string :as string]
            [cljs.tools.reader :as r]
            [script.goog-deps :as goog]
            [cljs.pprint :refer [pprint]]
            [script.io :refer [resource ->transit transit->clj realize-lazy-map]]))

(defn get-named-arg [name]
  (second (first (filter #(= (str "--" name) (first %)) (partition 2 1 *command-line-args*)))))

(def user-dir (get-named-arg "user_dir"))

(defn user-path [s]
  (cond->> s
           (not= (first s) \/)
           (str user-dir "/")))
(defn log [& args]
  #_(.error js/console args))

(defn prn-ret [x]
  (println x)
  x)

(defn ns->name [n]
  {:pre [(or (vector? n) (symbol? n))]}
  (cond-> n
          (vector? n) first))

(defn ns->path
  ([s] (ns->path s ""))
  ([s ext]
   (-> (cond-> s
               (vector? s) first)
       (str)
       (string/replace #"\.macros$" "$macros")
       (munge)
       (string/replace "." "/")
       (str ext))))

(defn path->ns [path]
  (-> path
      (string/replace ".cache.json" "")
      demunge
      (string/replace "/" ".")))

(defn ->dir [s]
  (cond-> s
          (not (string/ends-with? s \/)) (str \/)))

(defn cache-str [out-dir namespace]
  (or (some-> (get-in @repl/st [:cljs.analyzer/namespaces namespace])
              realize-lazy-map
              (->transit))
      (resource (ns->path namespace ".cljs.cache.json"))
      (resource (ns->path namespace ".cljc.cache.json"))
      (and out-dir
           (first (doall (for [ext [".cljc" ".cljs"]
                               [format f] [["edn" (comp ->transit r/read-string)]
                                           ["json" identity]]
                               :let [path (user-path (str (->dir out-dir) (ns->path namespace (str ext ".cache." format))))
                                     resource (some-> (resource path) f)]
                               :when resource]
                           resource))))))

(defn cache-map [namespace]
  (or (some-> (get-in @repl/st [:cljs.analyzer/namespaces namespace])
              realize-lazy-map)
      (some-> (cache-str nil namespace)
              transit->clj)))
(comment
  (assert (= (mapv ns->path ['my-app.core 'my-app.core$macros 'my-app.some-lib])
             ["my_app/core" "my_app/core$macros" "my_app/some_lib"])))

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
(defn topo-sorted-deps
  "Given a dep symbol to load, returns a topologically sorted sequence of deps to load, in load order."
  ([dep] (topo-sorted-deps dep #{}))
  ([dep found]
   {:pre [(symbol? dep)]}
   (let [spec (cache-map dep)
         requires (filter (complement found) (get-deps spec))]
     (distinct (concat (mapcat #(topo-sorted-deps % (into found requires)) requires) [dep])))))

(defn files-to-load*
  "Returns the files to load given and index and a foreign libs dep."
  ([dep] (files-to-load* false dep))
  ([min? dep]
   (map #(or (when min? (:file-min (% @js-index)))
             (:file (% @js-index))) (js-deps/topo-sorted-deps @js-index dep))))

(def foreign-lib? (set (keys @js-index)))

(defn foreign-lib-src [dep]
  (apply str (->> (files-to-load* dep)
                  (map resource))))

(defn macro-str [namespace]
  (or (resource (ns->path namespace ".clj"))
      (resource (ns->path namespace ".cljc"))))

(defn expose-on-js-window [m]
  (apply str (for [[name payload] (seq m)]
               (let [export-var (str "window['" name "']")]
                 (str export-var " = " export-var " || {}; "
                      (reduce-kv (fn [s path src]
                                   (str s export-var "['" path "'] = " (js/JSON.stringify (clj->js src)) ";")) "" payload))))))

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

(defn string-size [s]
  (-> (count s)
      (* 2)
      (/ 1000)))

(defn live-ns-form
  "Convert live-deps into ns form"
  [ns-name dep-spec]
  (let [dep-spec (update dep-spec :require-macros concat (:preload-macros dep-spec))]
    `(~'ns ~ns-name
       ~@(for [[k exprs] (seq (select-keys dep-spec
                                           [:exclude
                                            :require
                                            :require-macros
                                            :import]))]
           `(~(keyword k) ~@exprs)))))

(defn package-deps [{:keys [preload-caches
                            preload-macros
                            require-caches
                            excludes
                            precompiled
                            output-to
                            cljsbuild-out] :as dep-spec}]
  (let [[preload-caches preload-macros require-caches excludes precompiled]
        (->> [preload-caches preload-macros require-caches excludes precompiled] (map (comp set #(map ns->name %))))
        preload-caches (-> preload-caches (set/union '#{cljs.core cljs.core$macros}))
        ns-name (symbol (str "cljs-live." (gensym)))
        precompiled (->> (mapcat topo-sorted-deps precompiled)
                         (filter #(not (string/ends-with? (str %) "$macros")))
                         set)]

    (doseq [n precompiled]
      (cljs.js/eval repl/st `(~'ns ~'cljs-live.precompiled
                               (:require ~n))
                    #(when (:error %)
                      (println "\n\nFailed requiring precompiled dep, " n ":" (:error %)))))

    (cljs.js/eval repl/st (live-ns-form ns-name dep-spec) #(when (:error %)
                                                            (println "\n\nfailed" (live-ns-form ns-name dep-spec))
                                                            (println (:error %))))

    (let [required-sources (-> (set (topo-sorted-deps ns-name))
                               (set/difference (topo-sorted-deps 'cljs.core) excludes)
                               (disj ns-name))
          required-caches (set/union (set/intersection precompiled required-sources)
                                     require-caches preload-caches)
          required-deps (set/difference required-sources required-caches)
          sources (reduce
                    (fn [m namespace]
                      (let [path (ns->path namespace)]
                        (cond (foreign-lib? namespace)
                              (assoc m (munge (str path ".js")) (foreign-lib-src namespace))
                              (goog/goog? namespace)
                              (let [goog-files (goog/goog-dep-files namespace)]
                                (reduce (fn [m goog-file]
                                          (let [path (munge goog-file)]
                                            (-> m
                                                (assoc path (resource goog-file))
                                                (update "preload_goog" (fnil conj []) path)))) m goog-files))
                              :else (let [source (resource (str path ".js"))]
                                      (cond-> (assoc m (munge (str path ".cache.json")) (cache-str cljsbuild-out namespace))
                                              source (assoc (munge (str path ".js")) source)))))) {} required-deps)
          caches (reduce (fn [m namespace]
                           (assoc m (munge (ns->path namespace ".cache.json"))
                                    (cache-str cljsbuild-out namespace))) {} (filter #(and (not (goog/goog? %))
                                                                                           (not (foreign-lib? %))) required-caches))
          cache-preloads (reduce (fn [m namespace]
                                   (update m "preload_caches" (fnil conj []) (munge (ns->path namespace ".cache.json")))) {} preload-caches)
          macro-preloads (reduce (fn [m namespace]
                                   (let [src (macro-str namespace)
                                         path (munge (ns->path namespace "$macros.clj"))]
                                     (if-not src m (-> m
                                                       (assoc path src)
                                                       (update "preload_macros" (fnil conj []) path))))) {} preload-macros)
          bundle (merge sources caches cache-preloads macro-preloads)
          js (expose-on-js-window {".cljs_live_cache" bundle})]

      #_(pprint (reduce-kv (fn [m k v]
                             (-> m
                                 (assoc k (string-size v))
                                 (update :total + (string-size v)))) {} bundle))
      (if output-to
        (spit (user-path output-to) js)
        (println js)))

    (when output-to (println "Emitted file: " (user-path output-to)))))

(patch-planck-js-eval)

(let [deps-path (user-path (or (get-named-arg "deps") "live-deps.clj"))]
  (package-deps (r/read-string (slurp deps-path))))

; todo
; - option to include source maps? (they're big)
; - use boot to install dependencies listed in live-deps