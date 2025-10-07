(ns realworld-clojure.integration.common
  (:require
   [cheshire.core :as json]
   [realworld-clojure.config-test :as config]
   [org.httpkit.client :as http]))

(def port (get-in (config/read-test-config) [:server :port]))
(def host "http://localhost")
(def base-url (str host ":" port "/api"))

(defn- get-headers
  ([]
   {"Content-Type" "application/json"})
  ([token]
   {"Content-Type" "application/json"
    "Authorization" (str "Token " token)}))

;; Testing endpoints for all operations.

(defn get-user-request
  ([]
   @(http/get (str base-url "/user") {:headers (get-headers)}))
  ([token]
   @(http/get (str base-url "/user") {:headers (get-headers token)})))

(defn get-profile-request
  ([username]
   @(http/get (str base-url "/profiles/" username) {:headers (get-headers)}))
  ([username token]
   @(http/get (str base-url "/profiles/" username) {:headers (get-headers token)})))

(defn login-request
  ([user]
   @(http/post (str base-url "/users/login") {:headers (get-headers)
                                              :body (json/generate-string {:user (select-keys user [:email :password])})})))

(defn update-user-request
  ([user]
   @(http/put (str base-url "/user") {:headers (get-headers)
                                      :body (json/generate-string {:user user})}))
  ([user token]
   @(http/put (str base-url "/user") {:headers (get-headers token)
                                      :body (json/generate-string {:user user})})))

(defn register-request
  [user]
  @(http/post (str base-url "/users") {:headers (get-headers)
                                       :body (json/generate-string {:user (select-keys user [:username :password :email])})}))

;; Response schemas, used for validation.

(def user-response-schema
  [:map {:closed true}
   [:username [:string {:min 1}]]
   [:token [:string {:min 1}]]
   [:email [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]])

(def no-auth-profile-schema
  [:map {:closed true}
   [:username [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]])

(def auth-profile-schema
  [:map {:closed true}
   [:username [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]
   [:following [:boolean]]])
