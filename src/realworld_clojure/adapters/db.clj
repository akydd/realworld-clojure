(ns realworld-clojure.adapters.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [com.stuartsierra.component :as component]
            [ragtime.repl :as ragtime-repl]
            [ragtime.jdbc :as ragtime-jdbc]))

(def query-options
  {:builder-fn rs/as-unqualified-lower-maps})

(def profile-query-options
  (assoc query-options :columns [:id :username :bio :image]))

(defn insert-user
  "Insert record into user table"
  [database user]
  (sql/insert! (:datasource database) :users user query-options))

(defn get-user
  "Get a user record from uesr table"
  [database id]
  (sql/get-by-id (:datasource database) "users" id query-options))

(defn get-user-by-email
  "Get a user record by email"
  [database email]
  (first (sql/find-by-keys (:datasource database) :users {:email email} query-options)))

(defn update-user
  "Update a user record"
  [database id data]
  (sql/update! (:datasource database) :users data {:id id})
  (get-user database id))

(defn get-profile-by-username
  "Get a user by username"
  [database username]
  (first (sql/find-by-keys (:datasource database) :users {:username username} profile-query-options)))

(defn get-follows
  "Get a record from the follows table"
  [database follower-id following-id]
  (first (sql/find-by-keys (:datasource database) :follows {:user_id follower-id :follows following-id} query-options)))

(defn insert-follows
  "Create a record in the follows table"
  [database follower-id following-id]
  (sql/insert! (:datasource database) :follows {:user_id follower-id :follows following-id}))

(defn delete-follows
  "Remove record from the follows table"
  [database follower-id following-id]
  (sql/delete! (:datasource database) :follows {:user_id follower-id :follows following-id}))

(defn create-article
  "Insert a record into the articles table"
  [database article]
  (sql/insert! (:datasource database) :articles article query-options))

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
