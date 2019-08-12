(ns mitte.marklogic-client
  (:require [goog.object :as gobj]))

(defn format-result [result]
  (js-obj "status" (.-status result)
          "value" (str (.-value result))))

(defn closure-implementations []

  ;; monkey-patch goog.isProvided_ to suppress useless errors
  (set! js/goog.isProvided_ (fn [ns] false))

  ;; monkey-patch goog.require to be more sensible
  (set! *loaded-libs* #{"cljs.core"})
  (set! (.-require js/goog)
        (fn [name reload]
          (when (or (not (contains? *loaded-libs* name)) reload)
            (set! *loaded-libs* (conj (or *loaded-libs* #{}) name))
            (js/CLOSURE_IMPORT_SCRIPT
              (gobj/get (.. js/goog -dependencies_ -nameToPath) name))))))

(defn init []
  (this-as this
    (set! (.-format_result this) format-result)
    (closure-implementations)))

(init)