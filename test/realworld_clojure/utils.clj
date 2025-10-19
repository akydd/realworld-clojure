(ns realworld-clojure.utils
  (:require
   [com.stuartsierra.component :as component]
   [next.jdbc.optional :as o]
   [realworld-clojure.domain.user :as user]
   [malli.generator :as mg]
   [next.jdbc.sql :as sql]
   [realworld-clojure.domain.article :as article]
   [buddy.hashers :as hashers]
   [realworld-clojure.domain.comment :as comment]))

(def query-options
  {:builder-fn o/as-unqualified-lower-maps})

(def update-options
  {:return-keys true
   :builder-fn o/as-unqualified-lower-maps})

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

(defn comments-are-equal?
  [a b]
  (let [keys [:id :createdat :updatedat :body]]
    (= (select-keys keys a) (select-keys keys b))))

(defn create-user
  "Save a test user to the db. Returns the user with an unhashed password, for testing."
  [db]
  (let [user (mg/generate user/user-schema)
        password (hashers/derive (:password user))
        new-user (sql/insert! db :users (assoc user :password password) update-options)]
    (assoc new-user :password (:password user))))

(defn create-follows
  "Save a fdllowing record to the db."
  [db follower followed]
  (sql/insert! db :follows {:user_id (:id follower) :follows (:id followed)}))

(defn create-article
  "Save a test article to the db."
  ([db author-id]
   (let [input (mg/generate article/article-schema)
         slug (article/str->slug (:title input))
         article (assoc input
                        :author author-id
                        :slug slug)]
     (sql/insert! db :articles article update-options)))
  ([db author-id options]
   (let [input (mg/generate article/article-schema)
         slug (article/str->slug (:title input))
         article (-> input
                     (assoc
                      :slug slug
                      :author author-id)
                     (merge options))]
     (sql/insert! db :articles article update-options))))

(defn create-comment
  [db article-id author-id]
  (let [comment (mg/generate comment/comment-create-schema)]
    (sql/insert! db :comments (assoc comment :author author-id :article article-id) update-options)))
