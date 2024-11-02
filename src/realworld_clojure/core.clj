(ns realworld-clojure.core
  (:require
   [realworld-clojure.config :as config]
   [com.stuartsierra.component :as component]
   [realworld-clojure.adapters.db :as db]
   [realworld-clojure.ports.webserver :as webserver]
   [realworld-clojure.domain.user :as user]
   [realworld-clojure.domain.profile :as profile]
   [realworld-clojure.ports.handlers :as handlers]
   [realworld-clojure.ports.router :as router])
  (:gen-class))

(defn new-system [config]
  (let [dbspec (:dbspec config)
        server-config (:server config)
        jwt-secret (:jwt-secret config)]
    (component/system-map
     :database (db/new-database dbspec)
     :user-controller (component/using
                       (user/new-user-controller jwt-secret)
                       [:database])
     :profile-controller (component/using
                           (profile/new-profile-controller)
                           [:database])
     :handler (component/using
               (handlers/new-handler)
               [:user-controller :profile-controller])
     :router (component/using
              (router/new-router jwt-secret)
              [:handler])
     :web-server (component/using
                  (webserver/new-webserver (:port server-config))
                  [:router]))))

(defn start [system]
  (component/start-system system))

(defn stop [system]
  (component/stop-system system))

(defn -main []
  (println "Starting RealWorld Clojure")
  (start (new-system (config/read-config))))
