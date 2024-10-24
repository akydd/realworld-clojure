(ns realworld-clojure.middleware
  (:require [cambium.core  :as log]
            [buddy.auth :refer [authenticated?]]))

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
      {:status 403}
      (handler req))))
