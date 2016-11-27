(ns cljs-live.compiler
  (:require [cljs.js :as cljs]
            [cljs.tools.reader :as r]
            [cljs.tools.reader.reader-types :as rt]
            [cognitect.transit :as transit]
            [goog.net.XhrIo :as xhr]))

(defonce is-provided (aget js/goog "isProvided_"))

(defn get-json [path cb]
  (xhr/send path
            (fn [e]
              (cb (.. e -target getResponseJson)))))

(def cljs-cache (atom {}))

(defn read-string [s]
  (when (and s (not= "" s))
    (r/read {} (rt/indexing-push-back-reader s))))

(def debug? false)
(enable-console-print!)
(def log (if debug? println list))

(defonce c-state (cljs/empty-state))
(defonce c-env (atom {}))


(defn- transit-json->cljs
  [json]
  (let [rdr (transit/reader :json)]
    (transit/read rdr json)))

(def blank-result {:source "" :lang :js})

(defn load-fn
  "Load requirements from bundled deps"
  [{:keys [path macros name] :as s} cb]

  (let [path (cond-> path
                     macros (str "$macros"))
        name (if-not macros name
                            (symbol (str name "$macros")))
        provided? (or (is-provided (munge (str name))) (boolean (aget js/goog "dependencies_" "nameToPath" (munge (str name)))))]
    (cb (if (*loaded-libs* (str name))
          blank-result
          (let [[source lang] (when-not provided?
                                (or (some-> (get @cljs-cache (str path ".js"))
                                            (list :js))
                                    (some-> (get @cljs-cache (str path ".clj"))
                                            (list :clj))))
                cache (get @cljs-cache (str path ".cache.json"))
                result (cond-> blank-result
                               source (merge {:source source
                                              :lang   lang})
                               cache (merge {:cache (transit-json->cljs cache)}))]
            #_(when (or cache source)
              (set! *loaded-libs* (conj *loaded-libs* (str name)))
              (println [(if (boolean source) "source" "      ")
                        (if (boolean cache) "cache" "     ")] name))
            result)))))

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

(defonce _loaded-libs (set! *loaded-libs* (or *loaded-libs* #{})))

(defonce goog-require (aget js/goog "require"))

(set! (.-require js/goog)
      (fn [name reload]
        (when (or (not (contains? *loaded-libs* name)) reload)
          (let [path (aget (.. js/goog -dependencies_ -nameToPath) name)
                goog? (.test #"^\.\./" path)
                provided? (is-provided name)]
            (cond provided? nil
                  goog? (do (set! *loaded-libs* (conj *loaded-libs* name))
                            (js/eval (get @cljs-cache (str "goog/" path))))
                  :else (do (set! *loaded-libs* (conj *loaded-libs* name))
                            (goog-require name reload)))))))

(defn fetch-bundle [path cb]
  (get-json path (fn [bundle]
                   (let [bundle (js->clj bundle)]
                     (swap! cljs-cache merge bundle)
                     (cb bundle)))))

(defn load-cache! [c-state cache]
  (let [{:keys [name] :as cache} (transit-json->cljs cache)]
    (when (and (not (*loaded-libs* (str name))) cache)
      (set! *loaded-libs* (conj *loaded-libs* (str name)))
      (cljs/load-analysis-cache! c-state name cache))))

(defn load-core-caches [c-state]
  (doseq [path ["cljs/core.cache.json" "cljs/core$macros.cache.json"]]
    (load-cache! c-state (get @cljs-cache path))))

(defn load-bundles!
  [c-state paths cb]
  (let [bundles (atom {})
        loaded (atom 0)
        total (count paths)]
    (doseq [path paths]
      (fetch-bundle path (fn [bundle]
                           (println :loaded (keys bundle))
                           (swap! bundles merge bundle)
                           (swap! loaded inc)
                           (when (= total @loaded)
                             (load-core-caches c-state)
                             (cb @bundles)))))))