# CLJS-Live


Until 2015, ClojureScript required Java to compile, and therefore only _compiled_ apps could run in a web browser -- we had no `eval` :(. But that changed with the release of the self-hosted compiler :). Today, thanks to tools available in the `cljs.js` namespace, `eval` is easy.

But what is not easy is _dependency management_ in this new environment. In traditional Clojure, the compiler knows how to search the current `classpath` to find source files. But what happens in a browser, where there is no classpath? How do we make dependencies available in this new environment?

This is where `cljs-live` steps in. There are two main parts.

### Part I: create dependency bundles.

Given a file containing dependency information (see the example `live-deps.clj` file), here is our task:

1. Compile all ClojureScript files in provided :source-paths
2. Based on the transitive dependencies of the `:entry` and ``:provided` namespaces you specify,
   figure out what to include in the bundle:
   - analysis caches for all :entry namespaces (transit-encoded)
   - precompiled javascript files for macros and other non-:provided namespaces
   - a list of all `:provided` namespaces for each bundle.
3. Emit a single JSON object with all of the above info
4. Copy the original source files for every :entry and :provided namespace into another directory (useful for lookups later)

### Part II: consume dependency bundles in the browser.

The second part of `cljs-live` is a small library of functions for use _in the browser_.

There are two important functions in `cljs-live.compiler` that probably everyone using this tool would use:

* `load-bundles!` downloads precompiled bundles created by the bundle script, and merges them into a single local cache (kind of like a fake 'classpath').
* `load-fn` can then be passed as the :load function to `cljs.js` eval/compile functions, and will correctly resolve namespaces to the precompiled sources in the bundle cache.

There are also functions in `cljs-live.eval` that make it easy to manage the eval/compile process:

* `eval-str` evaluates all of the forms in a given string and returns the last result
* `eval` evaluates a single form

These functions do the same thing as what you find in `cljs.js`, but with some extras:

- Source mapping: the default `cljs.js` does not expose source maps from `eval`, so you have to perform an intermediate `compile` to get this information. We give this to you in one step.
- Error positions: when an error is encountered, we use source-maps to find its position in the original source file, which was also emitted (separately) from the bundler.
- Namespace tracking: the current namespace is stored in a compiler-env atom; the `ns` and `in-ns` repl-special functions update this atom whenever the user changes the namespace

Specifically, when you call `eval` and `eval-str` you get back a map which includes:

  :value or :error - depending on the result of evaluation
  :error-position  - the 0-indexed position of the error, if present
  :compiled-js     - the javascript source emitted by the compiler
  :source          - the source code string that was evaluated
  :source-map      - the base64-encoded source-map string
  :env             - the compile environment, a map containing :ns (current namespace)

### Example

;; TODO: update this example
https://cljs-live.firebaseapp.com

### Requirements:

- [Planck REPL](planck-repl.org)
- Clojure

### Warning

This code depends on implementation details of specific versions of ClojureScript and Planck and is still quite brittle.


### Usage

- In your project directory, create a `live-deps.clj` file. See the [example file](https://github.com/mhuebert/cljs-live/blob/master/live-deps.clj) for options.
- Run `cljs-live.bundle/build`, passing it a path to a `live-deps` file.

```clj
{:bundle-out     "resources/public/js/cljs_live"     ;; where to put bundle files + the sources directory
 :cljsbuild-out  "resources/public/js/compiled/out"} ;; an `output-dir` for the cljs-live compile step
 :source-paths   ["src"]
 :bundles        [{:entry           #{my-app.user}           ;; tells cljs-live which namespaces you want to use with the self-hosted compiler
                   :entry/no-follow #{my-app.some-namespace} ;; tells cljs-live *not* to follow+include the dependencies of these namespaces
                                                             ;;   (because they are not self-host-compatible, or you are trying to shrink file size0
                   :provided        #{my-app.core}           ;; tells cljs-live that these namespaces are already provided by the compiled app
                                                             ;;   (must use :optimizations :simple)
}]
```


## Modifying the bundle

If you aren't happy with the calculated dependencies, you can manually require or exclude specific namespaces from a bundle by using the following keys:

```clj
{:require-source      []
 :require-cache       []
 :require-goog        []
 :require-foreign-lib []

 :exclude-source      []
 :exclude-cache       []
 :exclude-goog        []
 :exclude-foreign-lib []}
```

The `cljs-live/compiler` namespace contains a `load-fn` that knows how to read from the resulting bundle.

### Notes

- Not all macros work in the self-host environment. Mike Fikes, creator of [Planck,](planck-repl.org) is an expert on the topic, so check out his blog! Eg: [ClojureScript Macro Tower and Loop](http://blog.fikesfarm.com/posts/2015-12-18-clojurescript-macro-tower-and-loop.html), [Collapsing Macro Tower](http://blog.fikesfarm.com/posts/2016-03-04-collapsing-macro-tower.html)
