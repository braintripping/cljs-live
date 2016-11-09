# cljs-live

Package ClojureScript dependencies for the self-hosted compiler.

**Status:** Alpha, still figuring things out

**Requirements:** [Planck](planck-repl.org) (I'm using master)

**Goals**

Stage 1 - easy to pre-build cljs/js dependencies for use in a self-hosted environment
Stage 2 - a web service to supply requested cljs/js deps on-demand for browser-based self-hosted ClojureScript

### Usage

Clone this repo and then:

1. Run `lein deps` to get all the dependencies listed in `project.clj` on the `lein classpath`.
2. `lein npm install` (see npm deps in project.clj)
3. Edit `live-deps.clj`, or create another file of the same format, and include the deps you want to use in a self-hosted environment. If you are adding new deps make sure to add them to `project.clj` and repeat #1.
4. Run `script/script/bootstrap.cljs --live-deps <your-dep-file>`, and include the output file in your webpage. Have a look at `src/cljs_live/compiler.cljs` to see what client-side usage looks like - pay special attention to `load-fn` and `preloads!`.

To get an example running in this repo, you can run the script:

`./build.sh --watch --live-deps live-deps.clj`

Then open resources/public/index.html.

This script precompiles the self-hosted deps listed in live-deps.clj, and builds a example environment that you can view in index.html. (Remove `--watch` if you don't want it to recompile on changes)

### live-deps file format

A `live-deps` file contains a list of bundles, which will contain source code and analysis caches for the required namespaces. Entries in each map will be run in the body of an `ns` expression, except for:

 - `:require-cache` lists namespaces for which **only** the analysis cache (not compiled source) will be bundled.
 - `:output-to` is the destination path for the emitted javascript.

### Things that work

- foreign libs (so we can include stuff from [cljsjs](http://cljsjs.github.io/), or foreign libs from your own libraries)
- **macros**, so long as they are in ClojureScript and are self-hosted-compiler friendly. (further reading by Mike Fikes: [ClojureScript Macro Tower and Loop](http://blog.fikesfarm.com/posts/2015-12-18-clojurescript-macro-tower-and-loop.html), [Collapsing Macro Tower](http://blog.fikesfarm.com/posts/2016-03-04-collapsing-macro-tower.html))
- Google Clojure Library dependencies, like `goog.events`. (I haven't tested others yet)

### Limitations

Libraries that cannot be loaded by Planck are not packaged correctly (eg. Sablono). Two known causes:

1. Macros that are not compatible with self-hosted ClojureScript
2. Libraries that require a browser environment to load (eg/ Quil's Processing.js require)
