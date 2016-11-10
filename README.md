# cljs-live

ClojureScript dependency packaging for the self-hosted compiler.

**Status:** Alpha, still figuring things out

**Requirements:** [Planck](planck-repl.org) (I'm using master)

**Goals**

1. Pre-build cljs/js dependencies for use in a self-hosted environment
2. (Eventually) supply requested deps on-demand for browser-based self-hosted ClojureScript?

### Usage

Clone this repo and:

1. Run `lein deps` to get all the dependencies listed in `project.clj` on the `lein classpath`.
2. `lein npm install` (see npm deps in project.clj)
3. Edit `live-deps.clj` to include the deps you want to use in a self-hosted environment. If you are adding new deps make sure to add them to `project.clj` and repeat #1.
4. Run `script/script/bootstrap.cljs`, and include the output file in your webpage. Have a look at `src/cljs_live/compiler.cljs` to see what client-side usage looks like - pay special attention to `load-fn` and `preloads!`.

To get an example running in this repo, the following script will perform all the steps listed above and also produce an example usage build that you can view at `resources/public/index.html`:

`./build.sh --watch`

(Remove `--watch` to avoid recompiling on source edits)

### live-deps file format

The `live-deps` file contains a map of dependencies. Entries in the map are evaluated in the body of an `ns` expression, except for:

 - `:require-caches` - namespaces for which an analysis cache (not compiled source) will be bundled. This option is for namespaces that are already included in your compiled build.
 - `:preload-caches` - same as above, but caches are loaded immediately (this is done by default for `cljs.core` and `cljs.core$macros`)
 - `:preload-macros` - raw Clojure source code will be included for the specified macro namespaces, and preloaded
 - `:output-to` is the destination path for the emitted javascript.

### Things that work

- **foreign libs** - javascript files from [cljsjs](http://cljsjs.github.io/) packages & other ClojureScript libraries (specified in `deps.cljs`) are loaded.
- **macros**, so long as they are in ClojureScript and are self-hosted-compiler friendly. (further reading by Mike Fikes: [ClojureScript Macro Tower and Loop](http://blog.fikesfarm.com/posts/2015-12-18-clojurescript-macro-tower-and-loop.html), [Collapsing Macro Tower](http://blog.fikesfarm.com/posts/2016-03-04-collapsing-macro-tower.html))
- Google Clojure Library dependencies, like `goog.events`. (I haven't tested this extensively yet)

### Limitations

Libraries that cannot be loaded by Planck are not packaged correctly (eg. Quil and Sablono). Two known causes:

1. Macros that are not compatible with self-hosted ClojureScript (eg. Sablono)
2. Libraries that require a browser environment to load (eg. Quil, which loads Processing.js)
