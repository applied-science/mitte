(ns mitte.mitte-server
  (:refer-clojure :exclude [load-file])
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [compojure.core :refer :all]
            [ring.middleware.params :refer [wrap-params]]
            [org.httpkit.server :as httpkit]
            [clojure.java.shell :refer [sh]]
            [clojure.java.io :as io]
            [cljs.closure :as closure]
            [cljs.env :as env]
            [clojure.string :as str]
            [cljs.util :as util]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [nrepl.cmdline]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Settings, override by passing options to `mitte.core/cljs-repl`

(defn compiler-defaults []
  {:output-dir
   (str/join [".repl-ml-cljs-" (util/clojurescript-version)])})

(defn server-defaults []
  {:mitte-host "localhost"
   :mitte-port (Integer. (or (System/getenv "POLL_PORT") 9999))
   :host "localhost"
   :port 8000
   :database "Documents"
   :user "admin-local"
   :password "admin"
   :debug true
   :compiler-options (compiler-defaults)})

(defn with-defaults [options]
  (-> (merge (server-defaults)
             {:session-id (.toString (java.util.UUID/randomUUID))}
             options)
      (update :compiler-options #(merge (compiler-defaults) %))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Evaluation

;; This pair of queues are used to buffer forms going out to, and
;; results coming in from, the JS runtime in Marklogic.
(defonce evaluation-queue (java.util.concurrent.LinkedBlockingQueue.))
(defonce result-queue (java.util.concurrent.LinkedBlockingQueue.))

;; to insert a form for remote evaluation in testing
(comment
  (.put evaluation-queue {:action :eval
                          :code (str "1 + 1")})

  (.put evaluation-queue {:action :quit})

  ;; to manually read a form from the result queue
  ;; NB this is a blocking operation!
  (.take result-queue))

;; https://github.com/clojure/clojurescript/blob/master/src/main/clojure/cljs/repl/node.clj
(defn eval-str
  "Evaluates JavaScript string in MarkLogic context.
   Returns map containing :type, :status, and :value."
  [js]
  (.put evaluation-queue {:action :eval :code js})
  (.take result-queue))

(defn get-resource
  "Returns string for resource in classpath or :output-dir"
  [opts path]
  (slurp (let [file (io/file (:output-dir (:compiler-options opts)) path)]
           (if (.exists file)
             file
             (io/resource path)))))

(def eval-resource (comp eval-str get-resource))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The HTTP server we use to communicate with MarkLogic's internal context

(defn format-result [req]
  (let [res-str (slurp (clojure.java.io/reader (:body req) :encoding "UTF-8"))
        content-type (first (str/split ((:headers req) "content-type") #";"))]
    (case content-type
      "application/json"
      (-> (json/read-str res-str :key-fn keyword)
          (update :status keyword))
      "application/edn"
      (edn/read-string
        ;; returns nil for all unknown reader tags
        {:default (constantly nil)} res-str))))             ; from which our nREPL server must read))

(defroutes mitte-server

  (GET "/repl" req
    ;; client is polling for next instruction
    (try
      (if (= (get-in req [:headers "x-repl-session-id"]) (-> req :repl-options :session-id))
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/write-str (.take evaluation-queue))}   ; next item in the queue, blocks if queue empty!
        {:status 403 :body "Invalid session"})

      ;; on server restart, ignore interrupted request errors
      (catch java.lang.InterruptedException error
        {:status 403 :body "Server restarted"})))

  (PUT "/repl" req
    ;; client is returning a result
    (let [{:as result
           :keys [status value]} (format-result req)]
      (case status
        :print (apply println (map edn/read-string value))
        (.put result-queue result))                         ; from which our nREPL server must read
      ""))

  (GET ["/resource/:path" :path #".+"] req
    ;; client is requesting a file
    (let [path (-> req :params :path)]
      {:status 200
       :body (get-resource (:repl-options req) path)})))

(defonce stop-server* (atom nil))

(defn stop-server []
  (when @stop-server*
    (@stop-server*))
  (.clear evaluation-queue)
  (.clear result-queue))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JS environment setup

(defn bootstrap-repl [{:as opts
                       :keys [output-dir]}]
  (env/ensure
    (let [deps-file (str output-dir "_deps.js")
          core (io/resource "cljs/core.cljs")
          core-js (closure/compile core
                                   (assoc opts :output-file
                                               (closure/src-file->target-file
                                                 core (dissoc opts :output-dir))))
          deps (closure/add-dependencies opts core-js)]
      ;; output unoptimized code and the deps file
      ;; for all compiled namespaces
      (apply closure/output-unoptimized
             (assoc opts :output-to (.getPath (io/file output-dir deps-file)))
             deps))))

(defn init-session [{:as opts
                     :keys [debug
                            compiler-options]}]

  ;; compile
  (bootstrap-repl compiler-options)

  ;; init closure variables
  (eval-str (format "var CLJS_DEBUG = %s;" (boolean debug)))
  (eval-str (format "var CLJS_OUTPUT_DIR = \"%s\";" (:output-dir compiler-options)))
  (eval-str (format "CLOSURE_UNCOMPILED_DEFINES = %s;"
                    (json/write-str (:closure-defines compiler-options))))

  ;; load closure library
  (eval-resource opts "goog/base.js")
  (eval-resource opts "goog/deps.js")

  ;; load deps index
  (eval-resource opts (str (:output-dir compiler-options) "_deps.js"))

  ;; minimal closure loading utils
  (eval-resource opts "closure_bootstrap.js"))

(defn start-client [{:keys [session-id
                                 mitte-host mitte-port
                                 port host database user password]}]
  (let [{:as result :keys [exit err out]}
        (sh "node" "./scripts/start_evaluator.js"
            ;; REPL config
            "--session_id" session-id
            "--mitte_port" (str mitte-port)
            "--mitte_host" mitte-host

            ;; MarkLogic config
            "--host" host
            "--port" (str port)
            "--database" database
            "--user" user
            "--password" password)]
    (println out)
    (when-not (zero? exit)
      (println err)
      (throw (Exception. "Evaluator installation failed")))))

(defn start-server [{:as options
                     :keys [mitte-port]}]
  (stop-server)
  (reset! stop-server* (httpkit/run-server
                         (comp (wrap-params #'mitte-server)
                               (partial merge {:repl-options options}))
                         {:port mitte-port})))

(comment
  (start-server (with-defaults {:session-id "1"}))
  (start-client (with-defaults {:session-id "1"})))

(defn start-session
  [& [repl-options]]
  (let [options (with-defaults repl-options)]
    (start-server options)
    (start-client options)
    (init-session options)))


