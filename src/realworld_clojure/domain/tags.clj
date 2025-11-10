(ns realworld-clojure.domain.tags
  (:require
   [realworld-clojure.adapters.db :as db]))

(defrecord TagController [database])

(defn get-tags
  "Get all the tags."
  [controller]
  (db/get-tags (:database controller)))

(defn new-tag-controller
  "Create a new TahController."
  []
  (map->TagController {}))
