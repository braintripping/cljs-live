(ns cljs-live.compile-macros-planck
  (:require [clojure.string :as string]
            [planck.repl :as repl]
            [cognitect.transit :as t]
            [planck.core :refer [slurp spit line-seq *in*]]
            [planck.io :as io]
            [cljs.analyzer :as ana]
            [planck.js-deps :as js-deps]
            [cljs.tools.reader :as r]
            [cljs.js :as cljsjs]
            [cljs-live.bundle-util :as util]
            [clojure.set :as set]))

(def ^:dynamic debug false)

(defn log [& args]
  (when debug (apply println args)))

(defn realize-lazy-map [m]
  (reduce (fn [acc k] (assoc acc k (get m k)))
          {} (keys m)))

(def map->transit
  (memoize
    (fn [x]
      (let [w (t/writer :json)]
        (t/write w (realize-lazy-map x))))))

(defn safe-js-eval [source source-url]
  (if source-url
    (let [exception (js/PLANCK_EVAL source source-url)]
      (when exception
        ;; ignore exceptions, we only want build artifacts
        #_(throw exception)))
    (try (js/eval source)
         (catch js/Error e
           ;; ignore exceptions, we only want build artifacts
           nil))))

(defn with-patched-eval
  "Instrument planck's js-eval to ignore exceptions; we are only interested in build artifacts,
  and some runtime errors occur because Planck is not a browser environment."
  [f]
  (let [js-eval repl/js-eval]
    (set! repl/js-eval safe-js-eval)
    (f)
    (set! repl/js-eval js-eval)))



(defn compile-macro-str [namespace path source]
  (let [res (atom nil)
        _ (cljs.js/compile-str repl/st source path {:macros-ns true
                                                    :ns        namespace} #(reset! res %))]
    @res))

(defn get-macro
  [namespace]
  (let [path (string/replace (util/ns->path namespace) "$macros" "")
        cljs-clojure-variants (cond (string/starts-with? path "cljs/") [path (string/replace path #"^cljs/" "clojure/")]
                                    (string/starts-with? path "clojure/") [path (string/replace path #"^clojure/" "cljs/")]
                                    :else [path])]
    (first (for [path cljs-clojure-variants
                 [$macros? ext] [[true "clj"]
                                 [true "cljc"]
                                 [false "clj"]
                                 [false "cljc"]]
                 :let [full-path (str path (when $macros? "$macros") "." ext)
                       _ (log "get-macro at path: " full-path)
                       contents (some-> (io/resource full-path) (slurp))]
                 :when contents
                 :let [{:keys [value error]} (compile-macro-str namespace full-path contents)]]
             (if error (throw error)
                       [full-path value])))))



(def get-deps
  (memoize (fn [ana-ns]
             (->> (select-keys ana-ns [:requires :imports])
                  vals
                  (keep identity)
                  (map vals)
                  (apply concat)
                  (concat (->> ana-ns :require-macros vals (map util/macroize-ns)))))))


(def js-index js-deps/foreign-libs-index)
;; modified, from Planck

(def cache-map
  (memoize (fn [namespace]
             (get-in @repl/st [:cljs.analyzer/namespaces namespace]))))

(defn transitive-deps
  "Given a dep symbol to load, returns a topologically sorted sequence of deps to load, in load order."
  ([dep-name] (transitive-deps dep-name #{}))
  ([dep-name found]
   (let [new-deps (filter (complement found) (get-deps (cache-map dep-name)))
         found (into found new-deps)]
     (cond-> found
             (seq new-deps) (into (mapcat #(transitive-deps % found) new-deps))))))

(def require-a-macro
  (memoize (fn [ns-name]
             (let [the-form `(require-macros (quote ~ns-name))]
               (log "Eval form: " the-form)
               (cljs.js/eval repl/st the-form {} #(when-let [e (:error %)] (println e)))))))

(defn require-macro-namespaces [namespaces]
  (with-patched-eval
    #(doseq [ns namespaces]
       (try
         (require-a-macro (util/demacroize-ns ns))
         (catch js/Error e nil)))))


(defn transitive-macro-deps
  "Returns dependencies of macro namespaces"
  [entry-macros]
  (let [entry-macros (set entry-macros)]
    (require-macro-namespaces entry-macros)
    (let [additional-macros (seq (filter util/macros-ns? (mapcat transitive-deps entry-macros)))]
      (if (= entry-macros (into entry-macros additional-macros))
        entry-macros
        (transitive-macro-deps (into entry-macros additional-macros))))))

(defn replace-ext [s ext]
  (as-> s s
        (string/split s #"\.")
        (drop-last s)
        (string/join "." s)
        (str s ext)))

(defn cache-str [ns]
  (-> (get-in @repl/st [:cljs.analyzer/namespaces ns])
      (map->transit)))

(defn -main
  "Given a list of macro namespaces, return the compiled source for each,
  and list of transitive dependencies for all."
  []
  (binding [ana/*cljs-warning-handlers* [(fn [warning-type env extra]
                                           (when-not (= warning-type :infer-warning)
                                             (ana/default-warning-handler warning-type env extra)))]]
    (let [{:keys [get-macros
                  exclude-macros]}
          #_{:get-macros     '#{re-db.core$macros cljs.js$macros cljs-live.eval$macros re-view.core$macros re-db.d$macros maria.user$macros re-db.patterns$macros}
             :exclude-macros '#{cljs.core$macros cljs.js$macros}}
          (-> (string/join (line-seq *in*))
              (r/read-string))]
      (log "Expanding macros...")
      (let [all-macros (doall (-> (transitive-macro-deps get-macros)
                                  (set)
                                  (set/difference exclude-macros)))
            _ (log "Building bundle...")
            bundle (reduce (fn [m ns]
                             (let [[full-path content] (get-macro ns)]
                               (-> m
                                   (assoc-in [:macro-sources (replace-ext full-path "$macros.js")]
                                             content)
                                   (assoc-in [:macro-caches (replace-ext full-path "$macros.cache.json")]
                                             (cache-str ns)))))
                           {:macro-deps all-macros} all-macros)
            filename (str "/tmp/" (gensym "PLANCK_BUNDLE") (rand-int 9999999999999999))]
        (spit filename (with-out-str (pr bundle)))
        (pr (str "___file:" filename "___"))))))