(ns realworld-clojure.ports.handlers
  (:require [realworld-clojure.domain.user :as user]
            [buddy.auth :refer [authenticated?]]))

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
  [_]
  (fn [req]
    (if (authenticated? req)
      {:status 200}
      {:status 400})))

(defn update-user
  "Update a user"
  [_]
  (fn [_]
    {:status 200}))
