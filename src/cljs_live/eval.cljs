(ns cljs-live.eval
  (:require [cljs.js :as cljs]
            [cljs.tools.reader :as r]
            [cljs-live.compiler :as c]
            [cljs.tools.reader.reader-types :as rt]))

(defn read-string [s]
  (when (and s (not= "" s))
    (r/read {} (rt/indexing-push-back-reader s))))

(defonce c-state (cljs/empty-state))
(defonce c-env (atom {}))

(defn compiler-opts
  []
  {:load          c/load-fn
   :eval          cljs/js-eval
   :ns            (:ns @c-env)
   :context       :expr
   :source-map    true
   :def-emits-var true})

(defn eval
  "Eval a single form, keeping track of current ns in c-env."
  [form cstate]
  (let [result (atom)
        ns? (and (seq? form) (#{'ns} (first form)))
        macro-ns? (and (seq? form) (= 'defmacro (first form)))]
    (cljs/eval cstate form (cond-> (compiler-opts)
                                   macro-ns?
                                   (update :ns #(symbol (str % "$macros")))) (partial reset! result))
    (when (and ns? (contains? @result :value))
      (swap! c-env assoc :ns (second form)))
    @result))

(defn eval-str
  "Eval string by first reading all top-level forms, then eval'ing them one at a time."
  ([src] (eval-str src c-state))
  ([src cstate]
   (let [forms (try (read-string (str "[\n" src "]"))
                    (catch js/Error e
                      (set! (.-data e) (clj->js (update (.-data e) :line dec)))
                      {:error e}))]
     (if (contains? forms :error)
       forms
       (loop [forms forms]
         (let [{:keys [error] :as result} (eval (first forms) cstate)
               remaining (rest forms)]
           (if (or error (empty? remaining))
             result
             (recur remaining))))))))