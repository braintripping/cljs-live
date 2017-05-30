(ns cljs-live.eval
  (:require [cljs.js :as cljs]
            [cljs.tools.reader :as r]
            [cljs.analyzer :refer [*cljs-warning-handlers*]]
            [cljs-live.compiler :as c]
            [cljs.repl :refer [print-doc]]
            [cljs.tools.reader.reader-types :as rt]
            [clojure.string :as string]
            [goog.crypt.base64 :as base64]
            [cljs.source-map :as sm])
  (:require-macros [cljs-live.eval :refer [defspecial]]))

(def ^:dynamic *cljs-warnings* nil)

(defonce c-state (cljs/empty-state))
(defonce c-env (atom {:ns (symbol "cljs.user")}))

(def repl-specials {}
  #_{'ns       wrap-ns
     'in-ns    in-ns
     'with-ns  with-ns
     'doc      doc
     'defmacro wrap-defmacro})

(defn swap-repl-specials!
  "Mutate repl specials available to the eval fns in this namespace."
  [f & args]
  (set! repl-specials (apply f repl-specials args)))

(defn c-opts
  [c-state env]
  {:load          (partial c/load-fn c-state)
   :eval          cljs/js-eval
   :ns            (:ns @env)
   :context       :expr
   :source-map    true
   :def-emits-var true})

(defn get-ns [c-state ns] (get-in @c-state [:cljs.analyzer/namespaces ns]))

(defn toggle-macros-ns [sym]
  (let [s (str sym)]
    (symbol
      (if (string/ends-with? s "$macros")
        (string/replace s "$macros" "")
        (str s "$macros")))))

(defn resolve-var
  ([sym] (resolve-var c-state c-env sym))
  ([c-state c-env sym]
   (binding [cljs.env/*compiler* c-state]
     (cljs.analyzer/resolve-var (assoc @cljs.env/*compiler* :ns (get-ns c-state (or (:ns @c-env) 'cljs.user))) sym))))

(defn resolve-symbol
  ([sym] (resolve-symbol c-state c-env sym))
  ([c-state c-env sym]
   (:name (resolve-var c-state c-env sym))))

(declare eval eval-forms)

(defn ensure-ns!
  "Create namespace if it doesn't exist"
  [c-state c-env ns]
  (when-not (contains? (get @c-state :cljs.analyzer/namespaces) ns)
    (eval c-state c-env `(~'ns ~ns))))

(defn ->macro-sym [sym]
  (let [ns? (namespace sym)
        ns (or ns? (name sym))
        name (if ns? (name ns) nil)]
    (if (string/ends-with? ns "$macros")
      ns
      (if ns? (symbol (str ns "$macros") name)
              (symbol (str ns "$macros"))))))

(defspecial in-ns
  "Switch to namespace"
  [c-state c-env namespace]
  (when-not (symbol? namespace) (throw (js/Error. "`in-ns` must be passed a symbol.")))
  (if (contains? (get @c-state :cljs.analyzer/namespaces) namespace)
    {:ns namespace}
    (eval c-state c-env `(~'ns ~namespace))))

(defspecial ns
  "Wraps `ns` to return :ns in result map"
  [c-state c-env & body]
  (-> (eval c-state c-env (with-meta (cons 'ns body) {::skip-repl-special true}))
      (assoc :ns (first body))))

(defspecial doc
  "Show doc for symbol"
  [c-state c-env name]
  (let [[namespace name] (let [name (resolve-symbol name)]
                           (map symbol [(namespace name) (clojure.core/name name)]))]
    {:value (with-out-str
              (some-> (get-in @c-state [:cljs.analyzer/namespaces namespace :defs name])
                      (select-keys [:name :doc :arglists])
                      print-doc)
              "Not found")}))

(defn repl-special [c-state c-env body]
  (let [f (get repl-specials (first body))]
    (try (f c-state c-env body)
         (catch js/Error e
           (prn "repl-special error" e)
           {:error e}))))

(defn dec-pos
  "Position information from the ClojureScript reader is 1-indexed - decrement line and column."
  [{:keys [line column] :as pos}]
  (assoc pos
    :line (dec line)
    :column (dec column)))

(defn relative-pos [{target-line   :line
                     target-column :column
                     :as           target}
                    {start-line :line
                     start-col  :column}]
  (if-not start-line
    target
    (cond-> (update target :line + start-line)
            (= target-line start-line) (update :column + start-col))))

(defn warning-handler
  "Collect warnings in a dynamic var"
  [form source warning-type env extra]
  (some-> *cljs-warnings*
          (swap! conj {:type   warning-type
                       :env    (relative-pos (dec-pos env) (when (satisfies? IMeta form) (some-> (meta form)
                                                                                                 (dec-pos))))
                       :extra  extra
                       :source source
                       :form   form})))

(defn cljs-location [error source-map]
  (let [[line column] (->> (re-find #"<anonymous>:(\d+)(?::(\d+))" (.-stack error))
                           (rest)
                           (map js/parseInt))
        source-map (-> (base64/decodeString source-map)
                       (js/JSON.parse)
                       (sm/decode))
        {:keys [line col]} (-> (get source-map (dec line))
                               (subseq <= column)
                               (last)
                               (second)
                               (last))]
    {:line   line
     :column col}))

(defn eval
  "Eval a single form, keeping track of current ns in c-env"
  ([form] (eval c-state c-env form))
  ([c-state c-env form] (eval c-state c-env form {}))
  ([c-state c-env form opts]
   (let [repl-special? (and (seq? form)
                            (contains? repl-specials (first form))
                            (not (::skip-repl-special (meta form))))
         opts (merge (c-opts c-state c-env) opts)
         {:keys [source] :as start-pos} (when (satisfies? IMeta form) (some-> (meta form) (dec-pos)))
         {:keys [ns] :as result} (if repl-special?
                                   (repl-special c-state c-env form)
                                   (let [result (atom)
                                         cb (partial swap! result merge)]
                                     (binding [*cljs-warning-handlers* [(partial warning-handler form source)]
                                               r/*data-readers* (conj r/*data-readers* {'js identity})]
                                       (if source
                                         (cljs/compile-str c-state source "user_cljs" opts
                                                           (fn [{error       :error
                                                                 compiled-js :value}]
                                                             (let [[js-source source-map] (clojure.string/split compiled-js #"\n//#\ssourceURL[^;]+;base64,")]
                                                               (->> (if error
                                                                      {:error          error
                                                                       :error-location (some-> (ex-cause error)
                                                                                               (ex-data)
                                                                                               (select-keys [:line :column])
                                                                                               (dec-pos)
                                                                                               (relative-pos start-pos))}
                                                                      (-> (try {:value (js/eval js-source)}
                                                                               (catch js/Error e {:error          e
                                                                                                  :error-location (-> (cljs-location e source-map)
                                                                                                                      (relative-pos start-pos))}))
                                                                          (merge {:compiled-js js-source
                                                                                  :source-map  source-map})))
                                                                    (reset! result)))))
                                         (cljs/eval c-state form opts cb)))
                                     @result))]
     (when (and (some? ns) (not= ns (:ns @c-env)))
       (swap! c-env assoc :ns ns))
     result)))

(defn read-string-indexed
  "Read string using indexing-push-back-reader, for errors with location information."
  [s]
  (when (and s (not= "" s))
    (let [reader (rt/source-logging-push-back-reader s)]
      (loop [forms []]
        (let [form (r/read {:eof ::eof} reader)]
          (if (= form ::eof)
            forms
            (recur (conj forms form))))))))

(defn read-src
  "Read src using indexed reader."
  [c-state c-env src]
  (binding [r/resolve-symbol #(resolve-symbol c-state c-env %)
            r/*data-readers* (conj r/*data-readers* {'js identity})]
    (try {:value (read-string-indexed src)}
         (catch js/Error e
           {:error e}))))

(defn eval-forms
  "Eval a list of forms"
  [c-state c-env forms]
  (binding [*cljs-warnings* (or *cljs-warnings* (atom []))]
    (loop [forms forms]
      (let [{:keys [error] :as result} (eval c-state c-env (first forms))
            remaining (rest forms)]
        (if (or error (empty? remaining))
          (assoc result :warnings @*cljs-warnings*)
          (recur remaining))))))

(defn eval-str
  "Eval string by first reading all top-level forms, then eval'ing them one at a time."
  ([src] (eval-str c-state c-env src))
  ([c-state c-env src]
   (let [{:keys [error value] :as result} (read-src c-state c-env src)]
     (if error
       result
       (eval-forms c-state c-env value)))))