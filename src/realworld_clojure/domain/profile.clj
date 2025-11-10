(ns realworld-clojure.domain.profile
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [realworld-clojure.adapters.db :as db]))

(defn get-profile
  "Get a profile by `username`."
  ([controller username]
   (db/get-profile (:database controller) username))
  ([controller username auth-user]
   (db/get-profile (:database controller) username auth-user)))

(def ^:private non-empty-string
  (m/schema [:string {:min 1}]))

(defn follow-user
  "Set `auth-user` to follow the user with `username`.
  Returns the profile of the user being followed, or `nil.`"
  [controller auth-user username]
  (if (m/validate non-empty-string username)
    (when-let [u (db/get-user-by-username (:database controller) username)]
      (db/follow-user (:database controller) auth-user u)
      (db/get-profile (:database controller) username auth-user))
    {:errors (->> username
                  (m/explain non-empty-string)
                  (me/humanize))}))

(defn unfollow-user
  "Set `auth-user` to unfollow user having `username`.
  Returns the profile of the user being unfollowed, or `nil`."
  [controller auth-user username]
  (if (m/validate non-empty-string username)
    (when-let [u (db/get-user-by-username (:database controller) username)]
      (db/unfollow-user (:database controller) auth-user u)
      (db/get-profile (:database controller) username auth-user))
    {:errors (->> username
                  (m/explain non-empty-string)
                  (me/humanize))}))

(defrecord ProfileController [database])

(defn new-profile-controller
  "Create a new ProfileController."
  []
  (map->ProfileController {}))
