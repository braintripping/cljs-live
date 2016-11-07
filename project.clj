(defproject cljs-live "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]

                 [malabarba/lazy-map "1.1"]

                 [replumb "0.2.4"]
                 [com.cognitect/transit-cljs "0.8.239"]     ;; required by replumb

                 [org.clojars.mhuebert/firelisp "0.1.0-SNAPSHOT"
                  :exclusions [cljsjs/react
                               cljsjs/react-dom
                               cljsjs/react-dom-server]]

                 [org.clojars.mhuebert/re-db "0.1.1-SNAPSHOT"]
                 [org.clojars.mhuebert/re-view "0.1.1-SNAPSHOT"]]
  :npm {:dependencies [[marked "0.3.6"]
                       [error-stack-parser "1.3.6"]
                       [google-closure-compiler-js "20161024.0.0"]]
        :package      {:scripts {:postinstall "rm -rf src/js/*;
                                               cp -r node_modules/marked src/js/marked;
                                               cp -r node_modules/error-stack-parser src/js/error-stack-parser;
                                               "}}}
  :plugins [[lein-figwheel "0.5.8"]
            [lein-npm "0.6.2"]
            [lein-cljsbuild "1.1.4" :exclusions [org.clojure/clojure]]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled/out"
                                    "target"]

  :source-paths ["src" "src_eval"]

  :cljsbuild {:builds [{:id           "dev"
                        :source-paths ["src"]
                        :figwheel     true
                        :compiler     {:main                 "cljs-live.core"
                                       :asset-path           "js/compiled/out"
                                       :output-to            "resources/public/js/compiled/cljs_live.js"
                                       :output-dir           "resources/public/js/compiled/out"
                                       :source-map-timestamp true
                                       :parallel-build       true}}
                       {:id           "prod"
                        :source-paths ["src"]
                        :compiler     {:main          "cljs-live.core"
                                       :asset-path    "js/compiled/out"
                                       :output-to     "resources/public/js/compiled/cljs_live.js"
                                       :optimizations :advanced}}]}

  :figwheel {:css-dirs ["resources/public/css"]}

  :profiles {:dev {:dependencies [[binaryage/devtools "0.8.2"]
                                  [figwheel-sidecar "0.5.8"]
                                  [com.cemerick/piggieback "0.2.1"]]
                   ;; need to add dev source path here to get user.clj loaded
                   :source-paths ["src" "dev"]
                   ;; for CIDER
                   ;; :plugins [[cider/cider-nrepl "0.12.0"]]
                   :repl-options {; for nREPL dev you really need to limit output
                                  :init             (set! *print-length* 50)
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}})
