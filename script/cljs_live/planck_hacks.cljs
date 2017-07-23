(ns cljs-live.planck-hacks
  (:require [planck.repl :as repl]))


;; Brutal hack to prevent unwanted behaviour due to reloading of :const.
;; see https://dev.clojure.org/jira/browse/CLJS-1854

(def remove-const-from-defs
  (memoize #(reduce-kv (fn [m def-name the-var]
                         (assoc m def-name
                                  (-> the-var
                                      (dissoc :const)
                                      (update :meta dissoc :const)))) {} %)))

(defn remove-const-defs [ana-namespaces]
  (reduce (fn [namespaces ns-name]
            (update-in namespaces [ns-name :defs] remove-const-from-defs)) ana-namespaces (keys ana-namespaces)))

(defn purge-const! [st]
  (update st :cljs.analyzer/namespaces remove-const-defs))

(def ^:dynamic *purging* false)

(add-watch repl/st :purge-const #(when-not *purging*
                                   (binding [*purging* true]
                                     (swap! repl/st purge-const!))))




;; `safe-js-eval` and `with-patched-eval` are means to temporarily catch all errors
;; during Planck's `eval`.
;; (We want to require & compile code that needs a browser environment
;;  and will break here in unforeseeable ways.)

(defn catch-js-eval
  "Patch planck's eval to catch all errors."
  [source source-url]
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
    (set! repl/js-eval catch-js-eval)
    (f)
    (set! repl/js-eval js-eval)))