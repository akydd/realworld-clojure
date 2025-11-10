(ns user
  (:require
   [clojure.core :as c]
   [clojure.tools.namespace.repl :as repl]
   [realworld-clojure.config :as config]
   [realworld-clojure.core :as core]))

(def system
  "Used to hold the entire application system.
  See https://github.com/stuartsierra/component."
  nil)

(defn init
  "Constructs the dev system."
  []
  (alter-var-root #'system (-> (config/read-config)
                               (core/new-system)
                               (c/constantly))))

(defn start
  "Start the system."
  []
  (alter-var-root #'system core/start))

(defn stop
  "Stop the system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (core/stop s)))))

(defn go
  "Initialize and start the system."
  []
  (init)
  (start))

(defn reset
  "Stop, reset, and then start the system."
  []
  (stop)
  (repl/refresh-all :after 'user/go))
