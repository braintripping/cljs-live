(ns cljs-live.core
  (:require
    [re-view.core :as view :include-macros true]
    [re-view.subscriptions :as sub :include-macros true]
    [re-db.d :as d :include-macros true]
    [goog.events]
    [goog.net.XhrIo]
    [cljs-live.compiler :as compiler]))

(enable-console-print!)

(def default-code
  "(ns cljs-live.user (:require [goog.events :as events]) (:import goog.net.XhrIo))
  "
  #_"(ns cljs-live.dev (:require [firelisp.compile :as c]
              [firelisp.rules :as rules :refer [at]]))

(c/compile-expr '(= auth.uid 1))
(str (at \"/\" {:read true}))
")

(defonce _
         (d/transact! [{:id             :cljs-live/state
                        :rules          (str default-code)
                        :rules-compiled ""
                        :rules-evaled   ""}]))

(d/compute! [:cljs-live/state :rules-compiled]
            (compiler/eval-str (d/get :cljs-live/state :rules)))

(def x
  (view/component
    :subscriptions
    {:fire (sub/db [:cljs-live/state])}
    :render
    (fn [this _ {{:keys [rules rules-compiled current-word]} :fire}]
      [:div
       [:textarea {:on-change #(d/transact! [[:db/add :cljs-live/state :rules (.-value (.-currentTarget %))]])
                   ;:on-key-up #(prn :keyup)
                   :value     rules
                   :style     {:width 300 :height 100}}]

       [:div {:style {:color "#aaa"}}

        (let [{:keys [error value]} rules-compiled]
          (if error (str error)
                    (if (fn? value)
                      (value)
                      (str value))))]

       [:div "Current word: " current-word]

       ])))


(defn main []
  (view/render-to-dom (x) "app"))

(main)