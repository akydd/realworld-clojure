(ns realworld-clojure.domain.tags
  (:require
   [realworld-clojure.adapters.db :as db]))

(defrecord TagController [database])

(defn get-tags
  [controller]
  (db/get-tags (:database controller)))

(defn new-tag-controller []
  (map->TagController {}))
