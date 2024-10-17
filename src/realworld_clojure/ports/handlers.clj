(ns realworld-clojure.ports.handlers
  (:require [realworld-clojure.domain.user :as user]))

(defrecord Handler [user-controller])

(defn new-handler []
  (->Handler nil))

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

(defn no-op [handler]
  (fn [req]
    {:status 200
     :body (:body req)}))
