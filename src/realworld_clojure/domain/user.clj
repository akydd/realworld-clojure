(ns realworld-clojure.domain.user
  (:require [realworld-clojure.adapters.db :as db]
            [malli.core :as m]
            [malli.error :as me]
            [buddy.hashers :as hashers]
            [buddy.sign.jwt :as jwt]
            [buddy.auth :refer [throw-unauthorized]]))

(def user-schema
  [:map {:closed true}
   [:username [:string {:min 1}]]
   [:password [:string {:min 1}]]
   [:email [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]])

(defn- hash-password
  "Returns the user with a hashed version of the password"
  [user]
  (if (:password user)
    (update user :password hashers/derive)
    user))

(defn- add-token
  "Returns the user with a token"
  [jwt-secret user]
  (assoc user :token (jwt/sign {:id (:id user)} jwt-secret)))

(defn- password-valid?
  "Returns true if the password is valid."
  [incoming-password encrypted-password]
  (:valid (hashers/verify incoming-password encrypted-password)))

(defn get-user
  "Return the current user."
  [_controller auth-user token]
  (assoc auth-user :token token))

(defn register-user
  "Register a user"
  [controller user]
  (if (m/validate user-schema user)
    (let [new-user (db/insert-user (:database controller) (hash-password user))]
      (add-token (:jwt-secret controller) new-user))
    {:errors (me/humanize (m/explain user-schema user))}))

(def user-login-schema
  [:map {:closed true}
   [:email [:string {:min 1}]]
   [:password [:string {:min 1}]]])

(defn login-user
  "User login"
  [controller user]
  (if (m/validate user-login-schema user)
    (when-let [fetched-user (db/get-user-by-email (:database controller) (:email user))]
      (let [incoming-password (:password user)
            encrypted-password (:password fetched-user)]
        (if (password-valid? incoming-password encrypted-password)
          (add-token (:jwt-secret controller) fetched-user)
          (throw-unauthorized))))
    {:errors (me/humanize (m/explain user-login-schema user))}))

(def user-update-schema
  [:map {:closed true}
   [:email {:optional true} [:string {:min 1}]]
   [:username {:optional true} [:string {:min 1}]]
   [:password {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]])

(defn update-user
  "Update a user record"
  [controller auth-user updates]
  (if (m/validate user-update-schema updates)
    (->> updates
         hash-password
         (db/update-user (:database controller) auth-user)
         (add-token (:jwt-secret controller)))
    {:errors (me/humanize (m/explain user-update-schema updates))}))

(defrecord UserController [jwt-secret database])

(defn new-user-controller [jwt-secret]
  (map->UserController {:jwt-secret jwt-secret}))
