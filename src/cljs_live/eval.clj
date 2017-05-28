(ns cljs-live.eval)

;; draft / untested
(defmacro defspecial
          "Define a repl-special function. It will receive current compiler-state and compiler-env as first two args."
          [name & body]
          (let [docstring (when (string? (first body)) (first body))
                body (cond->> body docstring (drop 1))]
               `(~'cljs-live.eval/swap-repl-specials! ~'assoc '~name
                  ^{:doc ~docstring} ~(cons 'fn body))))