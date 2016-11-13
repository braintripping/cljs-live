(defproject org.clojars.mhuebert/cljs-live "0.1.2-SNAPSHOT"
  :description "Tools for bunding dependencies for self-hosted ClojureScript"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [com.cognitect/transit-cljs "0.8.239"]]

  :profiles {:examples {:dependencies [[quil "2.5.0"]
                                       [cljsjs/bcrypt "2.3.0-0"]
                                       [sablono "0.7.5"]
                                       [cljsjs/react "15.3.1-0"]
                                       [cljsjs/react-dom "15.3.1-0"]]}}

  :npm {:dependencies [[stylus "0.54.5"]
                       [marked "0.3.6"]
                       [google-closure-compiler "20161024.2.0"
                        google-closure-library "20161024.0.0"]]
        :package      {:scripts {:postinstall "script/script/copy_deps.cljs marked;"}}}

  :plugins [[lein-npm "0.6.2"]
            [lein-cljsbuild "1.1.4" :exclusions [org.clojure/clojure]]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled/out" ".planck_cache"]

  :source-paths ["src" "script"])
