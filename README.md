# CLJS-Live


Until 2015, ClojureScript required Java to compile, and therefore only _compiled_ apps could run in a web browser -- we had no `eval` :(. But that changed with the release of the self-hosted compiler :). Today, thanks to tools available to all in the `cljs.js` namespace, `eval` is easy.

But what is not easy is _dependency management_ in this new environment. In traditional Clojure, the compiler knows how to search the current `classpath` to find source files. But what happens in a browser, where there is no classpath? How do we make dependencies available in this new environment?

This is where `cljs-live` steps in.

`cljs-live` consists of two main parts. First, there is a bundler script (`bundle.sh`). Given the file containing dependency information (see the example `live-deps.clj` file), the bundle script does the following:

 1. Calculates all of the dependent namespaces, transitively
 2. Finds source files for all of these dependencies
 3. Precompiles the source files into JavaScript
 4. Emits a file containing a JSON object with all the precompiled javascript, as well as analysis caches for these files (you can't use precompiled CLJS with the self-hosted compiler without including these caches!)
 5. Copies all of the original source files into the same directory (useful for source lookups, later)

The second part of `cljs-live` is a small library of functions for use _in the browser_. These include two functions in `cljs-live.compiler`:

* `load-bundles!` downloads precompiled bundles created by the bundle script, and merges them into a single local cache (kind of like a fake 'classpath').
* `load-fn` can then be passed as the :load function to `cljs.js` eval/compile functions, and will correctly resolve namespaces to the precompiled sources in the bundle cache.

That's all you need, if you want to retain full control over the eval/compile process. But if you want a smoother experience, we also provide some eval-related functions in `cljs-live.eval`:

* `eval-str` evaluates all of the forms in a given string and returns the last result
* `eval` evaluates a single form

The `eval-str` and `eval` in `cljs-live` have some additional functionality over what you find in `cljs.js`:

- Source mapping: the default `cljs.js` does not expose source maps from `eval`, so you have to perform an intermediate `compile` to get this information. We give this to you in one step.
- Error positions: when an error is encountered, we use source-maps to find its position in the original source file, which was also emitted (separately) from the bundler.
- Namespace tracking: the current namespace is stored in a compiler-env atom; the `ns` and `in-ns` repl-special functions update this atom whenever the user changes the namespace

Specifically, when you call `eval` and `eval-str` you get back a map which includes:

  :value or :error - depending on the result of evaluation
  :error-location  - the 0-indexed position of the error, if present
  :compiled-js     - the javascript source emitted by the compiler
  :source          - the source code string that was evaluated
  :source-map      - the base64-encoded source-map string
  :env             - the compile environment, a map containing :ns (current namespace)

For real-world usage, this is valuable information to keep on hand.


### Example

https://cljs-live.firebaseapp.com

### Requirements:

- [Planck REPL](planck-repl.org)
- Clojure
- Alembic (in `~/.lein/profiles.clj`)


### Warning

This code depends on implementation details of specific versions of ClojureScript and Planck and is not for the faint of heart.

### Goal

Given a map containing `ns`-style requirement expressions (`:require, :require-macros, :import`), and the entry namespace of the compiled ClojureScript app (`:provided`), **cljs-live** should calculate and bundle the minimal set of compiled source files and analysis caches, and provide a utility to read these bundles into the compiler state.

### Usage

- Put a symlink to bundle.sh on your path.
- In your project directory, create a `live-deps.clj` file. See the [example file](https://github.com/mhuebert/cljs-live/blob/master/live-deps.clj) for options.

```clj
{:output-dir     "resources/public/js/cljs_live_cache.js" ;; where to save the output file
 :cljsbuild-out  "resources/public/js/compiled/out"} ;; the `output-dir` of your cljsbuild options
 :source-paths   ["src"]
 :bundles        [{;; same behaviour as `ns` forms:
                   :require        [app.repl-user :include-macros true]
                   :require-macros [] ;; same as above
                   :import         [] ;; same as above

                   ;; other keys:
                   :provided       [app.core] ;; entry namespace(s) to the _compiled_ app
                   :dependencies   [[quil "2.5.0"]] ;; deps to add to classpath
}]
```

**Note the `:cljsbuild-out` key.**
- This should correspond to the `:output-dir` in the compiler options for your build.
- Make sure that compiler options include `:cache-analysis true` (see the [example cljsbuild options](https://github.com/mhuebert/cljs-live/blob/master/script/build_example.clj)).
- Make sure that you have run a build and left this `out` folder intact before running this script.

**Run `bundle.sh live-deps.clj`.**
- This should write a bundle of javascript source files and analysis caches to the `:output-to` path, which you can include on a webpage.
- Use the `load-fn` in `cljs-live.compiler` to read from the bundle.

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
