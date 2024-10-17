(ns realworld-clojure.ports.webserver
  (:require
   [com.stuartsierra.component :as component]
   [realworld-clojure.middleware :refer [wrap-exception]]
   [realworld-clojure.ports.router :as router]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [org.httpkit.server :as http-server]))

(defrecord Webserver [port router server]
  component/Lifecycle

  (start [component]
    (println "Starting webserver on port" port)
    (assoc component :server (http-server/run-server (->
                                                      (router/app-routes router)
                                                      wrap-exception
                                                      wrap-json-response
                                                      (wrap-content-type "text/json")
                                                      (wrap-json-body {:keywords? true})) {:port port})))

  (stop [component]
    (println "Stopping webserver")
    ((:server component) :timeout 10)
    (assoc component :server nil)))

(defn new-webserver [port]
  (map->Webserver {:port port}))
