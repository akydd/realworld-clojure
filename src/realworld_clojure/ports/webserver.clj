(ns realworld-clojure.ports.webserver
  (:require
   [com.stuartsierra.component :as component]
   [realworld-clojure.ports.router :as router]
   [org.httpkit.server :as http-server]))

(defrecord Webserver [port router server]
  component/Lifecycle

  (start [component]
    (println "Starting webserver on port" port)
    (assoc component :server (http-server/run-server (router/app-routes router) {:port port})))

  (stop [component]
    (println "Stopping webserver")
    ((:server component) :timeout 10)
    (assoc component :server nil)))

(defn new-webserver [port]
  (map->Webserver {:port port}))
