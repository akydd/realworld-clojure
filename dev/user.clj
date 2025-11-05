(ns user
  (:require
   [realworld-clojure.core :as core]
   [clojure.core :as c]
   [clojure.tools.namespace.repl :as repl]
   [realworld-clojure.config :as config]))

(def system nil)

(defn init
  "Constructs the dev system"
  []
  (alter-var-root #'system (-> (config/read-config)
                               (core/new-system)
                               (c/constantly))))

(defn start []
  (alter-var-root #'system core/start))

(defn stop []
  (alter-var-root #'system
                  (fn [s] (when s (core/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (repl/refresh-all :after 'user/go))
