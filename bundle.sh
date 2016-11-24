#!/usr/bin/env bash

USER_DIR=$(pwd);
USER_CLASSPATH=$(lein classpath);
SCRIPT_DIR=$(dirname $(python -c "import os,sys; print os.path.realpath(sys.argv[1])" "$0"))'/script/script';
CACHE_DIR=$USER_DIR'/.cljs_live_planck_cache'

mkdir -p $CACHE_DIR;
cp src/deps.cljs script/script/opts.clj;

#java -cp $USER_CLASSPATH clojure.main $SCRIPT_DIR'/cljs_deps.clj' --deps $USER_DIR'/'$1

PROVIDED=$(java -cp $USER_CLASSPATH clojure.main $SCRIPT_DIR'/cljs_deps.clj' --deps $USER_DIR'/'$1)

re="(.*)__END_CLASSPATH__(.*)"
[[ $PROVIDED =~ $re ]] && cp="${BASH_REMATCH[1]}" && provided="${BASH_REMATCH[2]}"

cd $SCRIPT_DIR;

echo $provided | planck -k $CACHE_DIR -c $USER_CLASSPATH':'$cp bundle.cljs --user_dir $USER_DIR --live-deps $USER_DIR'/'$1
