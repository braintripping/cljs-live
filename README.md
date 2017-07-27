# CLJS-Live

ALPHA

Since 2015, it has been possible to write and evaluate ClojureScript in a web browser. This is great! But a tough challenge remains: loading of external libraries into the environment. And what fun is programming if you can't re-use other people's code?

You can use **cljs-live** to precompile 'dependency bundles' which can be loaded on-demand from a web browser.

### What are the main goals?

1. Extensibility: a way to load many different libraries into the same self-hosted ClojureScript dev environment on-demand, without having to rebuild the whole project.

2. Speed: self-host projects are already very large, we should do what we can to keep things snappy (eg. precompilation, lazy loading).

### What does it do?

**cljs-live** works in two parts.

### Part I: create dependency bundles.

We start by making a file to describe the bundles we want, usually called `live-deps.clj`:

```clj
{:cljsbuild-out "resources/public/js/compiled/live-out"
 :bundles-out   "resources/public/js/bundles"
 :source-paths  ["src"]
 :bundles       [ { ... YOUR BUNDLES HERE ...} ]}
```

Then we must describe what bundles we want. Each bundle needs a `:name`, a list of `:entry` namespaces (what users should be able to load and use), and a list of `:provided` namespaces (this is usually the `:main` namespace of your compiled app, which will already be in the environment.)

It should look something like this:

```clj
{...
 :bundles [{:name  my-app.user

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

From this description, cljs-live will compile your project and write a single JSON file for each bundle, each containing the necessary files, to your `:bundle-out` directory. As well, a copy of the original source for every file in your project is copied to a `sources` subdirectory.

The JSON file is a simple mapping of paths to content. It will contain:

- Analysis cache files for every `:entry` namespace, and its transitive dependencies
- Compiled javascript for all required macro namespaces, as well as any namespace that isn't already included in the `:provided` build
- A `"provided"` key with a complete list of namespaces that are expected to be already loaded by the `:provided` app.

### Part II: consume dependency bundles in the browser.

How do we use the bundles created in Part I?

**Feeding files to the compiler**

There are two important functions in `cljs-live.compiler` that probably everyone using this tool would use:

* `add-bundle!` adds a bundle created by the bundle script to a local cache.
* `load-fn` should be passed as the :load function to `cljs.js` eval/compile functions, and will correctly resolve namespaces to assets in the cache.

**Eval!**

There are also functions in `cljs-live.eval` that make it easy to manage the eval/compile process:

* `eval-str` evaluates all of the forms in a given string, and returns the last result.
* `eval` evaluates a single form.

These functions do the same thing as what you find in `cljs.js`, but include extra information along with the result:

  :value or :error - same as in `cljs.js`, just the evaluation result
  :error-position  - the 0-indexed position of the error, if present
  :compiled-js     - javascript source emitted by the compiler
  :source          - original source string that was evaluated
  :source-map      - base64-encoded source-map string
  :env             - a map containing :ns (current namespace) 

(The :env key should probably be renamed/revisited. The current namespace is stored in a compiler-env atom; the `ns` and `in-ns` repl-special functions update this atom whenever the user changes the namespace.)

### Example

;; TODO: update this example! It's stale!
https://cljs-live.firebaseapp.com

### Requirements:

- [Planck REPL](planck-repl.org), version 2.5
- Clojure

### Notes

- Not all macros work in the self-host environment. Mike Fikes, creator of [Planck,](planck-repl.org) is an expert on the topic, so check out his blog! Eg: [ClojureScript Macro Tower and Loop](http://blog.fikesfarm.com/posts/2015-12-18-clojurescript-macro-tower-and-loop.html), [Collapsing Macro Tower](http://blog.fikesfarm.com/posts/2016-03-04-collapsing-macro-tower.html)
- Figuring out what does and doesn't work in the self-hosted environment can be tricky.

----

# Questions & Answers


### What is the ClojureScript 'compiler'?

It's the thing that turns your beautiful ClojureScript code into raw JavaScript code that can run in a browser (or on node.js, etc.).

----

### What is a 'self-hosted' compiler?

It is a compiler written in the same language that it compiles to. Originally, parts of the ClojureScript compiler were written in Java or Clojure (JVM), so you needed a Java environment to produce new ClojureScript code. When ClojureScript got a 'self-hosted' compiler in 2015, it became possible to write and compile ClojureScript without any Java involved at all, meaning we can now have nice development environments to play with in a web browser.

----

### What is an analysis cache?

When Clojure begins compiling a source file to javascript, it begins by 'analyzing' the source, taking note of the name of the namespace, the names of `defs` and `macros` in the namespace, what other namespaces are required or imported, and so on. This info is stored in an ordinary Clojure map in the 'compiler state', under the `:cljs.analyzer/namespaces` key. Clojure needs to use this namespace info to make sense of new code, eg. when you `eval` something at the repl which references a previously defined var.

After a ClojureScript project is fully converted to javascript, we normally 'throw away' all of this namespace info because it is no longer needed -- we are left with exactly the javascript our app needs to run. _However_, usage of the self-hosted compiler is a special case, because we want to _continue_ evaluating new code, which we expect should 'know' about the structure of the existing codebase.

So before we can use the self-hosted compiler with existing dependencies, we first need (A) to load the existing code (if it isn't already in the compiled app), and (B) populate the 'compiler state' with all that missing information.

----

### Why load files individually, rather than encoding/decoding the entire cache?

A shortcut you can use for simple uses of the self-hosted compiler is to create a ClojureScript build that contains everything you want available for use, save a transit-encoded snapshot of the compiler state for that project during/after the build process, and then load up that saved cache into the compiler state.

This shortcut comes with two limitations.

1. Startup time. Analysis caches can be very large, and take hundreds of milliseconds to decode in a browser (on a fast computer!), during which the page is unresponsive. Because self-host builds can't use advanced compilation, they are usually very large to begin with -- so these extra delays really add up.

    Instead of one long noticeable delay, we can deserialize and load dependencies on-demand as the user evaluates code which requires them, leading to shorter delays which occur precisely after the user has issued a command.

2. Extensibility. We can include analysis caches and source files that were not part of an original build, allowing users to try out new libraries, other than what was initially compiled with the app. Because Google Closure does not maintain dependency information or tracking for 'compiled' builds, we have to do this manually, otherwise namespaces will be 'accidentally' loaded more than once, causing bugs.

    This is why every bundle is broken into small pieces, and contains metadata about what namespaces it has provided.

----

### Why is the bundle JSON and not transit?

A flat JSON structure which mimics a directory of files is simple, requires a minimum of parsing, and is easy to inspect in a browser's web console. Analysis caches themselves are transit-encoded.

----

### Why do you include the "provides" key in the bundle?

The "provides" key lists the _transitive_ dependencies of the bundle's :provided namespaces. We need this to avoid loading source code for the same dependency more than once. This allows us to create multiple independent yet overlapping bundles for the same app, so users could choose to load many different libraries without worry that these libraries will interfere with each other.

----
