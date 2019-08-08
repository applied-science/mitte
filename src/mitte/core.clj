(ns mitte.core
  (:require [compojure.core :refer :all]
            [org.httpkit.server :refer [run-server]]
            [nrepl.server :as server]
            [cider.piggieback :as pback]
            [cljs.repl :as repl]
            [clojure.java.shell :refer [sh]]))

;; This pair of queues are used to buffer forms going out to, and
;; results coming in from, the JS runtime in Marklogic.
(defonce evaluation-queue (java.util.concurrent.LinkedBlockingQueue.))
(defonce result-queue (java.util.concurrent.LinkedBlockingQueue.))
(defonce session (atom nil))

;; this interface can be passed to piggieback to wrap it in an nREPL session
#_(defrecord MarkLogicEnv [engine debug]
  repl/IReplEnvOptions
  (-repl-options [this]
    {:output-dir ".cljs_nashorn_repl"
     :target :nashorn})
  repl/IJavaScriptEnv
  (-setup [this {:keys [output-dir bootstrap output-to] :as opts}]
    (init-engine engine opts debug)
    (let [env (ana/empty-env)]
      (if output-to
        (load-js-file engine output-to)
        (bootstrap-repl engine output-dir opts))
      (repl/evaluate-form this env repl-filename
                          '(.require js/goog "cljs.core"))
      ;; monkey-patch goog.isProvided_ to suppress useless errors
      (repl/evaluate-form this env repl-filename
                          '(set! js/goog.isProvided_ (fn [ns] false)))
      ;; monkey-patch goog.require to be more sensible
      (repl/evaluate-form this env repl-filename
                          '(do
                             (set! *loaded-libs* #{"cljs.core"})
                             (set! (.-require js/goog)
                                   (fn [name reload]
                                     (when (or (not (contains? *loaded-libs* name)) reload)
                                       (set! *loaded-libs* (conj (or *loaded-libs* #{}) name))
                                       (js/CLOSURE_IMPORT_SCRIPT
                                        (if (some? goog/debugLoader_)
                                          (.getPathFromDeps_ goog/debugLoader_ name)
                                          (goog.object/get (.. js/goog -dependencies_ -nameToPath) name))))))))))
  (-evaluate [{engine :engine :as this} filename line js]
    (when debug (println "Evaluating: " js))
    (try
      {:status :success
       :value (if-let [r (eval-str engine js)] (.toString r) "")}
      (catch ScriptException e
        (let [^Throwable root-cause (clojure.stacktrace/root-cause e)]
          {:status :exception
           :value  (eval-str engine "cljs.repl.error__GT_str(cljs.core._STAR_e)")}))
      (catch Throwable e
        (let [^Throwable root-cause (clojure.stacktrace/root-cause e)]
          {:status :exception
           :value (cljs.repl/ex-str (cljs.repl/ex-triage (Throwable->map root-cause)))}))))
  (-load [{engine :engine :as this} ns url]
    (load-ns engine ns))
  (-tear-down [this]
    (tear-down-engine engine))
  repl/IParseStacktrace
  (-parse-stacktrace [this frames-str ret opts]
    (st/parse-stacktrace this frames-str
                         (assoc ret :ua-product :nashorn) opts))
  repl/IParseError
  (-parse-error [_ err _]
    (update-in err [:stacktrace]
               (fn [st]
                 (string/join "\n" (drop 1 (string/split st #"\n")))))))

;; (server/start-server
;;   :handler (server/default-handler #'pback/wrap-cljs-repl)
;;   ; ...additional `start-server` get_options as desired
;;   )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The HTTP server we use to communicate with MarkLogic's internal context

;; to insert a form for remote evaluation in testing
(comment
  (.put evaluation-queue (str "1 + " (rand-int 100))))

;; to manually read a form from the result queue
;; NB this is a blocking operation!
;; (.take result-queue)

(defroutes mitte-server
  (GET "/request-form" req
       (if (= (get-in req [:headers "x-repl-session"]) @session)
         (.take evaluation-queue)                           ; next item in the queue, blocks if queue empty!
         {:status 500 :body "Invalid session"}))
  (PUT "/return-result" req
       (let [res-str (slurp (clojure.java.io/reader (:body req) :encoding "UTF-8"))]
         (println (str "computed: " res-str))
         (.put result-queue res-str) ; from which our nREPL server must read
         {:status  200
          :headers {}
          :body    ""})))

(defonce stop-server (atom nil))

(defn restart-server
  "Start or restart the HTTP server."
  []
  (when @stop-server
    (@stop-server))
      (reset! stop-server (run-server mitte-server {:port (Integer. (or (System/getenv "PORT") 9999))}))

      ;; create a new session (persistent v8 context in MarkLogic)
      (reset! session (.toString (java.util.UUID/randomUUID)))
      (future (sh "node" "./client/start_evaluator.js" "--session" @session)))

(comment
  ;; to start or restart the server (which must happen after changes to the routes)
  (restart-server)

  )
