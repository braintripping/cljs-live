#!/usr/bin/env bash
USER_DIR=$(pwd);
USER_CLASSPATH=$(lein classpath);
SCRIPT_DIR=$(dirname $(python -c "import os,sys; print os.path.realpath(sys.argv[1])" "$0"))'/script/script';
CACHE_DIR=$USER_DIR'/.cljs_live_planck_cache'

mkdir -p $CACHE_DIR;
[[ -e src/deps.cljs ]] && cp src/deps.cljs $SCRIPT_DIR'/'opts.clj;

PROVIDED=$(java -cp $USER_CLASSPATH clojure.main $SCRIPT_DIR'/cljs_deps.clj' --deps $USER_DIR'/'$1)

re="(.*)__BEGIN_CLASSPATH__(.*)__END_CLASSPATH__(.*)"



[[ $PROVIDED =~ $re ]] && cp="${BASH_REMATCH[2]}" && provided="${BASH_REMATCH[3]}"

echo "${BASH_REMATCH[1]}";

cd $SCRIPT_DIR;

echo $provided | planck -k $CACHE_DIR -c $USER_CLASSPATH':'$cp -m script.bundle --user-dir $USER_DIR --deps $USER_DIR'/'$1;

[[ -e opts.clj ]] && rm opts.clj;