{:cljsbuild-out "resources/public/js/compiled/out"
 :output-dir    "resources/public/js/compiled"
 :source-paths  ["src"]
 :bundles       [{:name          cljs.core
                  :require-cache [cljs.core cljs.core$macros]}
                 {#_:dependencies  #_[[quil "2.5.0"]
                                      [cljsjs/bcrypt "2.3.0-0"]
                                      [sablono "0.7.5"]
                                      [cljsjs/react "15.3.1-0"]
                                      [cljsjs/react-dom "15.3.1-0"]]
                  :name     cljs-live.user
                  :require  [npm.marked
                             cljsjs.bcrypt
                             goog.events
                             cljs-live.sablono
                             [quil.core :include-macros true]]
                  :provided [cljs-live.examples]}
                 {:name         goog
                  :require-goog [goog.string goog.ui.Zippy]}]}

