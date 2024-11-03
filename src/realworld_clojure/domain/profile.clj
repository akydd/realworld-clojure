(ns realworld-clojure.domain.profile
  (:require
   [realworld-clojure.adapters.db :as db]
   [malli.core :as m]
   [malli.error :as me]))

(defn get-profile
  "Get a profile by username. If request is authenticated, set the 'following' field."
  [controller username id]
  (when-let [u (db/get-profile-by-username (:database controller) username)]
    (if (nil? id)
      u
      (let [following (some? (db/get-follows (:database controller) id (:id u)))]
        (assoc u :following following)))))

(def non-empty-string
  (m/schema [:string {:min 1}]))

(defn follow-user
  "Follow a user. Returns the profile of the user being followd, or nil."
  [controller id username]
  (if (m/validate non-empty-string username)
    (when-let [u (db/get-profile-by-username (:database controller) username)]
      (db/insert-follows (:database controller) id (:id u))
      (assoc u :following true))
    {:errors (me/humanize (m/explain non-empty-string username))}))

(defn unfollow-user
  "Unfollow a user. Returns the profilw of the user being unfollowed, or nil."
  [controller id username]
  (when-let [u (db/get-profile-by-username (:database controller) username)]
    (db/delete-follows (:database controller) id (:id u))
    (assoc u :following false)))

(defrecord ProfileController [database])

(defn new-profile-controller []
  (map->ProfileController {}))
