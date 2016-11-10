(ns cljs-live.sablono
  (:require [sablono.core :refer-macros [html]]))

(defn html [body]
  (sablono.core/html body))