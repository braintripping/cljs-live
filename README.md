# cljs-live

**Objective:** package ClojureScript dependencies for the self-hosted compiler.

**Status:** Partly working.

**Requirements:** [Planck](planck-repl.org) (I'm using master)

### Instructions

First put your dependences on `project.clj` and edit `live-ns.edn` to include the deps you want to use in a self-hosted environment.

Then run `./build.sh`. The following files will be created:

`resources/public/js/compiled/cljs_live_cache.js` (via `scripts/bootstrap.cljs`)

`resources/public/js/compiled/cljs_live.js` (via `scripts/build.clj`)

Open index.html to view.

### Limitations

- Does not package goog.* requirements
