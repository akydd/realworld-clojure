(ns realworld-clojure.adapters.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [realworld-clojure.config :as config]))

(def ds (jdbc/get-datasource ((config/read-config) :db)))

(defn insert-user
  "Insert record into user table"
  [user]
  (sql/insert! ds :users user {:builder-fn rs/as-unqualified-lower-maps}))
