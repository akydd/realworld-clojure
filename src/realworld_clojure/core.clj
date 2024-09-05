(ns realworld-clojure.core
  (:require [compojure.core :as core]
            [realworld-clojure.config :as config]
            [realworld-clojure.middleware :refer [wrap-exception]]
            [org.httpkit.server :as http-server]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ragtime.jdbc :as ragtime-jdbc]
            [ragtime.repl :as ragtime-repl]
            [realworld-clojure.ports.handlers :as handlers])
  (:gen-class))

(core/defroutes app-routes
  (core/POST "/api/users/login" [] {:status 200})
  (core/POST "/api/users" [] handlers/register-user)
  (core/GET "/api/user" [] {:status 200})
  (core/PUT "/api/user" [] {:status 200})
  (core/GET "/api/profiles/:username" [username] {:status 200})
  (core/POST "/api/profiles/:username/follow" [username] {:status 200})
  (core/DELETE "/api/profiles/:username/follow" [username] {:status 200})
  (core/GET "/api/articles" [] {:status 200})
  (core/GET "/api/articles/feed" [] {:status 200})
  (core/GET "/api/articles/:slug" [slug] {:status 200})
  (core/POST "/api/articles" [] {:status 200})
  (core/PUT "/api/articles/:slug" [slug] {:status 200})
  (core/DELETE "/api/articles/:slug" [slug] {:status 200})
  (core/POST "/api/articles/:slug/comments" [slug] {:status 200})
  (core/GET "/api/articles/:slug/comments" [slug] {:status 200})
  (core/DELETE "/api/articles/:slug/comments/:id" [slug id] {:status 200})
  (core/POST "/api/articles/:slug/favorite" [slug] {:status 200})
  (core/DELETE "/api/articles/:slug/favorite" [slug] {:status 200})
  (core/GET "/api/tags" [] {:status 200}))

(def migration-config
  {:datastore (ragtime-jdbc/sql-database ((config/read-config) :db))
   :migrations (ragtime-jdbc/load-resources "migrations")})

(defn start
  "Start the server"
  [config]
  (let [port ((config :server) :port)]
    (ragtime-repl/migrate migration-config)
    (http-server/run-server (->
                             #'app-routes
                             wrap-exception
                             wrap-json-response
                             (wrap-content-type "text/json")
                             (wrap-json-body {:keywords? true}))  {:port port})
    (println (str "Running the server at http://localhost:" port "/"))))

(defn -main []
  (let [config (config/read-config)]
    (start config)))

(defn migrate
  "Migrate the db"
  []
  (ragtime-repl/migrate migration-config))

(defn rollback
  "Rollback db migrations"
  []
  (ragtime-repl/rollback migration-config))
