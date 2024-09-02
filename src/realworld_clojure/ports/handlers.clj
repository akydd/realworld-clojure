(ns realworld-clojure.ports.handlers
  (:require [realworld-clojure.domain.user :as user]))

(defn register-user
  "Register a user"
  [request]
  (let [u (user/register-user (get-in request [:body :user]))]
    (if (u :errors)
      {:status 422
       :body u}
      {:status 200
       :body u})))
