(ns script.build
  (:require [cljs.build.api]))

(prn "Starting compile..." (first *command-line-args*))

(def path "resources/public/js/compiled/")

(let [[env src-path] *command-line-args*
      build (if (= "dev" env)
              cljs.build.api/watch
              cljs.build.api/build)]
  (time (build src-path
               {:output-to      (str path "cljs_live.js")
                :output-dir     (str path "out")
                :pseudo-names   true
                :optimizations  :simple
                :dump-core      false
                :source-map     (str path "cljs_live.js.map")
                :parallel-build true})))

