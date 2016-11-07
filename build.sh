#!/usr/bin/env bash

lein npm install;
cp src/deps.cljs opts.clj;
planck -c `lein classpath` -i script/bootstrap.cljs -K;
java -cp ~/cljs.jar:src:`lein classpath` clojure.main script/build.clj $1 $2;