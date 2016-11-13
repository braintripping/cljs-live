#!/usr/bin/env planck
(ns script.copy-npm-deps
  (:require [cljs.tools.reader :as r]
            [planck.core :refer [slurp *command-line-args*]]
            [planck.shell :refer [sh]]))

(sh "rm" "-rf" "src/npm/")
(sh "mkdir" "src/npm/")

(doseq [name *command-line-args*]
  (prn name)
  (sh "cp" "-r" (str "node_modules/" name) (str "src/npm/" name)))

(sh "cp" "src/deps.cljs" "opts.clj")