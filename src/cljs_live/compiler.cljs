(ns cljs-live.compiler
  (:require [cljs.js :as cljs]
            [cljs.tools.reader :as r]
            [cognitect.transit :as transit]
            [goog.object :as gobj]))

(enable-console-print!)
(defn log [& args]
  ; (apply prn args)
  )

(defn- transit-json->cljs
  [json]
  (let [rdr (transit/reader :json)]
    (transit/read rdr json)))

(defonce fire-env (atom {:ns 'cljs-live.dev}))
(defonce fire-st (cljs/empty-state))

(defn get-cache [k]
  (log [:get-cache k])
  (gobj/getValueByKeys js/window (clj->js (conj [".cljs_live_cache"] k))))

(def fake-load-result {:source "" :lang :js})

(defn load-js [{:keys [path macros]} cb]
  (let [path (cond-> path
                     macros (str "$macros"))
        source (get-cache (str (munge path) ".js"))
        cache (get-cache (str (munge path) ".cache.json"))]
    (if source
      (log [:load-js path
            {:source (some-> source (subs 0 10))}
            {:cache (some-> cache (subs 0 10))}])
      (log [:no-source path]))
    (cb (if source
          (cond-> {:source source
                   :lang   :js}
                  cache (assoc :cache (transit-json->cljs cache)))
          fake-load-result))))

(defn eval [form]
  (let [result (atom)
        ns? (and (seq? form) (= 'ns (first form)))
        t0 (.now js/Date)]
    (cljs/eval fire-st form {:load          load-js
                             :eval          cljs/js-eval
                             :ns            (:ns @fire-env)
                             :context       :expr
                             :source-map    true
                             :def-emits-var true} (partial reset! result))
    (when (and ns? (contains? @result :value))
      (swap! fire-env assoc :ns (second form)))
    (log [:eval (- (.now js/Date) t0) form])
    (when-let [error (:error @result)]
      (log [:eval-error error]))
    @result))

(defn eval-str [src]
  (let [forms (try (r/read-string (str "[" src "]"))
                   (catch js/Error e
                     (.error js/console "read-str error" e)
                     (prn (.-data e))))]
    (last (for [form forms]
            (eval form)))))
(defonce __
         (let [cache-names (atom #{})
               t0 (.now js/Date)]
           (doseq [cache-str (gobj/getValueByKeys js/window #js [".cljs_live_cache" "preload-caches"])]
             (let [{:keys [name] :as cache} (transit-json->cljs cache-str)]
               (swap! cache-names conj name)
               (cljs/load-analysis-cache! fire-st name cache)))
           (log [:cache-preload (- (.now js/Date) t0) @cache-names])))