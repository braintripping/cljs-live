#!/usr/bin/env planck
(ns script.copy-npm-deps
  (:require [cljs.tools.reader :as r]
            [planck.core :refer [slurp]]
            [planck.shell :refer [sh]]))

(def npm-deps (->> (slurp "project.clj")
                   r/read-string
                   (drop 3)
                   (apply hash-map)
                   (#(get-in % [:npm :dependencies]))))

(sh "rm" "-rf" "src/npm/*")

(doseq [[name _] npm-deps]
  (prn name)
  (sh "cp" "-r" (str "node_modules/" name) (str "src/npm/" name)))

(sh "cp" "src/deps.cljs" "opts.clj")