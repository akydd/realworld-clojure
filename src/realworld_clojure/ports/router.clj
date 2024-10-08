(ns realworld-clojure.ports.router
  (:require
   [realworld-clojure.ports.handlers :as handlers]
   [compojure.core :as core]))

(defrecord Router [handler])

(defn new-router []
  (->Router nil))

(defn app-routes [router]
  (core/routes
   (core/POST "/api/users/login" [] {:status 200})
   (core/POST "/api/users" [] (handlers/no-op (:handler router)))
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
   (core/GET "/api/tags" [] {:status 200})))
