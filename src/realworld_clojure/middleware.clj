(ns realworld-clojure.middleware
  (:require [cambium.core :as log]
            [buddy.auth :refer [authenticated?]]
            [realworld-clojure.adapters.db :as db]))

(defn wrap-exception [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :body {:errors (str "Internal error" (.getMessage e))}}))))

(defn wrap-log-req [handler]
  (fn [req]
    (log/info (pr-str req))
    (handler req)))

(defn wrap-no-auth-error [handler]
  (fn [req]
    (if-not (authenticated? req)
      (do
        (log/warn "request it not authenticated")
        {:status 403})
      (handler req))))

(defn wrap-auth-user [handler database]
  (fn [req]
    (if (authenticated? req)
      (let [auth-id (get-in req [:identity :id])
            auth-user (db/get-user database auth-id)]
        (if auth-user
          ;; TODO: do not leak the encrypted password
          (handler (assoc req :auth-user auth-user))
          (handler req)))
      (handler req))))
