(ns cljs-live.sablono
  (:require [sablono.core :include-macros true]))

(defn html [body]
  (sablono.core/html body))