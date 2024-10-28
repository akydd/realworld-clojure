(ns realworld-clojure.ports.handlers
  (:require [realworld-clojure.domain.user :as user]))

(defrecord Handler [user-controller])

(defn new-handler []
  (->Handler nil))

(defn health [_]
  (fn [req]
    {:status 200
     :body (:body req)}))

(defn clean-user
  "Format the user object before returning it to web based api responses"
  [user]
  (dissoc user :id :password))

(defn register-user
  "Register a user"
  [handler]
  (fn [req]
    (let [u (user/register-user (:user-controller handler) (get-in req [:body :user]))]
      (if (u :errors)
        {:status 422
         :body u}
        {:status 200
         :body {:user (clean-user u)}}))))

(defn login-user
  "Login a user"
  [handler]
  (fn [req]
    (let [u (user/login-user (:user-controller handler) (get-in req [:body :user]))]
      (if (nil? u)
        {:status 403}
        (if (:errors u)
          {:status 422
           :body u}
          {:status 200
           :body {:user (clean-user u)}})))))

(defn get-user
  "get a user"
  [handler]
  (fn [req]
    (let [u (user/get-user (:user-controller handler) (get-in req [:identity :id]))]
      (if (nil? u)
        {:status 404}
        {:status 200
         :body {:user (clean-user u)}}))))

(defn update-user
  "Update a user"
  [handler]
  (fn [req]
    (let [u (user/update-user (:user-controller handler) (get-in req [:identity :id]) (get-in req [:body :user]))]
      (if (nil? u)
        {:status 404}
        (if (:errors u)
          {:status 422
           :body u}
          {:status 200
           :body {:user (clean-user u)}})))))
