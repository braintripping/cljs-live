(ns cljs-live.examples
  (:require
    [npm.marked]
    [sablono.core :refer-macros [html]]
    [cljs-live.sablono]
    [cljs-live.compiler :as compiler]))

(enable-console-print!)

(def examples (atom []))

(defn md [s]
  (html [:div {:dangerouslySetInnerHTML {:__html (js/marked s)}}]))

(defn render-examples []
  (html [:div.mw7.center.lh-copy.mb4
         [:.f1.mt5.mb2.tc "cljs-live"
          [:canvas#quil-canvas.v-mid.ml4.br-100]]
         [:p.center.f3.mb3.tc.i "Bundling dependencies for the self-hosted ClojureScript compiler"]
         [:.absolute.top-0.right-0.pa2
          [:a.black.dib.ph3.pv2.bg-near-white.br1 {:href "https://www.github.com/mhuebert/cljs-live"} "Source on GitHub"]]
         (md "Given the following `live-deps.clj` file, we run `script/script/bootstrap.cljs` to generate a cache of dependencies, which is included in this page. In the examples below, our self-hosted ClojureScript compiler reads from this cache while evaluating `(ns..)` and `(require..)` expressions.

**live-deps.clj**
```
{:require        [[npm.marked]
                  [cljsjs.bcrypt]
                  [goog.events]
                  [quil.core :include-macros true]]
 :require-caches [cljs-live.sablono]
 :output-to      \"resources/public/js/compiled/cljs_live_cache.js\"}
```

")
         [:.f2.mt4.mb2.tc "examples"]
         [:p.tc.mb3 "(hit command-enter in a textarea to re-evaluate)"]
         (for [{:keys [render]} @examples]
           (render))]))

(defn render-root []
  (js/ReactDOM.render (render-examples) (js/document.getElementById "app")))

(defn example [label initial-source]
  (let [source (atom initial-source)
        value (atom)
        eval (fn [] (reset! value (try (compiler/eval-str @source)
                                       (catch js/Error e
                                         (.debug js/console e)
                                         (str e)))))
        render (fn [] (html [:.cf.w-100.mb4.mt2
                             [:.bg-light-gray.ph2.mb3.br1.tc
                              (md label)]

                             [:textarea.fl.w-50.pre-wrap.h4
                              {:on-change   #(reset! source (.-value (.-currentTarget %)))
                               :on-key-down #(when (and (= 13 (.-which %)) (.-metaKey %))
                                              (eval))
                               :value       @source}]
                             [:.fl.w-50.pl4
                              (let [{:keys [value error]} @value]
                                (if error (str error)
                                          (if (js/React.isValidElement value)
                                            value
                                            [:.dib.ma2.gray
                                             (if value (str value) "nil")])))]]))]
    (eval)
    (add-watch source :src render-root)
    (add-watch value :val render-root)
    {:render render}))

(compiler/preloads! compiler/c-state)

(swap! examples conj

       (example "**quil**, a ClojureScript library from Clojars with macros and a foreign lib that depends on the browser environment:<br/>(renders next to the page title^^)"
                "(require '[quil.core :as q :include-macros true])
(def colors (atom [(rand-int 255) (rand-int 255) (rand-int 255) (rand-int 255)]))
(def ellipse-args (atom [(rand-int 100) (rand-int 100)]))
(defn rand-or [a b] (if (< (rand) 0.5) a b))
(defn rand-shift [v]  (mapv (rand-or (partial + 5) (partial - 5)) v))
(defn draw []
  (swap! colors rand-shift)
  (swap! ellipse-args rand-shift)
  (apply q/fill @colors)
  (apply q/ellipse (concat '(50 50) @ellipse-args)))
(q/defsketch my-sketch-definition
  :host \"quil-canvas\"
  :draw draw
  :size [100 100])

")

       (example
         "**goog.events**, a Google Closure Library dependency:"
         "(require '[goog.events :as events])\n(events/listenOnce js/window \"mousedown\" #(prn :mouse-down))")

       (example "**bcrypt**, from [cljsjs](http://cljsjs.github.io):"
                "(require '[cljsjs.bcrypt])\n(let [bcrypt js/dcodeIO.bcrypt]\n  (.genSaltSync bcrypt 10))\n")

       (example
         "**npm.marked**, a foreign lib defined in this project's `deps.cljs` file:"
         "(require 'npm.marked)
(js/marked \"**Hello, _world!_**\")")

       (example
         "**cljs-live.sablono**, a compiled namespace for which we've bundled the analysis cache:"
         "(require '[cljs-live.sablono :refer [html]])
(html
  [:div
    [:div {:style {:padding 20 :margin-bottom 10 :color \"black\" :background-color \"pink\"}} \"This is a div rendered with sablono (like hiccup).\"]])"))

(render-root)