#!/usr/bin/env bash

lein npm install;
cp src/deps.cljs opts.clj;
script/bootstrap.cljs $@;
java -cp ~/cljs.jar:src:`lein classpath` clojure.main script/build.clj $@;