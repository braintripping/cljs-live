#!/usr/bin/env planck

(ns script.goog-deps
  (:require [planck.core :refer [*command-line-args* slurp]]
            [planck.shell :as shell :refer [sh]]
            [planck.io :as io]
            [planck.repl :as repl]
            [clojure.string :as string]
            [script.io :refer [resource]]))

(defn goog? [namespace]
  (= "goog" (-> namespace
                str
                (string/split ".")
                first)))

(defn path [namespace]
  (get (repl/closure-index-mem) namespace))

(defn goog-src [namespace]
  (if (#{} namespace)
    ""
    (when-let [path (path namespace)]
      (resource (str path ".js")))))

(defn compile [& flags]
  (let [args (concat ["java" "-jar" "node_modules/google-closure-compiler/compiler.jar"]
                     (->> (flatten flags)
                          (partition 2)
                          (map (partial string/join "="))))]
    (apply sh args)))

(def closure-lib-dir "node_modules/google-closure-library/closure/")

(defn tempname [] (str "script/" (munge (gensym)) ".txt"))

(defn goog-dep-files
  "Returns the list of files included in a compile of the supplied Closure Library dependencies"
  [& goog-deps]
  (let [temp-manifest (tempname)
        temp-out (tempname)]
    (compile
      "--compilation_level" "WHITESPACE_ONLY"                 ;; WHITESPACE_ONLY | SIMPLE | ADVANCED
      "--js" (str closure-lib-dir "**.js")
      "--warning_level" "VERBOSE"                             ;; QUIET | DEFAULT | VERBOSE
      "--formatting" "PRETTY_PRINT"                           ;; PRETTY_PRINT, PRINT_INPUT_DELIMITER, SINGLE_QUOTES
      "--dependency_mode" "STRICT"                            ;; NONE | LOOSE | STRICT
      "--output_manifest" temp-manifest
      "--js_output_file" temp-out
      (for [dep goog-deps]
        ["--entry_point" dep]))
    (let [deps (-> (slurp temp-manifest)
                   (string/replace closure-lib-dir "")
                   (string/split "\n"))]
      (doseq [t [temp-out temp-manifest]]
        (io/delete-file t))
      ;; ignore goog/base because cljs environments always have this already
      (filter (partial not= "goog/base.js") deps))))