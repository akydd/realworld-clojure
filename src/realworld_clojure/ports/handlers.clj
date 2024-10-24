(ns realworld-clojure.ports.handlers
  (:require [realworld-clojure.domain.user :as user]))

(defrecord Handler [user-controller])

(defn new-handler []
  (->Handler nil))

(defn health [_]
  (fn [req]
    {:status 200
     :body (:body req)}))

(defn register-user
  "Register a user"
  [handler]
  (fn [req]
    (let [u (user/register-user (:user-controller handler) (get-in req [:body :user]))]
      (if (u :errors)
        {:status 422
         :body u}
        {:status 200
         :body u}))))

(defn login-user
  "Login a user"
  [_]
  (fn [_]
    {:status 200}))

(defn get-user
  "get a user"
  [handler]
  (fn [req]
    (let [u (user/get-user (:user-controller handler) (get-in req [:identity :id]))]
      (if (nil? (:user u))
        {:status 404}
        {:status 200
         :body u}))))

(defn update-user
  "Update a user"
  [_]
  (fn [_]
    {:status 200}))
