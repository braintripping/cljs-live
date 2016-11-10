#!/usr/bin/env bash


lein npm install;
java -cp ~/cljs.jar:src:`lein classpath` clojure.main script/script/build.clj $@;
script/script/bootstrap.cljs $@;
