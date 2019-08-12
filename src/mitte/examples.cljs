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

  (prn :examples)

  )