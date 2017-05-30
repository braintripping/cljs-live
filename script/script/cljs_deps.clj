#!/usr/bin/env lein exec -p
(ns script.cljs-deps
  (:require [cljs.closure :as cljsc]
            [cljs.build.api :as api]
            [cljs.env :as env]
            [clojure.string :as string]
            [clojure.tools.reader :as r]
            [clojure.java.io :as io]
            [cljs.js-deps :as deps]))

(defn compile-cljs [src-path output-dir]
  (api/build src-path {:output-dir     output-dir
                       :dump-core      false
                       :parallel-build true
                       :optimizations  :none}))

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

(let [{:keys [bundles cljsbuild-out source-paths]} (->> (cmd-arg "--deps")
                                                        slurp
                                                        r/read-string)
      provides (try {:value (doall (for [{:keys [provided dependencies]} bundles]
                                     (do (when dependencies (install-deps dependencies))
                                         (doseq [s source-paths]
                                           (compile-cljs s cljsbuild-out))
                                         (transitive-deps provided))))}
                    (catch Exception e {:error e}))
      classpath (time (->> (map :jar (alembic.still/dependency-jars))
                           (string/join ":")))]
  (println (str "__BEGIN_CLASSPATH__"
                classpath
                "__END_CLASSPATH__"))
  (prn provides))


