(ns script.build
  (:require [cljs.build.api]))

(prn "Starting compile...")

(def watch? (= "--watch" (first *command-line-args*)))
(def optimizations? true)

(let [build-fn (if watch?
                 cljs.build.api/watch
                 cljs.build.api/build)]
  (time (build-fn "src"
                  (cond-> {:main            "cljs-live.eval"
                           :output-to       "resources/public/js/compiled/cljs_live_eval.js"
                           :output-dir      "resources/public/js/compiled/out"
                           :cache-analysis  true
                           :dump-core       false
                           :parallel-build  true
                           :optimizations   (if optimizations? :simple :none)
                           ;:closure-defines {"process_closure_primitives" true}
                           ;:pseudo-names   true ;; if you run into externs hell, enable this to debug
                           }
                          watch? (assoc :source-map (if optimizations?
                                                      "resources/public/js/compiled/cljs_live.js.map"
                                                      true))
                          optimizations? (assoc :asset-path "js/compiled/out")
                          ))))