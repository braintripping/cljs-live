#!/bin/sh
":"                                                         ;: \
; USER_DIR=$(pwd) \
; USER_CLASSPATH=$(lein classpath) \
; cd $(dirname $(python -c "import os,sys; print os.path.realpath(sys.argv[1])" "$0")) \
; exec /usr/bin/env planck -K -c $USER_CLASSPATH bootstrap.cljs "$@" --user_dir $USER_DIR

; notes on the above lines:
; - [0] shell gibberish to get this script to execute, don't touch
; - [1, 2] USER_DIR and USER_CLASSPATH capture script context
; - [3] move to directory of invocation; use Python to get an absolute path, following symlinks (http://stackoverflow.com/a/3373298/3421050)
; - [4] execute Planck with user classpath, passing in command line args.

(ns script.bootstrap
  (:require [planck.core :refer [transfer-ns init-empty-state file-seq *command-line-args* slurp spit]]
            [planck.repl :as repl]
            [clojure.string :as string]
            [cljs.js :as cljsjs]
            [planck.shell :refer [sh with-sh-dir] :include-macros true]
            [clojure.string :as string]
            [cljs.tools.reader :as r]
            [cljs.pprint :refer [pprint]]
            [script.io :refer [resource ->transit transit->clj realize-lazy-map]]))

(defn patch-planck-js-eval
  "Instrument planck's js-eval to ignore exceptions; we are only interested in build artefacts,
  and some runtime errors occur because Planck is not a browser environment."
  []
  (cljsjs/eval repl/st
               '(defn js-eval [source source-url]
                  (if source-url
                    (js/PLANCK_EVAL source source-url)
                    (try (js/eval source) (catch js/Error e nil)))) {:ns 'planck.repl} #()))

(defn get-named-arg [name]
  (second (first (filter #(= (str "--" name) (first %)) (partition 2 1 *command-line-args*)))))

(def user-dir (get-named-arg "user_dir"))

(defn user-path [s]
  (cond->> s
           (not= (first s) \/)
           (str user-dir "/")))

(def script-dir (str (string/trim-newline (:out (sh "pwd"))) "/"))

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

(defn get-cache
  "Look everywhere to find a cache file"
  [out-dir namespace]
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

(comment
  (assert (= (mapv ns->path ['my-app.core 'my-app.core$macros 'my-app.some-lib])
             ["my_app/core" "my_app/core$macros" "my_app/some_lib"])))

(defn expose-browser-global
  "Writes contents of map to object in `window`. yes, we're that evil."
  [name payload]
  (let [export-var (str "window['" name "']")]
    (str export-var " = " export-var " || {}; "
         (reduce-kv (fn [s path src]
                      (str s export-var "['" path "'] = " (js/JSON.stringify (clj->js src)) ";")) "" payload))))

(defn get-cljs-deps
  "Calls a Clojure script which uses cljs.closure & friends to reliably determine transitive dependencies of given namespaces"
  [entry provided]
  (with-sh-dir user-dir
               (->> (concat ["java" "-cp" (string/trim-newline (:out (sh "lein" "classpath"))) "clojure.main" (str script-dir "cljs_closure_deps.clj")]
                            (cons "--require" (map (comp str ns->name) entry))
                            (cons "--provided" (map (comp str ns->name) provided)))
                    (apply sh)
                    :out
                    r/read-string)))

(defn planck-require
  "Loads namespace into Planck REPL so that we can consume the compiled js and analysis caches."
  [n macro?]
  (cljs.js/eval repl/st `(~'ns ~'cljs-live.precompile
                           (~(if macro? :require-macros :require) ~n))
                #(when (:error %)
                  (println "\n\nFailed requiring precompiled dep, " n ":" (:error %)))))

(patch-planck-js-eval)

(defn gather-deps
  "Gather dependency source files and caches. "
  [cljsbuild-out {:keys [require-source
                         require-macros
                         require-caches
                         require-goog
                         require-foreign-deps]}]

  (doseq [n require-source] (planck-require n false))
  (doseq [n require-macros] (planck-require n true))

  (let [sources (reduce (fn [m namespace]
                          (let [path (ns->path namespace)
                                m-path (munge path)
                                source (resource (str path ".js"))
                                cache (get-cache cljsbuild-out namespace)]
                            (cond-> m
                                    source (assoc (str m-path ".js") source)
                                    cache (assoc (str m-path ".cache.json") cache)))) {} require-source)
        foreign-deps (reduce (fn [m file]
                               (let [path (munge file)] (assoc m path (resource file)))) {} require-foreign-deps)
        caches (reduce (fn [m namespace]
                         (assoc m (munge (ns->path namespace ".cache.json"))
                                  (get-cache cljsbuild-out namespace))) {} require-caches)
        macros (reduce (fn [m namespace]
                         (if-let [src (resource (ns->path namespace "$macros.js"))]
                           (assoc m (munge (ns->path namespace "$macros.js")) src)
                           (do (prn :macro-not-found namespace) m))) {} require-macros)
        goog (reduce (fn [m file]
                       (let [path (munge file)]
                         (-> (assoc m path (resource file))
                             (update "preload_goog" (fnil conj []) path)))) {} require-goog)]
    (merge sources foreign-deps caches macros goog)))

(patch-planck-js-eval)


(let [{:keys [entry
              provided
              cljsbuild-out
              output-to] :as dep-spec} (->> (user-path (or (get-named-arg "deps") "live-deps.clj"))
                                            slurp
                                            (r/read-string))
      deps (->> (get-cljs-deps entry provided)
                ;; merge user overrides
                (merge-with into (select-keys dep-spec [:require-source
                                                        :require-macros
                                                        :require-caches
                                                        :require-foreign-deps
                                                        :require-goog]))
                (merge-with #(remove (set %1) %2) (->> (select-keys dep-spec [:exclude-source
                                                                              :exclude-macros
                                                                              :exclude-caches
                                                                              :exclude-foreign-deps
                                                                              :exclude-goog])
                                                       (reduce-kv (fn [m k v]
                                                                    (assoc m (-> (name k)
                                                                                 (string/replace "exclude" "require")
                                                                                 keyword) v)) {}))))
      js-string (->> deps (gather-deps cljsbuild-out)
                     (expose-browser-global ".cljs_live_cache"))]
  (spit (user-path output-to) js-string)
  (println "Bundled:")
  (pprint deps))