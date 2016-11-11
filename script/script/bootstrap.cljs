#!/bin/sh
":"                                                         ; exec /usr/bin/env planck -c `lein classpath` -K "$0" "$@"

(ns script.bootstrap
  (:require [planck.core :refer [transfer-ns init-empty-state file-seq *command-line-args* slurp spit]]
            [planck.repl :as repl]
            [planck.js-deps :as js-deps]
            [clojure.string :as string]
            [clojure.set :as set]
            [cljs.js :as cljsjs]
            [clojure.string :as string]
            [cljs.tools.reader :as r]
            [script.goog-deps :as goog]
            [script.io :refer [resource ->transit transit->clj realize-lazy-map]]))

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

(defn expose-as-window-vars [m]
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

(defn live-ns-form
  "Convert live-deps into ns form"
  [ns-name dep-spec]
  `(~'ns ~ns-name
     ~@(for [[k exprs] (seq (dissoc dep-spec
                                    :preload-macros
                                    :preload-caches
                                    :require-caches
                                    :output-to
                                    :dependencies))]
         `(~(keyword k) ~@exprs))))

(defn package-deps [{:keys [preload-caches preload-macros require-caches output-to] :as dep-spec}]
  (let [preload-caches (->> preload-caches
                            #_(mapcat topo-sorted-deps)
                            distinct
                            (concat '[cljs.core
                                      cljs.core$macros]))
        ns-name (symbol (str "cljs-live." (gensym)))]

    (cljs.js/eval repl/st (live-ns-form ns-name dep-spec) #(when (:error %)
                                                            (println "\n\nfailed" (live-ns-form ns-name dep-spec))
                                                            (println (:error %))))

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
                     (goog/goog? namespace)
                     (let [goog-files (goog/goog-dep-files namespace)]
                       (reduce (fn [m goog-file]
                                 (let [path (munge goog-file)]
                                   (-> m
                                       (assoc path (resource goog-file))
                                       (update "preload_goog" (fnil conj []) path)))) m goog-files))
                     :else (let [source (resource (str path ".js"))]
                             (cond-> (assoc m (munge (str path ".cache.json")) (cache-str namespace))
                                     source (assoc (munge (str path ".js")) source)))))) {})

         (merge (reduce (fn [m namespace]
                          (let [src (macro-str namespace)
                                path (munge (ns->path namespace "$macros.clj"))]
                            (if-not src m (-> m
                                              (assoc path src)
                                              (update "preload_macros" (fnil conj []) path))))) {} preload-macros))
         (merge (reduce (fn [m namespace]
                          (let [cache (cache-str namespace)
                                path (munge (ns->path namespace ".cache.json"))]
                            (-> m
                                (assoc path cache)
                                (update "preload_caches" (fnil conj []) path)))) {} preload-caches))

         (merge (reduce (fn [m namespace]
                          (assoc m (munge (ns->path namespace ".cache.json"))
                                   (cache-str namespace))) {} require-caches))

         (hash-map ".cljs_live_cache")
         (expose-as-window-vars)
         ((if output-to
            (partial spit output-to)
            println)))

    (when output-to (println "Emitted file: " output-to))))

(defn get-named-arg [name]
  (second (first (filter #(= (str "--" name) (first %)) (partition 2 1 *command-line-args*)))))


(patch-planck-js-eval)

(let [deps-path (or (get-named-arg "live-deps") "live-deps.clj")]
  (package-deps (r/read-string (slurp deps-path))))

; todo
; - option to include source maps? (they're big)
; - use boot to install dependencies listed in live-deps