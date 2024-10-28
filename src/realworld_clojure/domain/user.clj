(ns realworld-clojure.domain.user
  (:require [realworld-clojure.adapters.db :as db]
            [malli.core :as m]
            [malli.error :as me]
            [buddy.hashers :as hashers]
            [buddy.sign.jwt :as jwt]))

(def User
  [:map
   [:username [:string {:min 1}]]
   [:password [:string {:min 1}]]
   [:email [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]])

(defn hash-password
  "Returns the user with a hashed version of the password"
  [user]
  (if (:password user)
    (assoc user :password (hashers/derive (:password user)))
    user))

(defn add-token
  "Returns the user with a token"
  [jwt-secret user]
  (assoc user :token (jwt/sign {:id (:id user)} jwt-secret)))

(defn register-user
  "Register a user"
  [controller user]
  (if (m/validate User user)
    (->> user
         hash-password
         (db/insert-user (:database controller))
         (add-token (:jwt-secret controller)))
    {:errors (me/humanize (m/explain User user))}))

(defn get-user
  "Get a user"
  [controller id]
  (when-let [u (db/get-user (:database controller) id)]
    (add-token (:jwt-secret controller) u)))

(def UserLogin
  [:map
   [:email [:string {:min 1}]]
   [:password [:string {:min 1}]]])

(defn login-user
  "User login"
  [controller user]
  (if (m/validate UserLogin user)
    (when-let [fetched-user (db/get-user-by-email (:database controller) (:email user))]
      (let [incoming-password (:password user)
            encrypted-password (:password fetched-user)]
        (when (:valid (hashers/verify incoming-password encrypted-password))
          (add-token (:jwt-secret controller) fetched-user))))
    {:errors (me/humanize (m/explain UserLogin user))}))

(def UserUpdate
  [:map
   [:email {:optional true} [:string {:min 1}]]
   [:username {:optional true} [:string {:min 1}]]
   [:password {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]])

(defn update-user
  "Update a user record"
  [controller id user]
  (if (m/validate UserUpdate user)
    (->> user
         hash-password
         (db/update-user (:database controller) id)
         (add-token (:jwt-secret controller)))
    {:errors (me/humanize (m/explain UserUpdate user))}))

(defrecord UserController [jwt-secret database])

(defn new-user-controller [jwt-secret]
  (map->UserController {:jwt-secret jwt-secret}))
