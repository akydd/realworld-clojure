(ns realworld-clojure.adapters.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [com.stuartsierra.component :as component]
            [ragtime.repl :as ragtime-repl]
            [ragtime.jdbc :as ragtime-jdbc]
            [java-time.api :as jt]))

(def query-options
  {:builder-fn rs/as-unqualified-lower-maps})

(def update-options
  {:return-keys true
   :builder-fn rs/as-unqualified-lower-maps})

(defn insert-user
  "Insert record into user table"
  [database user]
  (sql/insert! (:datasource database) :users user update-options))

(defn get-user
  "Get a user record from uesr table"
  [database id]
  (sql/get-by-id (:datasource database) "users" id query-options))

(defn get-user-by-email
  "Get a user record by email"
  [database email]
  (first (sql/find-by-keys (:datasource database) :users {:email email} query-options)))

(defn get-user-by-username
  "Get a user record by username"
  [database username]
  (first (sql/find-by-keys (:datasource database) :users {:username username} query-options)))

(defn update-user
  "Update a user record"
  [database id data]
  (sql/update! (:datasource database) :users data {:id id} update-options))

(defn get-follows
  "Get a record from the follows table"
  [database follower-id following-id]
  (first (sql/find-by-keys (:datasource database) :follows {:user_id follower-id :follows following-id} query-options)))

(defn following?
  "Returns true if the user is following the other user."
  [database user other-user]
  (some? (get-follows database (:id user) (:id other-user))))

(defn insert-follows
  "Create a record in the follows table"
  [database follower-id following-id]
  (sql/insert! (:datasource database) :follows {:user_id follower-id :follows following-id}))

(defn delete-follows
  "Remove record from the follows table"
  [database follower-id following-id]
  (sql/delete! (:datasource database) :follows {:user_id follower-id :follows following-id}))

(defn get-article
  "Get a user record from uesr table"
  [database id]
  (sql/get-by-id (:datasource database) "articles" id query-options))

(defn create-article
  "Insert a record into the articles table"
  [database article]
  (sql/insert! (:datasource database) :articles article query-options))

(defn get-article-by-slug
  "Find a record in the article table by slug"
  [database slug]
  (first (sql/find-by-keys (:datasource database) :articles {:slug slug} query-options)))

(defn update-article
  "Update a record in the articles table"
  [database id article]
  (let [updated-at (jt/local-date-time)]
    (sql/update! (:datasource database) :articles (assoc article :updatedat updated-at) {:id id} update-options)))

(defn delete-article
  "Delete a record from the articles table"
  [database id]
  (sql/delete! (:datasource database) :articles {:id id}))

(defn create-comment
  "Add a record to the comments table"
  [database comment]
  (sql/insert! (:datasource database) :comments comment query-options))

(defn get-article-comments
  "Get all comments for an article"
  [database article-id]
  (sql/find-by-keys (:datasource database) :comments {:article article-id} query-options))

(defn delete-comment
  "Remove a record from the comment table"
  [database id]
  (sql/delete! (:datasource database) :comments {:id id}))

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
