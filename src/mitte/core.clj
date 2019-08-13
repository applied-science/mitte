(ns mitte.core
  (:require [cider.piggieback :as pback]
            [mitte.repl-env :as ml-repl]
            nrepl.server
            cider.nrepl))

(def nrepl-handler (apply nrepl.server/default-handler
                          (conj (mapv resolve cider.nrepl/cider-middleware)
                                #'cider.piggieback/wrap-cljs-repl)))

(defn cljs-repl
  "Turn an existing REPL into a CLJS repl connected to MarkLogic"
  ([] (cljs-repl {}))
  ([options]
   (let [repl-env (ml-repl/make-env options)]
     (pback/cljs-repl repl-env
                      :compiler-env
                      (-> repl-env :compiler-options :compiler-env)))))
(comment

  ;; invoke in any nREPL with piggieback middleware installed
  (cljs-repl {:user "admin-local"
              :password "admin"
              :port 8000
              :database "Documents"})

  )