(defproject cljs-live "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [com.cognitect/transit-cljs "0.8.239"]

                 [sablono "0.7.5"]
                 [cljsjs/react "15.3.1-0"]
                 [cljsjs/react-dom "15.3.1-0"]

                 [quil "2.5.0"]

                 [org.clojars.mhuebert/firelisp "0.1.0-SNAPSHOT"]
                 [cljsjs/bcrypt "2.3.0-0"]]

  :npm {:dependencies [[stylus "0.54.5"]
                       [marked "0.3.6"]
                       [google-closure-compiler "20161024.2.0"
                        google-closure-library "20161024.0.0"
                        mock-browser "0.92.12"]]
        :package      {:scripts {:postinstall "script/script/copy_npm_deps.cljs;"}}}

  :plugins [[lein-npm "0.6.2"]
            [lein-cljsbuild "1.1.4" :exclusions [org.clojure/clojure]]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled/out" ".planck_cache"]

  :source-paths ["src" "script"])
