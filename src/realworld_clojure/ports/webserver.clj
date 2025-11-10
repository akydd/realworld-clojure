(ns realworld-clojure.ports.webserver
  (:require
   [buddy.auth.backends :as backends]
   [buddy.auth.middleware :refer [wrap-authentication]]
   [camel-snake-kebab.core :as csk]
   [com.stuartsierra.component :as component]
   [compojure.coercions :refer [as-int]]
   [compojure.core :as core]
   [org.httpkit.server :as http-server]
   [realworld-clojure.middleware :refer [wrap-exception
                                         wrap-no-auth-error
                                         wrap-log-req
                                         wrap-auth-user]]
   [realworld-clojure.ports.handlers :as handlers]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.params :refer [wrap-params]]))

(defn app-routes-no-auth [handler]
  (core/routes
   (core/GET "/api/health" req (handlers/health handler req))

   (core/POST "/api/users/login" [:as {{:keys [user]} :body}]
     (handlers/login-user handler user))

   (core/POST "/api/users" [:as {{:keys [user]} :body}]
     (handlers/register-user handler user))

   (core/GET "/api/tags" [] (handlers/get-tags handler))))

(defn app-routes-with-auth [handler]
  (core/routes
   (core/GET "/api/user" [:as {:keys [auth-user headers]}]
     (handlers/get-user handler auth-user headers))

   (core/PUT "/api/user" [:as {:keys [auth-user]} :as {{:keys [user]} :body}]
     (handlers/update-user handler auth-user user))

   (core/POST "/api/profiles/:username/follow"
     [username :as {:keys [auth-user]}]
     (handlers/follow-user handler auth-user username))

   (core/DELETE "/api/profiles/:username/follow"
     [username :as {:keys [auth-user]}]
     (handlers/unfollow-user handler auth-user username))

   (core/GET "/api/articles/feed" [:as {:keys [params auth-user]}]
     (handlers/article-feed handler params auth-user))

   (core/POST "/api/articles"
     [:as {:keys [auth-user]} :as {{:keys [article]} :body}]
     (handlers/create-article handler article auth-user))

   (core/PUT "/api/articles/:slug"
     [slug :as {:keys [auth-user]} :as {{:keys [article]} :body}]
     (handlers/update-article handler slug article auth-user))

   (core/DELETE "/api/articles/:slug" [slug :as {:keys [auth-user]}]
     (handlers/delete-article handler slug auth-user))

   (core/POST "/api/articles/:slug/comments"
     [slug :as {:keys [auth-user]} :as {{:keys [comment]} :body}]
     (handlers/create-comment handler slug comment auth-user))

   (core/DELETE "/api/articles/:slug/comments/:id"
     [slug id :<< as-int :as {:keys [auth-user]}]
     (handlers/delete-comment handler slug id auth-user))

   (core/POST "/api/articles/:slug/favorite" [slug :as {:keys [auth-user]}]
     (handlers/favorite-article handler slug auth-user))

   (core/DELETE "/api/articles/:slug/favorite" [slug :as {:keys [auth-user]}]
     (handlers/unfavorite-article handler slug auth-user))))

(defn app-routes-optional-auth [handler]
  (core/routes
   (core/GET "/api/profiles/:username" [username :as {:keys [auth-user]}]
     (handlers/get-profile handler username auth-user))

   (core/GET "/api/articles" [:as {:keys [params auth-user]}]
     (handlers/list-articles handler params auth-user))

   (core/GET "/api/articles/:slug" [slug :as {:keys [auth-user]}]
     (handlers/get-article-by-slug handler slug auth-user))

   (core/GET "/api/articles/:slug/comments" [slug :as {:keys [auth-user]}]
     (handlers/get-comments handler slug auth-user))))

;; TODO: is this needed?
(defn unauthorized-handler [_]
  {:status 403})

(defn app-routes [handler jwt-secret database]
  (let [backend (backends/jws {:secret jwt-secret
                               :token-name "Token"
                               :unauthorized-handler unauthorized-handler})]
    (-> (core/routes
         (->
          (app-routes-no-auth handler)
          (core/wrap-routes wrap-log-req))
         (->
          (app-routes-with-auth handler)
          (core/wrap-routes wrap-log-req)
          (core/wrap-routes wrap-keyword-params)
          (core/wrap-routes wrap-params)
          (core/wrap-routes wrap-auth-user database)
          (core/wrap-routes wrap-no-auth-error)
          (core/wrap-routes wrap-authentication backend))
         (->
          (app-routes-optional-auth handler)
          (core/wrap-routes wrap-log-req)
          (core/wrap-routes wrap-keyword-params)
          (core/wrap-routes wrap-params)
          (core/wrap-routes wrap-auth-user database)
          (core/wrap-routes wrap-authentication backend)))
        wrap-exception
        (wrap-json-response {:date-format "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
                             :key-fn csk/->camelCaseString})
        (wrap-content-type "text/json")
        (wrap-json-body {:key-fn csk/->kebab-case-keyword}))))

(defrecord Webserver [port jwt-secret handler database server]
  component/Lifecycle

  (start [component]
    (println "Starting webserver on port" port)
    (assoc component :server (http-server/run-server
                              (app-routes handler jwt-secret database)
                              {:port port})))

  (stop [component]
    (println "Stopping webserver")
    ((:server component) :timeout 10)
    (assoc component :server nil)))

(defn new-webserver [port jwt-secret]
  (map->Webserver {:port port :jwt-secret jwt-secret}))
