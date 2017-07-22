(ns cljs-live.bundle-util
  (:require [clojure.string :as string]))

(defn macroize-ns [ns]
  (symbol (str ns "$macros")))

(defn demacroize [s]
  (string/replace s #"\$macros$" ""))

(defn demacroize-ns [ns]
  (symbol (demacroize (str ns))))

(defn ns->path
  ([s] (ns->path s ""))
  ([s ext]
   (-> (str s)
       munge
       (string/replace "." "/")
       (str ext))))

(defn macros-ns? [ns]
  (string/ends-with? (str ns) "$macros"))