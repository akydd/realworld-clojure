(ns realworld-clojure.ports.router
  (:require
   [realworld-clojure.ports.handlers :as handlers]
   [realworld-clojure.middleware :refer [wrap-exception wrap-no-auth-error wrap-log-req]]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [buddy.auth.middleware :refer [wrap-authentication]]
   [compojure.core :as core]
   [buddy.auth.backends :as backends]))

(defrecord Router [jwt-secret handler])

(defn new-router [jwt-secret]
  (map->Router {:jwt-secret jwt-secret}))

(defn app-routes-no-auth [router]
  (core/routes
   (core/GET "/api/health" [] (handlers/health (:handler router)))
   (core/POST "/api/users/login" [] (handlers/login-user (:handler router)))
   (core/POST "/api/users" [] (handlers/register-user (:handler router)))
   (core/GET "/api/articles/:slug" [slug] {:status 200})
   (core/GET "/api/tags" [] {:status 200})))

(defn app-routes-with-auth [router]
  (core/routes
   (core/GET "/api/user" [] (handlers/get-user (:handler router)))
   (core/PUT "/api/user" [] (handlers/update-user (:handler router)))
   (core/GET "/api/profiles/:username" [username] {:status 200})
   (core/POST "/api/profiles/:username/follow" [username] {:status 200})
   (core/DELETE "/api/profiles/:username/follow" [username] {:status 200})
   (core/GET "/api/articles/feed" [] {:status 200})
   (core/POST "/api/articles" [] {:status 200})
   (core/PUT "/api/articles/:slug" [slug] {:status 200})
   (core/DELETE "/api/articles/:slug" [slug] {:status 200})
   (core/POST "/api/articles/:slug/comments" [slug] {:status 200})
   (core/DELETE "/api/articles/:slug/comments/:id" [slug id] {:status 200})
   (core/POST "/api/articles/:slug/favorite" [slug] {:status 200})
   (core/DELETE "/api/articles/:slug/favorite" [slug] {:status 200})))

(defn app-routes-optional-auth [router]
  (core/routes
   (core/GET "/api/articles" [] {:status 200})
   (core/GET "/api/articles/:slug/comments" [slug] {:status 200})))

(defn unauthorized-handler [_]
  {:status 403})

(defn app-routes [router]
  (let [backend (backends/jws {:secret (:jwt-secret router) :token-name "Token" :unauthorized-handler unauthorized-handler})]
    (-> (core/routes
         (app-routes-no-auth router)
         (->
          (app-routes-with-auth router)
          wrap-log-req
          wrap-no-auth-error
          (wrap-authentication backend))
         (app-routes-optional-auth router))
        wrap-exception
        wrap-json-response
        (wrap-content-type "text/json")
        (wrap-json-body {:keywords? true}))))
