(ns realworld-clojure.domain.user
  (:require [realworld-clojure.adapters.db :as db]
            [malli.core :as m]
            [malli.error :as me]
            [buddy.hashers :as hashers]))

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

(defn register-user
  "Register a user"
  [controller user]
  (if (m/validate User user)
    (let [u (hash-password user)]
      {:user (dissoc (db/insert-user (:database controller) u) :id :password)})
    {:errors (me/humanize (m/explain User user))}))

(defrecord UserController [database])

(defn new-user-controller []
  (->UserController nil))
