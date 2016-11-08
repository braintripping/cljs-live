[{:require        [[npm.marked]                             ;; manually packaged npm dep
                   [cljsjs.bcrypt]                          ;; cljsjs foreign lib

                   [firelisp.db :include-macros true]
                   [firelisp.rules :include-macros true]

                   ;; not working
                   ;[sablono.core]
                   ;[quil.core :include-macros true]


                   ]
  :require-macros []
  :preload-caches  [cljs.analyzer]
  ;:preload-macros [sablono.core sablono.compiler]
  :output-to      "resources/public/js/compiled/cljs_live_cache.js"}]