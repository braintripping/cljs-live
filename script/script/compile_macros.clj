#!/bin/sh
":"                                                         ; exec /usr/bin/env java -cp ~/cljs.jar:src:`lein classpath` clojure.main script/script/compile_macros.clj "$@";

(ns script.compile-macros
  (:require [cljs.build.api :as build]
            [cljs.closure :as closure]
            [clojure.java.io :as io]
            [clojure.string :as string]))

;; for macros that can't be compiled

(def target "script/script/out")

(defn ns->path
  ([s] (ns->path s ""))
  ([s ext]
   (-> (str s)
       munge
       (string/replace "." "/")
       (str ext))))

(defn get-source [namespace]
  (some-> (first (for [ext [".clj" ".cljc"]]
                   (io/resource (ns->path namespace ext))))
          (closure/jar-file-to-disk target)))

(prn (->> (map get-source *command-line-args*)
          (apply build/inputs)
          (build/compile {})))


