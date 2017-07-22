(ns cljs-live.scratch.lumo-scratch
  (:require [lumo.cljs-deps :as deps]
            [lumo.closure :as cljsc]
            [lumo.repl :as repl]
            [cljs.env :as env]))

(defn map-count [m]
  (reduce-kv (fn [m k v] (assoc m k (count v))) {} m))

;; set up a compiler state with options
(def opts (cljsc/make-options {:optimizations :simple
                               :source-map    false
                               :target        :browser}))

(def live-st (env/default-compiler-env (cljsc/add-externs-sources opts)))
(swap! live-st assoc :js-dependency-index (deps/js-dependency-index opts))

(binding [env/*compiler* live-st]
  (let [opts (-> opts
                 #_(cljsc/add-implicit-options)
                 (cljsc/process-js-modules))]
    ;; initialize compiler state with js-dependencies
    (swap! env/*compiler* assoc :js-dependency-index (deps/js-dependency-index opts))

    ;; nil
    (prn [:js-dependencies 'maria.user] (count (cljsc/js-dependencies {} ['maria.user])))
    (prn [:js-dependencies "goog.events"] (count (cljsc/js-dependencies {} ["goog.events"])))
    ;; returns source for single namespace

    (prn [:source-for-namespace 'maria.user] (cljsc/source-for-namespace 'maria.user live-st))
    (prn [:source-for-namespace "goog.events"] (cljsc/source-for-namespace "goog.events" live-st))

    ;; err
    (println :find-cljs-dependencies "(takes a while)")
    (let [cljs-deps (-> (cljsc/find-cljs-dependencies '[maria.user])
                        (cljsc/add-dependency-sources)
                        (deps/dependency-order))]

      deps/dependency-order
      cljsc/add-js-sources

      (prn :find-cljs-dependencies (count cljs-deps))
      (prn :the-first (first cljs-deps))
      (doseq [dep cljs-deps]
        (println "     " (:ns dep)))))

  )

(defn -main [])

