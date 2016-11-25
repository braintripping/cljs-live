{#_:dependencies  #_[[quil "2.5.0"]
                     [cljsjs/bcrypt "2.3.0-0"]
                     [sablono "0.7.5"]
                     [cljsjs/react "15.3.1-0"]
                     [cljsjs/react-dom "15.3.1-0"]]
 :require       [npm.marked
                 cljsjs.bcrypt
                 goog.events
                 [quil.core :include-macros true]]
 :require-cache [cljs-live.sablono]
 :provided      [cljs-live.examples]
 :cljsbuild-out "resources/public/js/compiled/out"
 :output-to     "resources/public/js/compiled/cljs_live_cache.js"}