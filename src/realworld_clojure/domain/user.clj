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
  (assoc user :password (hashers/derive (:password user))))

(defn create-token
  "Returns the user with a token"
  [jwt-secret user]
  (assoc user :token (jwt/sign {:id (:id user)} jwt-secret)))

(defn register-user
  "Register a user"
  [controller user]
  (if (m/validate User user)
    (let [u (->> user
                 hash-password
                 (db/insert-user (:database controller))
                 (create-token (:jwt-secret controller)))]
      {:user (dissoc u :id :password)})
    {:errors (me/humanize (m/explain User user))}))

(def UserLogin
  [:map
   [:email [:string {:min 1}]]
   [:password [:string {:min 1}]]])

(defn login-user
  "User login"
  [user]
  (if (m/validate UserLogin user)
    {:user user}
    {:errors (me/humanize (m/explain UserLogin user))}))

(defrecord UserController [jwt-secret database])

(defn new-user-controller [jwt-secret]
  (map->UserController {:jwt-secret jwt-secret}))
