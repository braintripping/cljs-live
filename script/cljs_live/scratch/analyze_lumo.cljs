(ns cljs-live.scratch.analyze-lumo
  (:require [cljs.env :as env]
            [cljs.analyzer :as ana]
            [lumo.repl :as repl]
            [lumo.io :as io]
            [lumo.util :as util :refer [line-seq ns->relpath]]
            [lumo.analyzer :as lumo-ana :refer [gen-user-ns forms-seq*]]
            [cljs.tools.reader :as reader]
            [clojure.set :as set]
            [clojure.string :as string])
  (:require-macros
    [cljs.env.macros :refer [ensure]]
    [cljs.analyzer.macros
     :refer [no-warn wrapping-errors
             disallowing-recur allowing-redef disallowing-ns*]]))

(def ^:dynamic *seen* nil)

(defn macroize-ns [ns]
  (symbol (str ns "$macros")))

(defn macroize-dep-map [m]
  (reduce-kv (fn [m k v]
               (assoc m (macroize-ns k) (macroize-ns v))) {} m))

(defn parse-ns
  "Helper for parsing only the essential namespace information from a
   ClojureScript source file and returning a cljs.closure/IJavaScript compatible
   map _not_ a namespace AST node.
   By default does not load macros or perform any analysis of dependencies. If
   opts parameter provided :analyze-deps and :load-macros keys their values will
   be used for *analyze-deps* and *load-macros* bindings respectively. This
   function does _not_ side-effect the ambient compilation environment unless
   requested via opts where :restore is false."
  [no-follow ns]
  (cond (not *seen*)
        (binding [*seen* (atom (set (keys (:cljs.analyzer/namespaces @repl/st))))]
          (parse-ns no-follow ns))
        (@*seen* ns) nil
        :else (ensure
                (let [_ (assert (symbol? ns))
                      _ (swap! *seen* conj ns)
                      opts {:analyze-deps true
                            :load-macros  true
                            :restore      false}
                      src (util/ns->source ns)
                      next-deps (:requires (binding [env/*compiler* repl/st
                                                     ana/*cljs-ns* 'cljs.user
                                                     ana/*cljs-file* src
                                                     ana/*macro-infer* true
                                                     ana/*analyze-deps* (:analyze-deps opts)
                                                     ana/*load-macros* (:load-macros opts)]
                                             (let [rdr (when-not (sequential? src) src)]
                                               (try
                                                 (loop [forms (if rdr
                                                                (forms-seq* rdr (util/get-absolute-path src))
                                                                src)
                                                        ret (merge
                                                              {:source-file  (when rdr src)
                                                               :source-forms (when-not rdr src)
                                                               :requires     (cond-> #{'cljs.core}
                                                                                     (get-in @env/*compiler* [:options :emit-constants])
                                                                                     (conj ana/constants-ns-sym))})]
                                                   (if (seq forms)
                                                     (let [env (ana/empty-env)
                                                           ast (no-warn (ana/analyze env (first forms) nil))]
                                                       (cond
                                                         (= :ns (:op ast))
                                                         (let [ns-name (:name ast)
                                                               ns-name (if (and (= 'cljs.core ns-name)
                                                                                (= "cljc" (util/ext src)))
                                                                         'cljs.core$macros
                                                                         ns-name)
                                                               deps (merge (:uses ast)
                                                                           (:requires ast)
                                                                           (macroize-dep-map (:use-macros ast))
                                                                           (macroize-dep-map (:require-macros ast)))]
                                                           (merge
                                                             {:ns           (or ns-name 'cljs.user)
                                                              :provides     [ns-name]
                                                              :requires     (if (= 'cljs.core ns-name)
                                                                              (set (vals deps))
                                                                              (cond-> (conj (set (vals deps)) 'cljs.core)
                                                                                      (get-in @env/*compiler* [:options :emit-constants])
                                                                                      (conj ana/constants-ns-sym)))
                                                              :source-file  (when rdr src)
                                                              :source-forms (when-not rdr src)
                                                              :ast          ast
                                                              :macros-ns    (string/ends-with? (str ns-name) "$macros")}))

                                                         (= :ns* (:op ast))
                                                         (let [deps (merge (:uses ast)
                                                                           (:requires ast)
                                                                           (macroize-dep-map (:use-macros ast))
                                                                           (macroize-dep-map (:require-macros ast)))]
                                                           (recur (rest forms)
                                                                  (cond-> (update-in ret [:requires] into (set (vals deps)))
                                                                          ;; we need to defer generating the user namespace
                                                                          ;; until we actually need or it will break when
                                                                          ;; `src` is a sequence of forms - António Monteiro
                                                                          (not (:ns ret))
                                                                          (assoc :ns (gen-user-ns src) :provides [(gen-user-ns src)])))
                                                           )

                                                         :else ret))
                                                     ret))))))]
                  (when-not (no-follow ns)
                    (when-let [fresh-deps (set/difference next-deps @*seen*)]
                      (mapv (partial parse-ns no-follow) fresh-deps)))
                  @*seen*))))