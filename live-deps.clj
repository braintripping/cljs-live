{:bundles [{:require        [[npm.marked]                   ;; a manually packaged npm dep
                             [cljsjs.bcrypt]                ;; a cljsjs foreign lib
                             [firelisp.db :include-macros true] ;; a cljs lib with foreign dep
                             [firelisp.rules :include-macros true]
                             [goog.events]                  ;; a Google Closure Library dep

                             ;; not working
                             ;[sablono.core] ;; self-host incompatible macro structure?
                             ;[quil.core :include-macros true] ;; Planck can't load because of browser dependency, need another way to precompile
                             ]

            ;:require-macros []                              ;; will be put in (ns..)
            :preload-caches []                              ;; caches to be loaded at init (transit/json)
            :preload-macros [firelisp.rules]                ;; macros to be run at init (clj)
            :output-to      "resources/public/js/compiled/cljs_live_cache.js"}]}