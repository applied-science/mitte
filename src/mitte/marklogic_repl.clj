(ns mitte.marklogic-repl
  (:require [cljs.repl :as repl]
            [mitte.marklogic-session :as session :refer [eval-str eval-resource]]
            [cljs.compiler :as comp]
            [cljs.stacktrace :as st]
            [clojure.string :as string]))

(def repl-filename "<cljs repl>")

(defrecord MarkLogicEnv [options]
  repl/IReplEnvOptions
  (-repl-options [this] (:compiler-options options))
  repl/IJavaScriptEnv
  (-setup [this {:keys [output-to] :as compiler-opts}]
    (let [env {:context :expr :locals {}}]

      (session/start-session (assoc options :compiler-options compiler-opts))

      (if output-to
        (session/eval-resource compiler-opts output-to)
        (session/init-session options))

      ;; load cljs.core & client lib
      (repl/evaluate-form this env repl-filename
                          '(ns cljs.user
                             (:require mitte.marklogic-client)))))
  (-evaluate [this filename line js]
    (eval-str js))
  (-load [this ns url]
    (eval-str (format "goog.require(\"%s\");" (comp/munge (first ns)))))
  (-tear-down [this]
    (session/stop-server))

  ;; TODO
  ;; this is not tested/implemented
  repl/IParseStacktrace
  (-parse-stacktrace [this frames-str ret opts]
    (st/parse-stacktrace this frames-str
                         (assoc ret :ua-product :chrome) opts))
  ;; TODO
  ;; this is not tested/implemented
  repl/IParseError
  (-parse-error [_ err _]
    (update-in err [:stacktrace]
               (fn [st]
                 (string/join "\n" (drop 1 (string/split st #"\n")))))))

(defn repl-env [& [options]]
  (MarkLogicEnv. (session/with-defaults options)))
