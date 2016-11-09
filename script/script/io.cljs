(ns script.io
  (:require [planck.repl :as repl]
            [cognitect.transit :as t]))

(def out-path "resources/public/js/compiled/out")

(defn resource
  "Loads the content for a given file. Includes planck cache path."
  [file]
  (first (or (js/PLANCK_READ_FILE file)
             (js/PLANCK_LOAD file)
             (js/PLANCK_READ_FILE (str (:cache-path @repl/app-env) "/" (munge file)))
             (js/PLANCK_READ_FILE (str out-path "/" file)))))

(defn realize-lazy-map [m]
  (reduce (fn [acc k] (assoc acc k (get m k)))
          {} (keys m)))

(defn ->transit [x]
  (let [w (t/writer :json)]
    (t/write w (realize-lazy-map x))))

(defn transit->clj [x]
  (let [r (t/reader :json)]
    (t/read r x)))