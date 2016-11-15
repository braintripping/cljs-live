{:dependencies   [[quil "2.5.0"]
                  [cljsjs/bcrypt "2.3.0-0"]]
 :require        [[npm.marked]
                  [cljsjs.bcrypt]
                  [goog.events]
                  [quil.core :include-macros true]]
 :require-caches [cljs-live.sablono]
 :cljsbuild-out  "resources/public/js/compiled/out"
 :output-to      "resources/public/js/compiled/cljs_live_cache.js"}

;; other options:
;;  :require-macros []                              ;; will be put in (ns..)
;;  :preload-caches [cljs-live.sablono]                        ;; caches to be loaded at init (transit/json)
;;  :preload-macros []                              ;; macros to be run at init (clj)