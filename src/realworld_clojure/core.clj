(ns realworld-clojure.core
  (:require
   [realworld-clojure.config :as config]
   [com.stuartsierra.component :as component]
   [realworld-clojure.adapters.db :as db]
   [realworld-clojure.ports.webserver :as webserver]
   [realworld-clojure.domain.user :as user]
   [realworld-clojure.ports.handlers :as handlers]
   [realworld-clojure.ports.router :as router])
  (:gen-class))

(defn realworld-clojure-system [config]
  (let [dbspec (:dbspec config)
        server-config (:server config)]
    (component/system-map
     :database (db/new-database dbspec)
     :user-controller (component/using
                       (user/new-user-controller)
                       [:database])
     :handlers (component/using
                (handlers/new-handler)
                [:user-controller])
     :router (component/using
              (router/new-router)
              [:handlers])
     :web-server (component/using
                  (webserver/new-webserver (:port server-config))
                  [:router]))))

(defn -main []
  (let [system (-> (config/read-config)
                   (realworld-clojure-system)
                   (component/start-system))]
    (println "Starting RealWorld Clojure")
    (.addShutdownHook
     (Runtime/getRuntime)
     (new Thread #(component/stop-system system)))))
