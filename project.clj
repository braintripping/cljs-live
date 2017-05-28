(defproject cljs-live "0.1.15"
  :description "Tools for bunding dependencies for self-hosted ClojureScript"
  :url "https://github.com/mhuebert/cljs-live"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.9.0-alpha16"]
                 [org.clojure/clojurescript "1.9.542"]

                 [com.cognitect/transit-cljs "0.8.239"]
                 [alembic "0.3.2"]

                 ]

  :cljsbuild {:builds []}

  :profiles {:dev {:dependencies [[quil "2.5.0"]
                                  [re-view-hiccup "0.1.6"]
                                  [cljsjs/react "15.5.4-0"]
                                  [cljsjs/react-dom "15.5.4-0"]
                                  [cljsjs/bcrypt "2.3.0-0"]
                                  [cljsjs/marked "0.3.5-0"]]}}

  :npm {:dependencies [[stylus "0.54.5"]
                       [marked "0.3.6"]]}

  :plugins [[lein-npm "0.6.2"]
            [lein-cljsbuild "1.1.6" :exclusions [org.clojure/clojure]]]

  :lein-release {:deploy-via :clojars}

  :clean-targets ^{:protect false} ["resources/public/js/compiled/out" ".planck_cache"]

  :source-paths ["src" "script"])
