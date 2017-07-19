(ns cljs-live.bundle
  (:require [clojure.tools.reader :as r]
            [cljs.js-deps :as deps]
            [cljs.closure :as cljsc]
            [cljs.env :as env]
            [cljs.js-deps :as js-deps]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [cljs.build.api :as api]
            [clojure.set :as set]
            [clojure.string :as string]))

(defn get-classpath
  "Returns the current classpath as a string."
  []
  (->> (seq (.getURLs (java.lang.ClassLoader/getSystemClassLoader)))
       (map #(.getPath %))
       (string/join ":")))

(defn mk-tmp-dir!
  ;; from https://gist.github.com/samaaron/1398198
  "Creates a unique temporary directory on the filesystem. Typically in /tmp on
  *NIX systems. Returns a File object pointing to the new directory. Raises an
  exception if the directory couldn't be created after 10000 tries."
  []
  (let [base-dir (io/file (System/getProperty "java.io.tmpdir"))
        base-name (str (System/currentTimeMillis) "-" (long (rand 1000000000)) "-")
        tmp-base (str base-dir "/" base-name)
        max-attempts 10000]
    (.getAbsolutePath
      (loop [num-attempts 1]
        (if (= num-attempts max-attempts)
          (throw (Exception. (str "Failed to create temporary directory after " max-attempts " attempts.")))
          (let [tmp-dir-name (str tmp-base num-attempts)
                tmp-dir (io/file tmp-dir-name)]
            (if (.mkdir tmp-dir)
              tmp-dir
              (recur (inc num-attempts)))))))))

(defn cljs-deps

  [& inputs]
  (binding [env/*compiler* (env/default-compiler-env)]
    (let [opts (->> (cljsc/get-upstream-deps*)
                    (merge-with concat (some-> (io/resource "deps.cljs")
                                               slurp
                                               r/read-string)))]
      (swap! env/*compiler* assoc :js-dependency-index (deps/js-dependency-index opts))
      (-> (set inputs)
          (cljsc/find-cljs-dependencies)
          (cljsc/add-js-sources opts)))))

(defn transitive-deps
  "Return sequence of transitive dependencies of deps, as namespace symbols"
  [deps]
  (->> (set (apply cljs-deps deps))
       (mapcat deps/-requires)
       set
       (#(disj % nil))
       (map symbol)
       set))

(require 'alembic.still)
(defn install-deps! [{:keys [bundles]}]
  (doseq [{dependencies :dependencies} bundles]
    (some-> dependencies (seq) (alembic.still/distill))))

(defn compile-cljs [src-path output-dir]
  (api/build src-path {:output-dir     output-dir
                       :dump-core      false
                       :parallel-build true
                       :source-map     false
                       :cache-analysis true
                       :optimizations  :none}))

(defn compile-sources!
  "Compile source-paths to ClojureScript. We have to do this *without* using the self-hosted
  compiler because :provided namespaces don't need to be self-host compatible,
  but we still need their analysis caches and transitive dependency graphs."
  [{:keys [source-paths bundles cljsbuild-out]}]
  (doseq [s (->> (mapcat :source-paths bundles)
                 (concat source-paths)
                 (distinct))]
    (compile-cljs s cljsbuild-out)))

;(programs planck)

(defn dir-absolute-path [p]
  (-> p
      (string/replace #"/$" "")
      (str "/x")
      (io/make-parents))
  (.getCanonicalPath (io/file (str "./" p))))



(defn build [bundle-spec-path]
  (let [bundle-spec (-> bundle-spec-path
                        (slurp)
                        (r/read-string))
        _ (do (install-deps! bundle-spec)
              (compile-sources! bundle-spec))
        bundle-spec (-> bundle-spec
                        (update :bundles #(mapv (fn [{:keys [provided] :as bundle}]
                                                  (-> bundle
                                                      (assoc :provided/transitive (transitive-deps provided))
                                                      (set/rename-keys {:provided :provided/entry}))) %))
                        (update :cljsbuild-out dir-absolute-path)
                        (update :output-dir dir-absolute-path)
                        (assoc :goog-dependencies (js-deps/goog-dependencies)))
        {:keys [exit out err]} (shell/sh "planck"
                                         "-k" (mk-tmp-dir!)
                                         "-c" (get-classpath)
                                         "-m" "cljs-live.bundle-planck"
                                         :in (with-out-str (prn bundle-spec)))]

    (some->> exit (println :exit))
    (some->> err (println :err))
    (some->> out (println :out))))