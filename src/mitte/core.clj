(ns mitte.core
  (:require [cider.piggieback :as pback]
            [mitte.marklogic-repl :as ml-repl]
            [nrepl.server :as nrepl]))

(defonce ^:private nrepl-server* (atom nil))

(defn stop-nrepl []
  (when @nrepl-server*
    (nrepl/stop-server @nrepl-server*)))

(defn restart-nrepl
  "Start or restart an nrepl server with piggieback middleware"
  []
  (stop-nrepl)

  (let [nrepl-port (Integer. (or (System/getenv "NREPL_PORT") 9990))]
    (println (str "nrepl started on port " nrepl-port))
    (reset! nrepl-server* (nrepl/start-server
                            :port nrepl-port
                            :handler (nrepl/default-handler #'pback/wrap-cljs-repl)))
    (spit "./.nrepl-port" (str nrepl-port))))

(defn cljs-repl
  "Turn an existing REPL into a CLJS repl connected to MarkLogic"
  ([] (cljs-repl {}))
  ([options]
   (let [repl-env (ml-repl/repl-env options)]
     (pback/cljs-repl repl-env
                      :compiler-env
                      (-> repl-env :compiler-options :compiler-env)))))

(comment

  ;; starts a new nrepl server.
  ;; you may not need to use this if you already
  ;; have an nrepl connection with piggieback middleware
  (restart-nrepl)

  ;; turns an existing nrepl into a cljs repl
  (cljs-repl {:user "admin-local"
              :password "admin"
              :port 8000
              :database "Documents"})

  )