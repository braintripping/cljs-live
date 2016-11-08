#!/bin/sh
":"                                                         ; exec /usr/bin/env planck -c `lein classpath` -K "$0" "$@"

(ns script.bootstrap
  (:require [planck.core :refer [transfer-ns init-empty-state file-seq *command-line-args* slurp spit]]
            [planck.repl :as repl]
            [planck.js-deps :as js-deps]
            [cognitect.transit :as t]
            [clojure.string :as string]
            [clojure.set :as set]
            [cljs.js :as cljsjs]
            [clojure.string :as string]
            [cljs.tools.reader :as r]))

(defn log [& args]
  #_(.error js/console args))
(def out-path "resources/public/js/compiled/out")

(defn resource
  "Loads the content for a given file. Includes planck cache path."
  [file]
  (first (or (js/PLANCK_READ_FILE file)
             (js/PLANCK_LOAD file)
             (js/PLANCK_READ_FILE (str (:cache-path @repl/app-env) "/" (munge file)))
             (js/PLANCK_READ_FILE (str out-path "/" file)))))

(defn realize-lazy-map [m]
  (reduce (fn [acc k] (assoc acc k (get m k)))
          {} (keys m)))

(defn ->transit [x]
  (let [w (t/writer :json)]
    (t/write w (realize-lazy-map x))))

(defn transit->clj [x]
  (let [r (t/reader :json)]
    (t/read r x)))

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

(defn cache-str [namespace]
  (or (some-> (get-in @repl/st [:cljs.analyzer/namespaces namespace])
              realize-lazy-map
              (->transit))
      (let [path #(str "resources/public/js/compiled/out/" (ns->path namespace (str % ".cache.json")))]
        (or (resource (path ".cljc"))
            (resource (path ".cljs"))))))

(defn cache-map [namespace]
  (or (some-> (get-in @repl/st [:cljs.analyzer/namespaces namespace])
              realize-lazy-map)
      (some-> (or (resource (ns->path namespace ".cljs.cache.json"))
                  (resource (ns->path namespace ".cljc.cache.json")))
              (transit->clj))))

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

(defn goog? [namespace]
  (= "goog" (-> namespace
                str
                (string/split ".")
                first)))

(defn goog-path [namespace]
  (get (repl/closure-index-mem) namespace))

(defn goog-src [namespace]
  (if (#{} namespace)
    ""
    (when-let [path (goog-path namespace)]
      (resource (str path ".js")))))

(defn extend-window-obj [name m]
  (let [export-var (str "window['" name "']")]
    (str export-var " = " export-var " || {}; "
         (reduce-kv (fn [s path src]
                      (str s export-var "['" path "'] = " (js/JSON.stringify (clj->js src)) ";")) "" m))))

(comment
  ;; instrument planck's js-eval
  (cljsjs/eval repl/st
               '(defn js-eval
                  [source source-url]
                  (if source-url
                    (let [exception (js/PLANCK_EVAL source source-url)]
                      (when exception
                        (throw exception)))
                    (try (js/eval source) (catch js/Error e (prn :js-eval-error e) nil))))
               {:ns 'planck.repl}
               #(when (:error %) (prn %))))

(defn live-ns-form [ns-name dep-spec]
  `(~'ns ~ns-name
     ~@(for [[k exprs] (seq (dissoc dep-spec
                                    :preload-macros
                                    :preload-caches
                                    :output-to))]
         `(~(keyword k) ~@exprs))))

(defn package-deps [{:keys [preload-caches preload-macros output-to] :as dep-spec}]
  (let [preload-caches (->> preload-caches
                            #_(mapcat topo-sorted-deps)
                            distinct
                            (concat '[cljs.core
                                      cljs.core$macros]))
        ns-name (symbol (str "cljs-live." (gensym)))
        goog-deps (atom #{})]

    (cljs.js/eval repl/st (live-ns-form ns-name dep-spec) #(when (:error %)
                                                            (println "\n\nfailed" (live-ns-form ns-name dep-spec))
                                                            (println (:error %))))


    (prn @js-index)
    (->> [ns-name 'cljs.core]
         (map topo-sorted-deps)
         (map set)
         (apply set/difference)
         (#(disj % ns-name))
         (reduce
           (fn [m namespace]
             (let [path (ns->path namespace)]
               (cond (foreign-lib? namespace)
                     (assoc m (munge (str path ".js")) (foreign-lib-src namespace))
                     (goog? namespace)
                     (do (swap! goog-deps conj namespace)
                         (assoc m (munge (str path ".js")) ""))
                     :else (let [source (resource (str path ".js"))]
                             (cond-> (assoc m (munge (str path ".cache.json")) (cache-str namespace))
                                     source (assoc (munge (str path ".js")) source)))))) {})
         (merge (reduce (fn [m namespace]
                          (let [src (macro-str namespace)]
                            (cond-> m
                                    src (assoc (munge (ns->path namespace "$macros.clj")) src)))) {} preload-macros))
         (extend-window-obj ".cljs_live_cache")
         (str (extend-window-obj ".cljs_live_preload_caches"
                                 (reduce (fn [m namespace]
                                           (let [cache (cache-str namespace)]
                                             (cond-> m
                                                     cache
                                                     (assoc (munge (ns->path namespace ".cache.json")) cache)))) {} preload-caches)))
         #_(str (extend-window-obj ".cljs_live_preload_macros"
                                   (reduce (fn [m namespace]
                                             (assoc m (munge (ns->path namespace ".cljs")) (macro-str namespace))
                                             ) {} preload-macros)))
         ((if output-to
            (partial spit output-to)
            println)))

    (when (seq @goog-deps)
      (.error js/console (str "***\nModule " output-to "
> The following Closure dependencies must be provided by your build:"
                              (apply str (for [dep @goog-deps]
                                           (str "\n  [" dep "]")))
                              "\n***")))
    (when output-to (println "Emitted file: " output-to))))

(defn get-named-arg [name]
  (second (first (filter #(= (str "--" name) (first %)) (partition 2 1 *command-line-args*)))))

(let [deps-path (get-named-arg "live-deps")]
  (when-not deps-path (throw (js/Error "Error: Must provide --live-deps arg")))
  (doseq [dep-spec (r/read-string (slurp "live-deps.clj"))]
    (package-deps dep-spec)))


; todo
; - does not handle goog deps
; - option to include source maps? (they're big)
; - run as standalone script, with command-line args?

;; require preload-caches's separately


;; tried individual (ns .. (:require ..)) but it blows away previous defs
#_(doall
    (for [[ns-key names] (seq (dissoc dep-spec
                                      :preload-macros :preload-caches :output-to))
          expr names]
      (do (prn [ns-key expr])
          (cljs.js/eval repl/st `(~'ns ~ns-name
                                   (~ns-key ~expr))
                        {:ns ns-name}
                        #(when (:error %)
                          (println "\n\nfailed" (str ns-key) " " expr "\n")
                          (println (:error %)))))))