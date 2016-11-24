# cljs-live

Because ClojureScript in the browser is fun, but packaging dependencies isn't.

**Status:** Alpha

**Requirements:**

- [Planck REPL](planck-repl.org)
- Clojure
- Alembic (in `~/.lein/profiles.clj`)

**Goal**

Given a list of namespaces you'd like to use in the browser (`entry`), and the main namespace of the compiled ClojureScript app (`provided`), **cljs-live** should calculate and package the minimal required set of compiled source files and analysis caches, and help load them into the compiler state.

### Usage

1. Put a symlink to bundle.sh on your path.
2. In your project directory, create a `live-deps.clj` file. It should contain a single map, which accepts the following keys:

```
{:entry          [app.repl-user] ;; a namespace which contains *only* the names you want available in the self-host environment. Transitive deps will be included.
 :provided       [app.core] ;; the `main` namespace of your **compiled** app (to prevent including redundant files)
 :dependencies   [quil "2.5.0"] ;; add dependencies that are not in your `lein classpath` here
 :output-to      "resources/public/js/cljs_live_cache.js" ;; where to put the output file, which you'll include in `index.html`
 :cljsbuild-out  "resources/public/js/compiled/out"} ;; the `out` directory of an existing cljsbuild - we read some cached files from here
```

Then run `bundle.sh --deps live-deps.clj`

## Modifying the bundle

If you aren't happy with the calculated dependencies, you can manually require or exclude via the following keys:

```
{:require-source []
 :require-macros []
 :require-caches []
 :require-goog []
 :require-foreign-libs []

 :exclude-source []
 :exclude-macros []
 :exclude-caches []
 :exclude-goog []
 :exclude-foreign-libs []}
```

The `cljs-live/compiler` namespace contains a `load-fn` that knows how to read from the resulting bundle.

### Notes

- Not all macros work in the self-host environment. Mike Fikes, creator of [Planck,](planck-repl.org) is an expert on the topic, so check out his blog! Eg: [ClojureScript Macro Tower and Loop](http://blog.fikesfarm.com/posts/2015-12-18-clojurescript-macro-tower-and-loop.html), [Collapsing Macro Tower](http://blog.fikesfarm.com/posts/2016-03-04-collapsing-macro-tower.html)
