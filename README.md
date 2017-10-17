# CLJS-Live

**cljs-live** is a small library with some convenience functions for evaluating ClojureScript using the self-hosted compiler. It _previously_ also contained a build script for dependency bundling, but that has been happily superceded by [shadow-cljs](https://github.com/thheller/shadow-cljs/), which recently [added support](https://code.thheller.com/blog/shadow-cljs/2017/10/14/bootstrap-support.html) for compiling and loading dependencies for the self-hosted compiler.



The **cljs-live.eval** namespace contains two main functions:

* `eval-str` evaluates all of the forms in a given string, and returns the last result.
* `eval` evaluates a single form.

These functions do the same thing as what you find in `cljs.js`, but include extra information along with the result:

  :value or :error - same as in `cljs.js`, just the evaluation result
  :error/position  - the 0-indexed position of the error, if present
  :compiled-js     - javascript source emitted by the compiler
  :source          - original source string that was evaluated
  :source-map      - base64-encoded source-map string
  :env             - a map containing :ns (current namespace) 

(The :env key should probably be renamed/revisited. The current namespace is stored in a compiler-env atom; the `ns` and `in-ns` repl-special functions update this atom whenever the user changes the namespace.)

----

# Questions & Answers

_this Q&A was written back when cljs-live still contained a build script for dependency-bundling. it has been left here for info purposes. shadow-cljs now does all the dirty work._

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

