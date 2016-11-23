#!/usr/bin/env lein exec -p

(ns script.cljs-closure-deps
  (:require [cljs.closure :as cljsc]
            [cljs.env :as env]
            [clojure.string :as string]
            [clojure.tools.reader :as r]
            [cljs.compiler :as comp]
            [clojure.java.io :as io]
            [cljs.js-deps :as deps]))

(defn ns->uri [ns exts]
  (first (for [ext exts
               :let [ns-str (str (comp/munge ns {}))
                     path (string/replace ns-str \. \/)
                     relpath (str path ext)
                     uri (io/resource relpath)]
               :when uri]
           uri)))

(defn cljs-requires [namespace]
  (deps/-requires {:requires [(name namespace)]
                   :type     :seed
                   :url      (ns->uri namespace [".cljs" [".cljc"]])}))

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

(defn macro-deps [input]
  (let [ast (:ast input)]
    (set (concat
           (vals (:require-macros ast))
           (vals (:use-macros ast))))))

(def goog-dep? #(= (:group %) :goog))

(def dep-sort deps/dependency-order)

(defn parse-command-line-args [args]
  (loop [opts {}
         current-opt nil
         remaining args]
    (cond (empty? remaining) opts
          (string/starts-with? (first remaining) "-") (recur (update opts (first remaining) #(if (nil? %) [] %))
                                                             (first remaining)
                                                             (rest remaining))
          :else (recur (update opts current-opt conj (first remaining))
                       current-opt
                       (rest remaining)))))

(defn cmd-arg [n]
  (get (parse-command-line-args *command-line-args*) n))

(defn core-dep? [ijs]
  (#{'cljs.core 'cljs.core$macros} (:ns ijs)))

(let [require-names (set (map symbol (cmd-arg "--require")))
      transitive-requires (->> (apply cljs-deps require-names)
                               (filter #(not (contains? #{'cljs.env} (:ns %)))))
      direct-requires (->> transitive-requires
                           (filter #(contains? require-names (:ns %)))
                           set)
      provided (->> (cmd-arg "--provided")
                    (apply cljs-deps)
                    set)

      {:keys [bundle-source
              bundle-cache
              bundle-goog
              bundle-foreign]} (group-by #(let [provided? (contains? provided %)
                                                direct? (contains? direct-requires %)]
                                           (cond (core-dep? %) :bundle-cache
                                                 (goog-dep? %) (when-not provided? :bundle-goog)
                                                 (:foreign %) (when-not provided? :bundle-foreign)
                                                 (not provided?) :bundle-source
                                                 direct? :bundle-cache
                                                 :else :bundle-cache)) transitive-requires)]

  (prn {:require-source       (map :ns bundle-source)
        :require-macros       (mapcat macro-deps transitive-requires)
        :require-caches       (keep identity (map :ns bundle-cache))
        :require-foreign-deps (map (comp str :file) bundle-foreign)
        :require-goog         (map (comp str :file) bundle-goog)}))