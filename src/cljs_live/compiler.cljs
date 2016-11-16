(ns cljs-live.compiler
  (:require [cljs.js :as cljs]
            [cljs.tools.reader :as r]
            [cljs.tools.reader.reader-types :as rt]
            [cognitect.transit :as transit]
            [goog.object :as gobj]))

(defn read-string [s]
  (when (and s (not= "" s))
    (r/read {} (rt/indexing-push-back-reader s))))

(def debug? false)
(enable-console-print!)
(def log (if debug? println list))

(defonce fire-st (cljs/empty-state))
(defonce fire-env (atom {}))
(defonce loaded (atom #{"cljs/core" "cljs/core$macros"}))

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
          (let [[source lang] (or (some-> (get-cache (str (munge path) ".js"))
                                          (list :js))
                                  (some-> (get-cache (str (munge path) ".clj"))
                                          (list :clj)))
                cache (get-cache (str (munge path) ".cache.json"))]
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
   :ns            (:ns @fire-env)
   :context       :expr
   :source-map    true
   :def-emits-var true})

(defn by-index [index]
  (for [path (gobj/getValueByKeys js/window #js [".cljs_live_cache" index])]
    (gobj/getValueByKeys js/window #js [".cljs_live_cache" path])))

(defn eval
  "Eval a single form, keeping track of current ns in fire-env."
  [form]
  (let [result (atom)
        ns? (and (seq? form) (#{'ns} (first form)))
        macro-ns? (and (seq? form) (= 'defmacro (first form)))]
    (cljs/eval fire-st form (cond-> (compiler-opts)
                                    macro-ns?
                                    (update :ns #(symbol (str % "$macros")))) (partial reset! result))
    (when (and ns? (contains? @result :value))
      (swap! fire-env assoc :ns (second form)))
    @result))

(defn eval-str
  "Eval string by first reading all top-level forms, then eval'ing them one at a time."
  [src]
  (let [forms (try (read-string (str "[\n" src "]"))
                   (catch js/Error e
                     (set! (.-data e) (clj->js (update (.-data e) :line dec)))
                     {:error e}))]
    (if (contains? forms :error)
      forms
      (loop [forms forms]
        (let [{:keys [error] :as result} (eval (first forms))
              remaining (rest forms)]
          (if (or error (empty? remaining))
            result
            (recur remaining)))))))

(defn preloads!
  "Load bundled analysis caches and macros into compiler state"
  []

  (log "Starting preloads...")

  (log "Analysis Caches:")
  (doseq [src (by-index "preload_caches")]
    (let [{:keys [name] :as cache} (transit-json->cljs src)]
      (cljs/load-analysis-cache! fire-st name cache)))

  (log "Google Closure Libary deps:")
  (doseq [src (by-index "preload_goog")] (js/eval src))

  (log "Macros:")
  (doseq [src (by-index "preload_macros")]
    (eval-str src)))

;; some macros we can and bundle the js (those which work with Planck)
;; some macros we have to include in the compiled-build and only run the analysis cache
;; some macros we have to include the source and run in the client