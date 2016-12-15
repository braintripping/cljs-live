(ns cljs-live.eval
  (:require [cljs.js :as cljs]
            [cljs.tools.reader :as r]
            [cljs.analyzer :refer [*cljs-warning-handlers*]]
            [cljs-live.compiler :as c]
            [cljs.tools.reader.reader-types :as rt]
            [clojure.string :as string]))

(def ^:dynamic *cljs-warnings* nil)

(defonce c-state (cljs/empty-state))
(defonce c-env (atom {:ns (symbol "cljs.user")}))
(defn c-opts
  [c-state env]
  {:load          (partial c/load-fn c-state)
   :eval          cljs/js-eval
   :ns            (:ns @env)
   :context       :expr
   :source-map    true
   :def-emits-var true})

(defn read-string-indexed
  "Read string using indexing-push-back-reader, for errors with location information."
  [s]
  (when (and s (not= "" s))
    (r/read {} (rt/indexing-push-back-reader s))))

(defn get-ns [ns c-state] (get-in @c-state [:cljs.analyzer/namespaces ns]))

(defn resolve-symbol [c-state sym]
  (binding [cljs.env/*compiler* c-state]
    (:name (cljs.analyzer/resolve-var (assoc @cljs.env/*compiler* :ns (or (get-ns c-state (:ns @c-env)) 'cljs.user)) sym))))

(def ^:dynamic *repl-special*
  {'in-ns (fn [n]
            (when-not (symbol? n)
              (throw (js/Error. "`in-ns` must be passed a symbol.")))
            (swap! c-env assoc :ns n)
            {:value nil
             :ns    n})})

(defn repl-special [op & args]
  (when-let [f (get *repl-special* op)]
    (try (apply f args)
         (catch js/Error e {:error e}))))

(declare eval)

(defn ensure-macro-ns [sym]
  (if
    (string/ends-with? (name sym) "$macros")
    sym
    (symbol (namespace sym) (str (name sym) "$macros"))))

(defn eval
  "Eval a single form, keeping track of current ns in c-env"
  ([form] (eval form c-state c-env))
  ([form c-state c-env]
   (or (and (seq? form) (apply repl-special form))
       (let [result (atom)
             ns? (and (seq? form) (#{'ns} (first form)))
             macros-ns? (and (seq? form) (= 'defmacro (first form)))
             opts (cond-> (c-opts c-state c-env)
                          macros-ns?
                          (merge {:macros-ns true
                                  :ns        (ensure-macro-ns (:ns @c-env))}))]
         (binding [*cljs-warning-handlers* [(fn [warning-type env extra]
                                              (some-> *cljs-warnings*
                                                      (swap! conj {:type        warning-type
                                                                   :env         env
                                                                   :extra       extra
                                                                   :source-form form})))]
                   r/*data-readers* (conj r/*data-readers* {'js identity})]
           (try (cljs/eval c-state form opts (partial swap! result merge))
                (when (and macros-ns? (not= (:ns opts) (:ns @c-env)))
                  (eval `(require-macros '[~(:ns @c-env) :refer [~(second form)]])))
                (catch js/Error e
                  (.error js/console (or (.-cause e) e))
                  (swap! result assoc :error e))))
         (when (and ns? (contains? @result :value))
           (swap! c-env assoc :ns (second form)))
         @result))))

(defn wrap-source
  "Clojure reader only returns the last top-level form in a string,
  so we wrap user source strings."
  [src]
  (str "[\n" src "\n]"))

(defn read-src
  "Read src using default tools.reader. If an error is encountered,
  re-read an unwrapped version of src using indexed reader to return
  a correct error location."
  [src c-state]
  (binding [r/resolve-symbol (partial resolve-symbol c-state)
            r/*data-readers* (conj r/*data-readers* {'js identity})]
    (try (r/read-string (wrap-source src))
         (catch js/Error e1
           (try (read-string-indexed src)
                ;; if no error thrown by indexed reader, return original error
                {:error e1}
                (catch js/Error e2
                  {:error e2}))))))

(defn eval-str
  "Eval string by first reading all top-level forms, then eval'ing them one at a time."
  ([src] (eval-str src c-state c-env))
  ([src c-state c-env]
   (binding [*cljs-warnings* (atom [])]
     (let [{:keys [error] :as result} (read-src src c-state)]
       (if error result
                 (loop [forms result]
                   (let [{:keys [error] :as result} (eval (first forms) c-state c-env)
                         remaining (rest forms)]
                     (if (or error (empty? remaining))
                       (assoc result :warnings @*cljs-warnings*)
                       (recur remaining)))))))))