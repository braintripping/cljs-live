#!/usr/bin/env lein exec -p
(ns script.cljs-deps
  (:require [cljs.closure :as cljsc]
            [cljs.env :as env]
            [clojure.string :as string]
            [clojure.tools.reader :as r]
            [clojure.java.io :as io]
            [cljs.js-deps :as deps]))

(defn cljs-deps [& inputs]
  (binding [env/*compiler* (env/default-compiler-env)]
    (let [opts (->> (cljsc/get-upstream-deps*)
                    (merge-with concat (some-> (io/resource "deps.cljs")
                                               slurp
                                               r/read-string)))]
      (swap! env/*compiler* assoc :js-dependency-index (deps/js-dependency-index opts))
      (-> (set inputs)
          (cljsc/find-cljs-dependencies)
          (cljsc/add-js-sources opts)))))

(defn cmd-arg [arg-name]
  (first (for [[n v] (partition 2 1 *command-line-args*)
               :when (= n arg-name)]
           v)))

(require 'alembic.still)

(defn install-deps [dependencies]
  (alembic.still/distill (seq dependencies)))

(defn transitive-deps
  "Return sequence of transitive dependencies of deps, as namespace symbols"
  [deps]
  (->> (set (apply cljs-deps deps))
       (mapcat deps/-requires)
       set
       (#(disj % nil))
       (map symbol)
       set))

(let [provides (try {:value (doall (for [{:keys [provided dependencies]} (:bundles (->> (cmd-arg "--deps")
                                                                                        slurp
                                                                                        r/read-string))]
                                     (do (when dependencies (install-deps dependencies))
                                         (transitive-deps provided))))}
                    (catch Exception e {:error e}))]
  (println (str "__BEGIN_CLASSPATH__"
                (->> (map :jar (alembic.still/dependency-jars))
                     (string/join ":"))
                "__END_CLASSPATH__"))
  (prn provides))


