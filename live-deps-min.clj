{:require        [[cljs.spec :include-macros true]
                  [cljs.spec.impl.gen :include-macros true]]
 :require-macros [cljs.spec
                  cljs.spec.impl.gen]
 :require-caches [s$macros
                  cljs.compiler
                  cljs.source-map.base64
                  cljs.tools.reader.impl.commons
                  cljs.source-map.base64-vlq
                  cljs.core$macros
                  cljs.tools.reader
                  cljs.analyzer.macros$macros
                  cljs.tools.reader.reader-types$macros
                  cljs.tools.reader.reader-types
                  c$macros
                  cljs.spec.impl.gen$macros
                  cljs.analyzer.api
                  cljs.env
                  gen$macros
                  clojure.set
                  cljs.tools.reader.impl.utils
                  cljs.env.macros$macros
                  cljs.tagged-literals
                  cljs.support$macros
                  cljs.spec.impl.gen
                  cljs.analyzer
                  cljs.source-map
                  clojure.string
                  cljs.reader
                  clojure.walk
                  cljs.spec
                  cljs.compiler.macros$macros]
 :cljsbuild-out  "resources/public/js/compiled/out"
 :output-to      "resources/public/js/compiled/cljs_live_cache_core.js"}