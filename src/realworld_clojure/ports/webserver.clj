(ns realworld-clojure.ports.webserver
  (:require
   [com.stuartsierra.component :as component]
   [realworld-clojure.middleware :refer [wrap-exception wrap-log-req]]
   [realworld-clojure.ports.router :as router]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [compojure.core :as core]
   [org.httpkit.server :as http-server]
   [buddy.auth.backends :as backends]
   [buddy.auth.middleware :refer [wrap-authentication]]))

(defrecord Webserver [port jwt-secret router server]
  component/Lifecycle

  (start [component]
    (println "Starting webserver on port" port)
    (let [backend (backends/jws {:secret jwt-secret :token-name "Bearer"})]
      (assoc component :server (http-server/run-server (->
                                                        (core/routes
                                                         (router/app-routes-no-auth router)
                                                         (->
                                                          (router/app-routes-with-auth router)
                                                          (wrap-authentication backend)
                                                          wrap-log-req)
                                                         (router/app-routes-optional-auth router))
                                                        wrap-exception
                                                        wrap-json-response
                                                        (wrap-content-type "text/json")
                                                        (wrap-json-body {:keywords? true}))
                                                       {:port port}))))

  (stop [component]
    (println "Stopping webserver")
    ((:server component) :timeout 10)
    (assoc component :server nil)))

(defn new-webserver [port jwt-secret]
  (map->Webserver {:port port :jwt-secret jwt-secret}))
