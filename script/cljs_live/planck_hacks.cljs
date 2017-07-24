(ns cljs-live.planck-hacks
  (:require [planck.repl :as repl]))

;; We are (mis)-using Planck to load and compile namespaces that are designed for
;; browser environments, expecting things that Planck doesn't support.
;;
;; In fact we only load these namespaces for the side-effect of compiling them, so
;; we can catch and ignore all eval errors.
;;
;; `safe-js-eval` and `with-patched-eval` modify the js-eval function used by Planck.

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


(comment

  ;; This was a brutal hack to prevent unwanted behaviour due to reloading of :const.
  ;; related: https://dev.clojure.org/jira/browse/CLJS-1854
  ;;
  ;; It was previously necessary in order to compile reagent macros.
  ;; However, after modifying the script to read from planck's cache,
  ;; we avoid loading reagent macros twice.
  ;;
  ;; It is possible that we may run into a :const-related issue again in the future,
  ;; so keeping this code around just in case.

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
                                       (swap! repl/st purge-const!)))))