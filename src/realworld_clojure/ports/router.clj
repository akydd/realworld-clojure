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
   (core/POST "/api/users/login" [:as {{:keys [user]} :body}] ((handlers/login-user (:handler router)) user))
   (core/POST "/api/users" [:as {{:keys [user]} :body}] ((handlers/register-user (:handler router)) user))
   (core/GET "/api/articles/:slug" [slug] {:status 200})
   (core/GET "/api/tags" [] {:status 200})))

(defn app-routes-with-auth [router]
  (core/routes
   (core/GET "/api/user" [:as {{:keys [id]} :identity}] ((handlers/get-user (:handler router)) id))
   (core/PUT "/api/user" [:as {{:keys [id]} :identity} :as {{:keys [user]} :body}] ((handlers/update-user (:handler router)) id user))
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
   (core/GET "/api/profiles/:username" [username :as {{:keys [id]} :identity}] ((handlers/get-profile (:handler router)) username id))
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
          (core/wrap-routes wrap-no-auth-error)
          (core/wrap-routes wrap-authentication backend))
         (->
          (app-routes-optional-auth router)
          (core/wrap-routes wrap-authentication backend)))
        wrap-log-req
        wrap-exception
        wrap-json-response
        (wrap-content-type "text/json")
        (wrap-json-body {:keywords? true}))))
