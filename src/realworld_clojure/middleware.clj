(ns realworld-clojure.middleware
  (:require [cambium.core :as log]
            [buddy.auth :as buddy-auth]
            [realworld-clojure.adapters.db :as db]))

(defn wrap-exception [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           (let [ex-data (ex-data e)]
             (if (= (:type ex-data) :duplicate)
               {:status 409
                :body {:errors "hi"}}
               (case (::buddy-auth/type ex-data)
                 ::buddy-auth/unauthorized {:status 403}
                 {:status 500
                  :body {:errors (str "Internal error" (.getMessage e))}})))))))

(defn wrap-log-req [handler]
  (fn [req]
    (log/info (pr-str req))
    (handler req)))

(defn wrap-no-auth-error [handler]
  (fn [req]
    (if-not (buddy-auth/authenticated? req)
      (do
        (log/warn "request it not authenticated")
        {:status 401})
      (handler req))))

(defn wrap-auth-user [handler database]
  (fn [req]
    (if (buddy-auth/authenticated? req)
      (let [auth-id (get-in req [:identity :id])
            auth-user (db/get-user database auth-id)]
        (if auth-user
          ;; do not leak the encrypted password
          (handler (assoc req :auth-user (dissoc auth-user :password)))
          (handler req)))
      (handler req))))
