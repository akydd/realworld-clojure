(ns realworld-clojure.domain.profile
  (:require
   [realworld-clojure.adapters.db :as db]))

(defn get-profile
  "Get a profile by username. If request is authenticated, set the 'following' field."
  [controller username id]
  (when-let [u (db/get-profile-by-username (:database controller) username)]
    (if (nil? id)
      u
      (let [following (some? (db/get-follows (:database controller) id (:id u)))]
        (assoc u :following following)))))

(defrecord ProfileController [database])

(defn new-profile-controller []
  (map->ProfileController {}))
