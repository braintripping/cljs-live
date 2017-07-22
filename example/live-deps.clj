{:cljsbuild-out "resources/public/js/compiled/out"
 :output-dir    "resources/public/js/compiled"
 :source-paths  ["src"]
 :dependencies  [[quil "2.5.0"]
                 [re-view-hiccup "0.1.6"]
                 [cljsjs/bcrypt "2.3.0-0"]
                 [cljsjs/marked "0.3.5-0"]]
 :bundles       [{:name          cljs.core
                  :require-cache [cljs.core cljs.core$macros]}
                 {:name     cljs-live.user
                  :require  [npm.marked
                             cljsjs.bcrypt
                             goog.events
                             re-view-hiccup.core
                             [quil.core :include-macros true]]
                  :provided [cljs-live.examples]}
                 {:name         goog
                  :require-goog [goog.string goog.ui.Zippy]}]}

