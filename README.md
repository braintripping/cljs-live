# cljs-live

Packaging ClojureScript dependencies for the self-hosted compiler.

**Status:** Partly working.

**Requirements:** [Planck](planck-repl.org) (I'm using master)

### Instructions

Put your dependences in `project.clj`, run `lein deps`, and edit `live-ns.edn` to include the deps you want to use in a self-hosted environment.

Then run `./build.sh`. The following files will be created:

`resources/public/js/compiled/cljs_live_cache.js`

- created by `scripts/bootstrap.cljs`
- a cache of compiled javascript source files, foreign libs, and analysis caches to be loaded by the self-hosted compiler

`resources/public/js/compiled/cljs_live.js`

 - created by `scripts/build.clj`
 - a regular ClojureScript build
 - look in `cljs-live.compiler` to see the load-function that reads from the cache, enabling ordinary `(ns..)` and `(require..)` expressions

Open index.html to view.

### Limitations

- Does not package goog.* requirements