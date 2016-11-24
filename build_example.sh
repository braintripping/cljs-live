java -cp ~/cljs.jar:src:`lein with-profile dev classpath` clojure.main script/build_example.clj $@;
./bundle.sh --deps live-deps.clj;