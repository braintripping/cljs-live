(ns script.build
  (:require [cljs.build.api]))

(prn "Starting compile...")

(let [build-fn (if (= "--watch" (first *command-line-args*))
                 cljs.build.api/watch
                 cljs.build.api/build)]
  (time (build-fn "src"
                  {:main           "cljs-live.examples"
                   :output-to      "resources/public/js/compiled/cljs_live.js"
                   :output-dir     "resources/public/js/compiled/out"
                   :cache-analysis true
                   :dump-core      false
                   :parallel-build true
                   :optimizations  :simple
                   ;:asset-path     "js/compiled/out" ;; for :optimizations :none, in development
                   ;:pseudo-names   true ;; if you run into externs hell, enable this to debug
                   })))

