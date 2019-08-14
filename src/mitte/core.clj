(ns mitte.core
  (:require [cider.piggieback :as pback]
            [mitte.repl-env :as ml-repl]
            [nrepl.cmdline :as ncmd]
            nrepl.server
            cider.nrepl
            [mitte.mitte-server :as session]))

(def nrepl-handler (apply nrepl.server/default-handler
                          (conj (mapv resolve cider.nrepl/cider-middleware)
                                #'cider.piggieback/wrap-cljs-repl)))

(defn cljs-repl
  "Turn an existing REPL into a CLJS repl connected to MarkLogic"
  ([] (cljs-repl {}))
  ([options]
   (let [options (session/with-defaults options)
         repl-env (ml-repl/->MarkLogicEnv options)]
     (pback/cljs-repl repl-env
                      :compiler-env
                      (-> repl-env :compiler-options :compiler-env)))))

(defn -main []
  (ncmd/-main
    "--handler" "mitte.core/nrepl-handler"
    "--interactive"))

(comment
  ;; invoke in any nREPL with piggieback middleware installed
  (cljs-repl {:user "admin-local"
              :password "admin"
              :port 8000
              :database "Documents"}))