(ns script.bootstrap
  (:require [planck.core :refer [transfer-ns init-empty-state file-seq *command-line-args* slurp spit]]
            [planck.repl :as repl]
            [planck.js-deps :as js-deps]
            [cognitect.transit :as t]
            [clojure.string :as string]
            [clojure.set :as set]
            [clojure.string :as string]
            [cljs.tools.reader :as r]))

(defn log [& args]
  #_(prn args))

(when-not (:cache-path @repl/app-env)
  (swap! repl/app-env assoc :cache-path ".planck_cache"))

(defn resource
  "Loads the content for a given file. Includes planck cache path."
  [file]
  (first (or (js/PLANCK_READ_FILE file)
             (js/PLANCK_LOAD file)
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
(def compiled-ns 'cljs-live.core)
(def live-ns-str (slurp "live-ns.edn"))
(def live-ns (second (r/read-string live-ns-str)))

;; modified, from Planck
(defn topo-sorted-deps
  "Given a dep symbol to load, returns a topologically sorted sequence of deps to load, in load order."
  ([dep] (topo-sorted-deps dep #{}))
  ([dep found]
   {:pre [(symbol? dep)]}
   (let [spec (get-in @repl/st [:cljs.analyzer/namespaces dep])
         requires (filter (complement found) (get-deps spec))]
     (distinct (concat (mapcat #(topo-sorted-deps % (into found requires)) requires) [dep])))))

(def goog-requires (atom []))
(let [goog-require (.-require js/goog)]
  (set! (.-require js/goog) (fn [dep]
                              (swap! goog-requires conj dep)
                              (goog-require dep))))

(cljs.js/eval-str repl/st live-ns-str #(when (:error %) (prn %)))

(defn files-to-load*
  "Returns the files to load given and index and a foreign libs dep."
  ([dep] (files-to-load* false dep))
  ([min? dep]
   (map #(or (when min? (:file-min (% @js-index)))
             (:file (% @js-index))) (js-deps/topo-sorted-deps @js-index dep))))

(defn foreign-lib-deps
  [dep]
  (set/intersection
    (set (topo-sorted-deps dep))
    (set (keys @js-index))))
(def foreign-lib? (set (keys @js-index)))

(defn foreign-lib-src [dep]
  (apply str (->> (files-to-load* dep)
                  (map resource))))

(defn js-deps []
  (->> (for [dep (foreign-lib-deps live-ns)]
         {(munge (ns->path dep)) (foreign-lib-src dep)})
       (apply merge)))

(defn realize-lazy-map [m]
  (reduce (fn [acc k] (assoc acc k (get m k)))
          {} (keys m)))

(defn ->transit [x]
  (let [w (t/writer :json)]
    (t/write w (realize-lazy-map x))))

(defn cache-str [namespace]
  (-> (get-in @repl/st [:cljs.analyzer/namespaces namespace])
      realize-lazy-map
      (->transit)))

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

#_(def goog-deps (atom #{}))

(->> [live-ns 'cljs.core]
     (map topo-sorted-deps)
     (map set)
     (apply set/difference)
     (reduce
       (fn [m namespace]
         (let [path (ns->path namespace)]
           (cond (foreign-lib? namespace)
                 (assoc m (munge (str path ".js")) (foreign-lib-src namespace))
                 (goog? namespace)
                 (do (log [:unhandled-goog-dep namespace])
                     (assoc m (munge (str path ".js")) ""))
                 :else (let [source (resource (str path ".js"))]
                         (cond-> (assoc m (munge (str path ".cache.json")) (cache-str namespace))
                                 source (assoc (munge (str path ".js")) source)))))) {})

     (merge {:preload-caches (map cache-str '[cljs.core
                                              cljs.core$macros
                                              cljs.js])})
     (clj->js)
     (js/JSON.stringify)
     (str "window['.cljs_live_cache'] = ")
     (spit "resources/public/js/compiled/cljs_live_cache.js"))


;; does not handle goog deps

#_(merge {:goog-deps (reduce (fn [m goog-dep]
                               (conj m [(munge (ns->path goog-dep ".js"))
                                        (goog-src (symbol goog-dep))])) [] (distinct @goog-requires))})

#_(defn strip [path]
    (-> path
        (string/replace cache-dir "")
        (string/replace #"^[\./]+" "")))

#_(->> #_(rest (file-seq cache-dir))
    #_(filter #(and (string/ends-with? (:path %) ".cache.json")
                    (not (string/starts-with? (-> (strip (:path %)) (path->ns)) "script."))))
    (reduce (fn [caches {:keys [path]}]
              (let [relative-path (strip path)
                    namespace (path->ns relative-path)
                    source-str (resource (ns->path namespace ".js"))]
                (cond-> (-> caches
                            (update :bundled-namespaces conj namespace)
                            (assoc-in [:cache relative-path] (resource (ns->path namespace ".json.cache"))))
                        source-str (assoc-in [:src (str (ns->path namespace ".js"))] source-str)))) {})
    (#(update % :src merge (js-deps)))
    (clj->js)
    (js/JSON.stringify)
    (str "window.bootstrap = ")
    (spit "resources/public/js/compiled/bootstrap.js"))

; 1. load target namespace
; 2. read transitive foreign-lib dependencies of target
; 3. include these foreign libs in the bootstrap cache (min or not-min)

; 4. closure dependencies? #902 planck.repl
; 5. Clojure source code + source maps? - #973 planck.repl

; - we can track which javascript files planck reads.
; - source files that are already read by Planck don't get noticed.
; - there should be a canonical way to get a topologically sorted list
;   of dependencies for a namespace. I need to know, *which* are the
;   foreign libs that come from *my* dep.

;; - include all the basic clojure stuff precompiled. Source + Cache. Copy from Planck.
;; - include all the new/required #user# cache+source from Planck's cache directory.
;; - include :foreign-libs deps from #user# (crawl ns for transitive deps)

;; - include all cache files + macro files for deps that were loaded
;; *** are there any macros missing? ***
