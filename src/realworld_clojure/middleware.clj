(set! *warn-on-reflection* true)

(ns realworld-clojure.middleware
  (:require [buddy.auth :as buddy-auth]
            [cambium.core :as log]
            [realworld-clojure.adapters.db :as db]))

(defn wrap-exception
  "Convert exceptions to http status codes.

  If the db encountered a duplicate record on insert, return 409.
  If the request is not authorized, return 403.
  Otherwise, return 500."
  [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           (let [data (ex-data e)]
             (if (= (:type data) :duplicate)
               {:status 409
                :body {:errors data}}
               (case (::buddy-auth/type data)
                 ::buddy-auth/unauthorized {:status 403}
                 (do
                   (log/error {} e "Caught exception")
                   {:status 500
                    :body {:errors (str
                                    "Internal error"
                                    (.getMessage e))}}))))))))

(defn wrap-log-req
  "Middleware to log requests."
  [handler]
  (fn [req]
    (log/info (pr-str req))
    (handler req)))

(defn wrap-no-auth-error
  "Respond with a 401 when the request has not been authenticated."
  [handler]
  (fn [req]
    (if-not (buddy-auth/authenticated? req)
      (do
        (log/warn "request is not authenticated")
        {:status 401})
      (handler req))))

(defn wrap-auth-user
  "Add the authenticated user to the request if the request is authenticated."
  [handler database]
  (fn [req]
    (if (buddy-auth/authenticated? req)
      (let [auth-id (get-in req [:identity :id])
            auth-user (db/get-user database auth-id)]
        (if auth-user
          ;; do not leak the encrypted password
          (handler (assoc req :auth-user (dissoc auth-user :password)))
          (handler req)))
      (handler req))))
