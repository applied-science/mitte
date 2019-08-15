(ns mitte.examples
  (:require [clojure.string :as str]
            [clojure.set :as set]))

(comment

  ;; use required namespaces
  (str/upper-case "a")
  (set/difference #{:a :b} #{:a})

  ;; list globals
  (this-as this
    (set (js/Object.keys this)))

  ;; print to console (nothing special implemented here)
  (prn {:example-of-printed-thing []})

  ;; errors are caught and printed
  (throw (js/Error. "Whatever"))

  ;; require a namespace
  (require '[goog.object :as gobj])
  (gobj/get #js{:a 1} "a")


  ;; require a namespace that has a :foreign-lib dep
  (require '[tubax.core])
  ;; fails
  ;; 1. :foreign-libs aren't being processed
  ;; 2. even when processed, there are issues with the "sax" dep

  )
