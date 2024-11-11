(ns realworld-clojure.domain.profile
  (:require
   [realworld-clojure.adapters.db :as db]
   [malli.core :as m]
   [malli.error :as me]))

(defn get-profile
  "Get a profile by username. If user is provided, set the 'following' field."
  ([controller username user]
   (when-let [u (get-profile controller username)]
     (let [following (some? (db/get-follows (:database controller) (:id user) (:id u)))]
       (assoc u :following following))))
  ([controller username]
   (db/get-profile-by-username (:database controller) username)))

(def non-empty-string
  (m/schema [:string {:min 1}]))

(defn follow-user
  "Set the user to Follow the user with username. Returns the profile of the user being followd, or nil."
  [controller user username]
  (if (m/validate non-empty-string username)
    (when-let [u (db/get-profile-by-username (:database controller) username)]
      (db/insert-follows (:database controller) (:id user) (:id u))
      (assoc u :following true))
    {:errors (me/humanize (m/explain non-empty-string username))}))

(defn unfollow-user
  "Unfollow a user. Returns the profilw of the user being unfollowed, or nil."
  [controller user username]
  (when-let [u (db/get-profile-by-username (:database controller) username)]
    (db/delete-follows (:database controller) (:id user) (:id u))
    (assoc u :following false)))

(defrecord ProfileController [database])

(defn new-profile-controller []
  (map->ProfileController {}))
