#!/usr/bin/env bash
java -cp ~/cljs.jar:src:`lein with-profile dev classpath` clojure.main script/build_example.clj $@;
./bundle.sh live-deps.clj;