{:cljsbuild-out "resources/public/js/compiled/out"
 :output-dir    "resources/public/js/compiled"
 :bundles       [{#_:dependencies  #_[[quil "2.5.0"]
                                      [cljsjs/bcrypt "2.3.0-0"]
                                      [sablono "0.7.5"]
                                      [cljsjs/react "15.3.1-0"]
                                      [cljsjs/react-dom "15.3.1-0"]]
                  :name          cljs-live-cache
                  :require       [npm.marked
                                  cljsjs.bcrypt
                                  goog.events
                                  [quil.core :include-macros true]]
                  :require-cache [cljs-live.sablono]
                  :provided      [cljs-live.examples]}
                 {:name          goog
                  :require-goog  [goog.dom
                                  goog.string
                                  goog.Uri
                                  goog.history.EventType
                                  goog.object
                                  goog.History
                                  goog.string.StringBuffer
                                  goog.history.Html5History
                                  goog.events]
                  :exclude-cache [cljs.core cljs.core$macros]}
                 {:name          re-tools
                  :require       [[re-view.core :include-macros true]
                                  [re-db.core :include-macros true]]
                  :exclude-goog  [goog.dom
                                  goog.string
                                  goog.Uri
                                  goog.history.EventType
                                  goog.object
                                  goog.History
                                  goog.string.StringBuffer
                                  goog.history.Html5History
                                  goog.events]
                  :exclude-cache [cljs.core cljs.core$macros]}]}

