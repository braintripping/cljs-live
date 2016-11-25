(ns cljs-live.compiler
  (:require [cljs.js :as cljs]
            [cljs.tools.reader :as r]
            [cljs.tools.reader.reader-types :as rt]
            [cognitect.transit :as transit]))

(def cljs-cache (js->clj (aget js/window ".cljs_live_cache")))

(defn read-string [s]
  (when (and s (not= "" s))
    (r/read {} (rt/indexing-push-back-reader s))))

(def debug? false)
(enable-console-print!)
(def log (if debug? println list))

(defonce c-state (cljs/empty-state))
(defonce c-env (atom {}))
(defonce loaded (atom #{}))


(defn- transit-json->cljs
  [json]
  (let [rdr (transit/reader :json)]
    (transit/read rdr json)))

(def blank-result {:source "" :lang :js})

(defn load-fn
  "Load requirements from bundled deps"
  [{:keys [path macros]} cb]

  (let [path (cond-> path
                     macros (str "$macros"))]
    (cb (if (@loaded path)
          blank-result
          (let [[source lang] (or (some-> (get cljs-cache (str path ".js"))
                                          (list :js))
                                  (some-> (get cljs-cache (str path ".clj"))
                                          (list :clj)))
                cache (get cljs-cache (str path ".cache.json"))]
            (swap! loaded conj path)
            #_(println {:path   path
                        :macros macros
                        :lang   lang
                        :source (some-> source (subs 0 40))
                        :cache  (some-> cache (subs 0 40))})
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

(defn load-cache
  "Load a transit-encoded analysis cache into compiler state"
  [cache-str c-state]
  (let [{:keys [name] :as cache} (transit-json->cljs cache-str)]
    (cljs/load-analysis-cache! c-state name cache)))

(defn preloads!

  "Load bundled analysis caches and macros into compiler state"
  [c-state]
  (log "Starting preloads...")

  ;; monkey-patch goog.isProvided_ to return false if we have a matching namespace in the compiler
  ;; https://github.com/clojure/clojurescript/wiki/Custom-REPLs#eliminating-loaded-libs-tracking
  (let [is-provided (aget js/goog "isProvided_")]
    (aset js/goog "isProvided_" (fn [name] (if (get-in @c-state (list :cljs.analyzer/namespaces (symbol name)))
                                             false
                                             (is-provided name)))))


  (log "Google Closure Libary Preloads:")
  (doall (for [path (get cljs-cache "preload_goog")
               :let [src (get cljs-cache path)]]
           (do (swap! loaded conj path)
               (js/eval src))))

  (log "Analysis Cache Preloads:")
  (doseq [path ["cljs/core.cache.json" "cljs/core$macros.cache.json"]]
    (swap! loaded conj path)
    (load-cache (get cljs-cache path) c-state)))