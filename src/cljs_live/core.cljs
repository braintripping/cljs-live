(ns cljs-live.core
  (:require
    [sablono.core :refer-macros [html]]
    [cljs-live.user]
    [cljs-live.compiler :as compiler]))

(enable-console-print!)

(def examples (atom []))

(defn render-examples []
  (html [:div
         [:p "Press command-enter to eval"]
         (for [{:keys [render]} @examples]
           (render))]))

(defn main []
  (js/ReactDOM.render (render-examples) (js/document.getElementById "app")))

(declare main)

(defn example [initial-source]
  (let [source (atom initial-source)
        value (atom)
        eval (fn []  (reset! value (try (compiler/eval-str @source)
                                        (catch js/Error e
                                          (.debug js/console e)
                                          (str e)))))
        render (fn [] (html [:div

                             [:textarea {:on-change   #(reset! source (.-value (.-currentTarget %)))
                                         :on-key-down #(when (and (= 13 (.-which %)) (.-metaKey %))
                                                        (eval))
                                         :value       @source
                                         :style       {:width 300 :height 100 :display "inline-block"}}]
                             [:div {:style {:color "#aaa" :display "inline-block" :margin 20}}
                              (let [{:keys [value error]} @value]
                                (if error (str error)
                                          (if (fn? value)
                                            (value)
                                            (if value (str value)
                                                      "nil"))))]]))]
    (eval)
    (add-watch source :src main)
    (add-watch value :val main)
    {:render render}))

(swap! examples conj

       ;; foreign lib copied from npm and defined in deps.cljs
       (example "(require '[npm.marked])\n\n(js/marked \"Hello from markdown\") ")

       ;; foreign lib from cljsjs
       (example "(require '[cljsjs.bcrypt])\n\n(let [bcrypt js/dcodeIO.bcrypt]\n  (.genSaltSync bcrypt 10))\n")

       ;; personal lib, uses macros
       (example "(require '[firelisp.rules :refer-macros [at]])")

       ;; unable to load sablono.compiler in Planck. maybe a macro-loading dependency/order issue.
       #_(example "(require '[sablono.core :refer-macros [html]])")

       ;; unable to load quil in Planck because of browser dependencies.
       #_(example "(require '[quil.core :as q])")

       ;; hiccups
       #_(example "(ns myns
  (:require-macros [hiccups.core :as hiccups :refer [html]])
  (:require [hiccups.runtime :as hiccupsrt]))

(hiccups/defhtml my-template []
  [:div
    [:a {:href \"https://github.com/weavejester/hiccup\"}
      \"Hiccup\"]])")

       )

(compiler/preloads!)

(main)

(prn :pre-loads)