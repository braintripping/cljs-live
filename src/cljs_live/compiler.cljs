(ns cljs-live.compiler
  (:require [cljs.js :as cljs]
            [cljs.tools.reader :as r]
            [cljs.tools.reader.reader-types :as rt]
            [cognitect.transit :as transit]
            [goog.object :as gobj]
            [clojure.string :as string]))



(defn read-string [s]
  (when (and s (not= "" s))
    (r/read {} (rt/indexing-push-back-reader s))))

(def debug? false)
(enable-console-print!)
(def log (if debug? println list))

(defonce c-state (cljs/empty-state))
(defonce c-env (atom {}))
(defonce loaded (atom #{}))

;; monkey-patch goog.isProvided_
(def is-provided (aget js/goog "isProvided_"))
(aset js/goog "isProvided_" (fn [name] (if (get-in @c-state (list :cljs.analyzer/namespaces (symbol name)))
                                         false
                                         (is-provided name))))

(defn- transit-json->cljs
  [json]
  (let [rdr (transit/reader :json)]
    (transit/read rdr json)))

(defn get-cache
  "Read from preload cache"
  [k]
  (gobj/getValueByKeys js/window (clj->js (conj [".cljs_live_cache"] k))))

(def blank-result {:source "" :lang :js})

(defn load-fn
  "Load requirements from bundled deps"
  [{:keys [path macros]} cb]
  (let [path (cond-> path
                     macros (str "$macros"))]
    (cb (if (@loaded path)
          blank-result
          (let [[source lang] (or (some-> (get-cache (str path ".js"))
                                          (list :js))
                                  (some-> (get-cache (str path ".clj"))
                                          (list :clj)))
                cache (get-cache (str path ".cache.json"))]
            (swap! loaded conj path)
            #_(println {:path   path
                        :macros macros
                        :lang   lang
                        :source (some-> source (subs 0 50))})
            (cond-> blank-result
                    source (merge {:source source
                                   :lang   lang})
                    cache (merge {:cache (transit-json->cljs cache)})))))))

(defn compiler-opts
  []
  {:load          load-fn
   :eval          cljs/js-eval
   :ns            (:ns @c-env)
   :context       :expr
   :source-map    true
   :def-emits-var true})

(defn caches-by-index [index]
  (for [path (gobj/getValueByKeys js/window #js [".cljs_live_cache" index])]
    [path (gobj/getValueByKeys js/window #js [".cljs_live_cache" path])]))

(defn eval
  "Eval a single form, keeping track of current ns in fire-env."
  [form cstate]
  (let [result (atom)
        ns? (and (seq? form) (#{'ns} (first form)))
        macro-ns? (and (seq? form) (= 'defmacro (first form)))]
    (cljs/eval cstate form (cond-> (compiler-opts)
                                   macro-ns?
                                   (update :ns #(symbol (str % "$macros")))) (partial reset! result))
    (when (and ns? (contains? @result :value))
      (swap! c-env assoc :ns (second form)))
    @result))

(defn eval-str
  "Eval string by first reading all top-level forms, then eval'ing them one at a time."
  ([src] (eval-str src c-state))
  ([src cstate]
   (let [forms (try (read-string (str "[\n" src "]"))
                    (catch js/Error e
                      (set! (.-data e) (clj->js (update (.-data e) :line dec)))
                      {:error e}))]
     (if (contains? forms :error)
       forms
       (loop [forms forms]
         (let [{:keys [error] :as result} (eval (first forms) cstate)
               remaining (rest forms)]
           (if (or error (empty? remaining))
             result
             (recur remaining))))))))

(defn preloads!
  "Load bundled analysis caches and macros into compiler state"
  [c-state]
  (log "Starting preloads...")

  (log "Analysis Cache Preloads:")
  (doseq [[path src] (caches-by-index "preload_caches")]
    (swap! loaded conj path)
    (let [{:keys [name] :as cache} (transit-json->cljs src)]
      (cljs/load-analysis-cache! c-state name cache)))

  (let [eval-f #(eval '(require 'goog.events) c-state)]
    (println (eval-f))
    (log "Google Closure Libary Preloads:")
    (doseq [[path src] (caches-by-index "preload_goog")]
      (swap! loaded conj path)
      (js/eval src)
      (println "added" path (eval-f))))

  (log "Analysis Cache Preloads:")
  (doseq [path ["cljs/core.cache.json" "cljs/core$macros.cache.json"]]
    (swap! loaded conj path)
    (let [{:keys [name] :as cache} (transit-json->cljs (gobj/getValueByKeys js/window #js [".cljs_live_cache" path]))]
      (cljs/load-analysis-cache! c-state name cache)))

  )

;; some macros we can and bundle the js (those which work with Planck)
;; some macros we have to include in the compiled-build and only run the analysis cache
;; some macros we have to include the source and run in the client