(ns realworld-clojure.domain.profile
  (:require
   [realworld-clojure.adapters.db :as db]
   [realworld-clojure.domain.converters :as c]
   [malli.core :as m]
   [malli.error :as me]))

(defn get-profile
  "Get a profile by username. If auth-user is provided, set the 'following' field."
  ([controller username auth-user]
   (when-let [u (get-profile controller username)]
     (let [following (some? (db/get-follows (:database controller) (:id auth-user) (:id u)))]
       (assoc u :following following))))
  ([controller username]
   (when-let [u (db/get-user-by-username (:database controller) username)]
     (c/user-db->profile u))))

(def non-empty-string
  (m/schema [:string {:min 1}]))

(defn follow-user
  "Set auth-user to Follow the user with username. Returns the profile of the user being followd, or nil."
  [controller auth-user username]
  (if (m/validate non-empty-string username)
    (when-let [u (db/get-user-by-username (:database controller) username)]
      (db/insert-follows (:database controller) (:id auth-user) (:id u))
      (assoc (c/user-db->profile u) :following true))
    {:errors (->> username
                  (m/explain non-empty-string)
                  (me/humanize))}))

(defn unfollow-user
  "Set auth-user to unfollow a user. Returns the profile of the user being unfollowed, or nil."
  [controller auth-user username]
  (when-let [u (db/get-user-by-username (:database controller) username)]
    (db/delete-follows (:database controller) (:id auth-user) (:id u))
    (assoc (c/user-db->profile u) :following false)))

(defrecord ProfileController [database])

(defn new-profile-controller []
  (map->ProfileController {}))
