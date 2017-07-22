# CLJS-Live


Until 2015, ClojureScript only ran in _compiled_ mode -- we had no `eval` :(. But that changed with the release of the self-hosted compiler :). Today, thanks to tools available in the `cljs.js` namespace, `eval` is easy. This is of particular interest for those of us interested in experimenting with new ideas for editing environments.

But _consuming dependencies_ in this new environment remains an untamed challenge. There are many subtleties that you have to get right in order for a 'live' ClojureScript compiler to co-exist happily alongside a precompiled app. The purpose of this library is to solve these problems.

CLJS-Live has two main parts.

### Part I: create dependency bundles.

We start by making a file like this, usually called `live-deps.clj`:

```clj
{:cljsbuild-out "resources/public/js/compiled/live-out"
 :bundles-out   "resources/public/js/bundles"
 :source-paths  ["src"]
 :bundles [ { ... YOUR BUNDLES HERE ...} ]}
```

Then, we fill in the :bundles with a vector of bundle specifications, which look like this:

```clj
{...
 :bundles [{:name            my-app.user     ;; determines the bundle's filename

            ;; namespaces to include in the bundle. transitive dependencies will be included.
            :entry #{my-app.user}

            ;; namespaces already provided in your compiled app.
            :provided #{my-app.core}

            ;; (optional) namespaces whose dependencies should not be followed/included.
            :entry/no-follow #{}

            ;; (optional) namespaces that should not be included.
            :entry/exclude #{}
            }]
```

CLJS-Live will attempt to calculate and generate all the files necessary to use your `:entry` namespaces in a self-hosted environment. It then bundles all of this together and emits a single JSON file to the `:bundles-out` directory, and also copies all of the related original source files to a `sources` directory in the same location.

The JSON file is a simple mapping of paths to content. It will contain:

- Analysis cache files for every `:entry` namespace
- Compiled javascript for all required macro namespaces, as well as any other namespaces that aren't already in the `:provided` build
- A `"provided"` key with a complete list of namespaces that are expected to be already loaded by the `:provided` app.

### Part II: consume dependency bundles in the browser.

The second part of `cljs-live` is a small library of functions for use _in the browser_.

**Feeding files to the compiler**

There are two important functions in `cljs-live.compiler` that probably everyone using this tool would use:

* `load-bundles!` downloads precompiled bundles created by the bundle script, and merges them into a single local cache (kind of like a fake 'classpath').
* `load-fn` can then be passed as the :load function to `cljs.js` eval/compile functions, and will correctly resolve namespaces to the precompiled sources in the bundle cache.

**Eval!**

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

### Warning

This code depends on implementation details of specific versions of ClojureScript and Planck and is still quite brittle.


### Example

;; TODO: update this example
https://cljs-live.firebaseapp.com

### Requirements:

- [Planck REPL](planck-repl.org)
- Clojure

### Notes

- Not all macros work in the self-host environment. Mike Fikes, creator of [Planck,](planck-repl.org) is an expert on the topic, so check out his blog! Eg: [ClojureScript Macro Tower and Loop](http://blog.fikesfarm.com/posts/2015-12-18-clojurescript-macro-tower-and-loop.html), [Collapsing Macro Tower](http://blog.fikesfarm.com/posts/2016-03-04-collapsing-macro-tower.html)
- Figuring out what does and doesn't work in the self-hosted environment can be tricky.
