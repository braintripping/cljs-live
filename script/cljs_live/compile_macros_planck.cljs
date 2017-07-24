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
            [clojure.set :as set]
            [cljs-live.planck-hacks :as hacks]))

(def ^:dynamic debug true)

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


(defn cached-resource
  "Loads the content for a given file. Includes planck cache path."
  [file]
  (let [the-resource (first (or (js/PLANCK_READ_FILE file)
                                (js/PLANCK_LOAD file)
                                (js/PLANCK_READ_FILE (str (:cache-path @repl/app-env) "/" (munge file)))))]
    (prn :file (boolean the-resource))
    the-resource
    ))


(defn compile-macro-str [namespace path source]
  (let [res (atom nil)
        _ (cljs.js/compile-str repl/st source path {:macros-ns true
                                                    :ns        namespace} #(reset! res %))
        {:keys [value error]} @res]
    (if error (throw error)
              value)))

(defn get-source
  [namespace]
  (let [cached-source (cached-resource (str (util/ns->path namespace) ".js"))]
    (or cached-source
        (first (for [path (let [ns-path (util/ns->path (util/demacroize-ns namespace))]
                            (cond-> [ns-path]
                                    (string/starts-with? ns-path "cljs/") (conj (string/replace ns-path #"^cljs/" "clojure/"))
                                    (string/starts-with? ns-path "clojure/") (conj (string/replace ns-path #"^clojure/" "cljs/"))))
                     ext (if (util/macros-ns? namespace)
                           ["clj" "cljc"]
                           ["cljs" "cljc"])
                     :let [filepath (str path "." ext)
                           _ (log "get-source at path: " filepath)
                           contents (some-> (io/resource filepath) (slurp))]
                     :when contents]
                 (compile-macro-str namespace filepath contents))))))

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
  (hacks/with-patched-eval
    #(doseq [ns namespaces]
       (try
         (require-a-macro (util/demacroize-ns ns))
         (catch js/Error e nil)))))


(defn transitive-macro-deps
  "Returns dependencies of macro namespaces"
  [exclude-macros entry-macros]
  (require-macro-namespaces entry-macros)
  (let [additional-macros (-> (filter util/macros-ns? (mapcat transitive-deps entry-macros))
                              (set)
                              (set/difference exclude-macros))]
    (if (= entry-macros (into entry-macros additional-macros))
      entry-macros
      (transitive-macro-deps exclude-macros (into entry-macros additional-macros)))))

(defn replace-ext [s ext]
  (as-> s s
        (string/split s #"\.")
        (drop-last s)
        (string/join "." s)
        (str s ext)))

(defn cache-str [ns]
  (-> (get-in @repl/st [:cljs.analyzer/namespaces ns])
      (map->transit)))

(defn warning-handler [warning-type env extra]
  (case warning-type
    :infer-warning nil
    :undeclared-ns (log "Undeclared namespace: " (:ns-sym extra))
    :undeclared-var (log (str "Undeclared var: " (:prefix extra) "." (:suffix extra)))
    (do
      (prn warning-type extra)
      (ana/default-warning-handler warning-type env extra))))

(defn -main
  "Given a list of macro namespaces, return the compiled source for each,
  and list of transitive dependencies for all."
  []
  (binding [ana/*cljs-warning-handlers* [warning-handler]]
    (let [{:keys [entry/macros
                  entry/exclude-macros]} (-> (string/join (line-seq *in*))
                                             (r/read-string))]
      (log "Beginning Planck macro processing...")
      (log "Excluding: " exclude-macros)
      (let [all-deps (transitive-macro-deps exclude-macros macros)
            _ (log "Expanded set of macros:" all-deps)
            _ (log "Building bundle...")
            bundle (reduce (fn [m ns]
                             (let [content (get-source ns)]
                               (when-not content
                                 (throw (js/Error. (str "No source for ns: " ns))))
                               (-> m
                                   (assoc-in [:macro-sources (util/ns->path ns ".js")]
                                             content)
                                   (assoc-in [:macro-caches (util/ns->path ns ".cache.json")]
                                             (cache-str ns)))))
                           {:macro-deps all-deps}
                           all-deps)
            filename (str "/tmp/" (gensym "PLANCK_BUNDLE") (rand-int 9999999999999999))]
        (spit filename (with-out-str (pr bundle)))
        (pr (str "___file:" filename "___"))))))