#!/usr/bin/env bash

lein npm install;
script/script/bootstrap.cljs $@;
java -cp ~/cljs.jar:src:`lein classpath` clojure.main script/script/build.clj $@;