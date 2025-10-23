(ns realworld-clojure.utils
  (:require
   [com.stuartsierra.component :as component]
   [next.jdbc.optional :as o]
   [realworld-clojure.domain.user :as user]
   [malli.generator :as mg]
   [next.jdbc.sql :as sql]
   [realworld-clojure.domain.article :as article]
   [buddy.hashers :as hashers]
   [realworld-clojure.domain.comment :as comment]
   [next.jdbc :as jdbc]))

(def update-options
  {:return-keys true
   :builder-fn o/as-unqualified-lower-maps})

(defn clear-ds
  "Delete all data from datasource"
  [ds]
  (sql/query ds ["truncate favorites, follows, comments, articles, tags, article_tags, users cascade"]))

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

(def query-options
  {:builder-fn o/as-unqualified-lower-maps})

(defn insert-tag
  [db tag]
  (try
    (jdbc/execute-one! db ["insert into tags (tag) values (?)" tag] update-options)
    (catch org.postgresql.util.PSQLException e
      (if (= "23505" (.getSQLState e))
        (jdbc/execute-one! db ["select * from tags where tag=?" tag] query-options)
        (throw (ex-info "db error" {:type :unknown :state (.getSQLState e)} e))))))

(defn link-article-and-tag [db article tag]
  (jdbc/execute-one! db ["insert into article_tags (article, tag) values (?, ?) on conflict do nothing" (:id article) (:id tag)] update-options))

(defn create-article
  "Save a test article to the db.
  This takes 3 steps. 1) save the article,
  2) save the tags, 3) link articles and tags."
  ([db author-id]
   (let [input (mg/generate article/article-schema)
         tags (:tag-list input)
         slug (article/str->slug (:title input))
         article (-> input
                     (assoc
                      :author author-id
                      :slug slug)
                     (dissoc :tag-list))
         saved-article (sql/insert! db :articles article update-options)]
     (doseq [t tags]
       (let [saved-tag (insert-tag db t)]
         (link-article-and-tag db saved-article saved-tag)))
     (assoc saved-article :tag-list tags)))
  ([db author-id options]
   (let [input (mg/generate article/article-schema)
         tags (:tag-list input)
         slug (article/str->slug (:title input))
         article (-> input
                     (assoc
                      :slug slug
                      :author author-id)
                     (merge options)
                     (dissoc :tag-list))
         saved-article (sql/insert! db :articles article update-options)]
     (doseq [t tags]
       (let [saved-tag (insert-tag db t)]
         (link-article-and-tag db saved-article saved-tag)))
     (assoc saved-article :tag-list tags))))

(defn create-article-in-tx
  [db author-id]
  (jdbc/with-transaction [tx db]
    (create-article tx author-id)))

(defn create-comment
  [db article-id author-id]
  (let [comment (mg/generate comment/comment-create-schema)]
    (sql/insert! db :comments (assoc comment :author author-id :article article-id) update-options)))

(defn fav-article
  [db user article]
  (sql/insert! db :favorites {:user_id (:id user) :article (:id article)}))
