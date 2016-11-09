(ns cljs-live.compiler
  (:require [cljs.js :as cljs]
            [cljs.tools.reader :as r]
            [cognitect.transit :as transit]
            [goog.object :as gobj]))

(enable-console-print!)

(defonce fire-st (cljs/empty-state))
(defonce fire-env (atom {:ns 'cljs-live.user}))

(defn- transit-json->cljs
  [json]
  (let [rdr (transit/reader :json)]
    (transit/read rdr json)))

(defn get-cache
  "Read from preload cache"
  [k]
  (gobj/getValueByKeys js/window (clj->js (conj [".cljs_live_cache"] k))))

(defn load-fn
  "Load requirements from bundled deps"
  [{:keys [path macros]} cb]
  (let [path (cond-> path
                     macros (str "$macros"))
        [source lang] (or (some-> (get-cache (str (munge path) ".js"))
                                  (list :js))
                          (some-> (get-cache (str (munge path) ".clj"))
                                  (list :clj)))
        cache (get-cache (str (munge path) ".cache.json"))]

    (cb (if source
          (cond-> {:source source
                   :lang   lang}
                  cache (assoc :cache (transit-json->cljs cache)))
          {:source "" :lang :js}))))

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

(defn preloads!
  "Load bundled analysis caches and macros into compiler state"
  []

  (doseq [src (by-index "preload_caches")]
    (let [{:keys [name] :as cache} (transit-json->cljs src)]
      (cljs/load-analysis-cache! fire-st name cache)))

  (doseq [src (by-index "preload_goog")] (js/eval src))

  (doseq [src (by-index "preload_macros")]
      (cljs/eval-str fire-st src "macro_load" (assoc (compiler-opts) :macros-ns true) #(when (:error %)
                                                                                        (.debug js/console (:error %))
                                                                                        #_(some->> (.-cause (:error %))
                                                                                                   ((fn [e]
                                                                                                      (.error js/console e)
                                                                                                      (.-cause e)))
                                                                                                   (.error js/console))))))
(defn eval
  "Eval a single form, keeping track of current ns in fire-env."
  [form]
  (let [result (atom)
        ns? (and (seq? form) (#{'ns} (first form)))]
    (cljs/eval fire-st form (cond-> (compiler-opts)
                                    (= 'defmacro (first form))
                                    (assoc :macros-ns true)) (partial reset! result))
    (when (and ns? (contains? @result :value))
      (swap! fire-env assoc :ns (second form)))
    @result))

(defn eval-str
  "Eval string by first reading all top-level forms, then eval'ing them one at a time."
  [src]
  (let [forms (try (r/read-string (str "[" src "]"))
                   (catch js/Error e
                     (.debug js/console "read-str error" e)
                     (prn src)
                     (prn (.-data e))))]
    (last (for [form forms]
            (eval form)))))

;; some macros we can and bundle the js (those which work with Planck)
;; some macros we have to include in the compiled-build and only run the analysis cache
;; some macros we have to include the source and run in the client