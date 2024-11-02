(ns realworld-clojure.domain.profile
  (:require
   [realworld-clojure.adapters.db :as db]))

(defn get-profile
  "Get a profile"
  [controller username id]
  (when-let [u (db/get-profile-by-username (:database controller) username)]
    (let [following (some? (db/get-follows (:database controller) id (:id u)))]
      (assoc u :following following))))

(defrecord ProfileController [database])

(defn new-profile-controller []
  (map->ProfileController {}))
