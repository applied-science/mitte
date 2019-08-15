(ns mitte.repl-client
  (:require [goog.object :as gobj]))

(def global (js-this))
(def put-result (.-put_result global))

(defn format-result [result]
  {:status (.-status result)
   :value (.-value result)}
  (-> (js->clj result :keywordize-keys true)
      (update :status keyword)
      (str)))

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
              (or (gobj/get (.. js/goog -dependencies_ -nameToPath) name)
                  (throw (js/Error. (str "No path for dep: " name)))))))))

(defn- repl-print [& args]
  (apply js/console.log (mapv str (to-array args)))
  (put-result #js{:status "print"
                  :value (mapv str args)}))

(defn enable-repl-print! []
  (set! *print-fn* repl-print)
  (set! *print-err-fn* repl-print)
  (set! *print-newline* false))

(defn init []
  (this-as this
    (set! (.-format_result this) format-result)
    (closure-implementations)
    (enable-repl-print!)))

(init)