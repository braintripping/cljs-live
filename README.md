# cljs-live

Package ClojureScript dependencies for the self-hosted compiler.

**Status:** Alpha, partly working.

**Requirements:** [Planck](planck-repl.org) (I'm using master)

### Usage

1. Make sure dependencies are on your `lein classpath`. Eg/ put your deps in `project.clj` and run `lein deps`.
2. Edit `live-deps.clj`, or create another file of the same format.
3. Run `scripts/bootstrap.cljs --live-deps <your-dep-file>`, and include the output file in your webpage.
4. Have a look at `cljs-live.compiler` and pay special attention to `load-fn` and `preload-caches!`.

The `bundle.sh` script loads npm dependencies via `lein-npm`, copies `deps.cljs` to `opts.clj` as required for Planck to find foreign-libs correctly, creates all dep bundles listed in the file provided by `--live-deps`, and builds this example project.

### live-deps file format

The file should contain a list of maps. Each entry represents an output file which will contain javascript source and analysis caches for the required namespaces. Entries in each map will be run in the body of an `ns` expression, except for:

 - `:require-cache` lists namespaces for which **only** the analysis cache (not compiled source) will be bundled.
 - `:output-to` is the destination path for the emitted javascript.

Open index.html to view.

### Limitations

- Does not package goog.* requirements
- Libraries that cannot be loaded by Planck are not packaged correctly (eg. Sablono)
- cljs.js foreign libs are not included