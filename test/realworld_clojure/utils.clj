(ns realworld-clojure.utils
  (:require
   [com.stuartsierra.component :as component]
   [next.jdbc.result-set :as rs]
   [realworld-clojure.domain.user :as user]
   [malli.generator :as mg]
   [next.jdbc.sql :as sql]
   [realworld-clojure.domain.article :as article]))

(def query-options
  {:builder-fn rs/as-unqualified-lower-maps})

(def update-options
  {:return-keys true
   :builder-fn rs/as-unqualified-lower-maps})

(defn clear-ds
  "Delete all data from datasource"
  [ds]
  (sql/query ds ["truncate comments, articles, users cascade"]))

(defmacro with-system
  [[bound-var binding-expr] & body]
  `(let [~bound-var (component/start ~binding-expr)]
     (try
       ~@body
       (finally
         (clear-ds (get-in ~bound-var [:database :datasource]))
         (component/stop ~bound-var)))))

(defn profile-matches-user?
  "Returns true if the profile's fields match that of the user."
  [profile user]
  (let [keys [:username :bio :image]]
    (= (select-keys profile keys) (select-keys user keys))))

(defn create-user
  "Save a test user to the db."
  [db]
  (sql/insert! db :users (mg/generate user/User) query-options))

(defn create-follows
  "Save a fdllowing record to the db."
  [db follower followed]
  (sql/insert! db :follows {:user_id (:id follower) :follows (:id followed)}))

(defn create-article
  "Save a test article to the db."
  [db author-id]
  (let [article (mg/generate article/Article)
        slug (article/str->slug (:title article))]
    (sql/insert! db :articles (assoc article :author author-id :slug slug) update-options)))
