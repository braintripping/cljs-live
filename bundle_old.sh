
##!/usr/bin/env bash
#
#USER_DIR=$(pwd);
#USER_CLASSPATH=$(lein classpath);
#SCRIPT_DIR=$(dirname $(python -c "import os,sys; print os.path.realpath(sys.argv[1])" "$0"))'/script/script';
#CACHE_DIR=$USER_DIR'/.cljs_live_planck_cache'
#
#mkdir -p $CACHE_DIR;
#
##java -cp $USER_CLASSPATH clojure.main $SCRIPT_DIR'/cljs_deps.clj' --deps $USER_DIR'/'$1
#
#DEPS=$(java -cp $USER_CLASSPATH clojure.main $SCRIPT_DIR'/cljs_deps.clj' --deps $USER_DIR'/'$1)
#re="(.*)__END_CLASSPATH__(.*)"
#[[ $DEPS =~ $re ]] && cp="${BASH_REMATCH[1]}" && deps="${BASH_REMATCH[2]}"
#
#cd $SCRIPT_DIR;
#
#echo $deps | planck -k $CACHE_DIR -c $USER_CLASSPATH':'$cp bundle.cljs --user_dir $USER_DIR