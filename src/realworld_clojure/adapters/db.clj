(ns realworld-clojure.adapters.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [com.stuartsierra.component :as component]
            [ragtime.repl :as ragtime-repl]
            [ragtime.jdbc :as ragtime-jdbc]))

(defn insert-user
  "Insert record into user table"
  [database user]
  (sql/insert! (:datasource database) :users user {:builder-fn rs/as-unqualified-lower-maps}))

(defn migrate
  "Migrate the db"
  [database]
  (ragtime-repl/migrate (:migration-config database)))

(defn rollback
  "Rollback db migrations"
  [database]
  (ragtime-repl/rollback (:migration-config database)))

(defrecord Database [dbspec datasource migration-config]
  component/Lifecycle

  (start [component]
    (println "Starting database with" dbspec)
    (let [ds (jdbc/get-datasource dbspec)
          migration-config {:datastore (ragtime-jdbc/sql-database dbspec)
                            :migrations (ragtime-jdbc/load-resources "migrations")}]
      (ragtime-repl/migrate migration-config)
      (assoc component
             :datasource ds
             :migration-config migration-config)))

  (stop [component]
    (println "Stopping database")
    (assoc component :datasource nil)))

(defn new-database [dbspec]
  (map->Database {:dbspec dbspec}))
