(ns realworld-clojure.adapters.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.optional :as o]
            [next.jdbc.result-set :as rs]
            [next.jdbc.date-time :as dt]
            [com.stuartsierra.component :as component]
            [ragtime.repl :as ragtime-repl]
            [ragtime.jdbc :as ragtime-jdbc]
            [honey.sql :as hsql]
            [honey.sql.helpers :as h]))

(def query-options
  {:builder-fn o/as-unqualified-lower-maps})

(def update-options
  {:return-keys true
   :builder-fn rs/as-unqualified-lower-maps})

(defn- handle-psql-exception
  [e]
  (case (.getSQLState e)
    "23505" (throw (ex-info "duplicate record" {:type :duplicate}))
    (throw (ex-info "db error" {:type :unknown :state (.getSQLState e)} e))))

(defn insert-user
  [database user]
  (try
    (jdbc/execute-one! (:datasource database)
                       (hsql/format {:insert-into :users
                                     :values [user]
                                     :returning :*})
                       {:builder-fn rs/as-unqualified-kebab-maps})
    (catch org.postgresql.util.PSQLException e
      (handle-psql-exception e))))

(defn get-user
  "Get a user record from user table"
  [database id]
  (jdbc/execute-one! (:datasource database)
                     (hsql/format {:select :*
                                   :from :users
                                   :where [:= :id id]})
                     {:builder-fn rs/as-unqualified-kebab-maps}))

(defn get-user-by-email
  "Get a user record by email"
  [database email]
  (jdbc/execute-one! {:datasource database}
                     (hsql/format {:select :*
                                   :from :users
                                   :where [:= :email email]})
                     {:builder-fn rs/as-unqualified-kebab-maps}))

;; Table aliasing used throughout:
;; article a
;; users u
;; tags t
;; article-tags h
;; follows f
;; comments c
;; favorites g, or favs if within a subquery

(def article-as-a
  [:articles :a])

(def users-as-u
  [:users :u])

(def tags-as-t
  [:tags :t])

(def article-tags-as-h
  [:article-tags :h])

(def follows-as-f
  [:follows :f])

(def comments-as-c
  [:comments :c])

(def favorites-as-g
  [:favorites :g])

(def profile-selects
  [:u.username :u.bio :u.image])

(def following-select
  [[:case [:is :f.follows nil] false
    :else true
    :end] :following])

(defn- get-profile-query
  [username auth-user]
  (-> (apply h/select profile-selects)
      (cond-> auth-user (h/select following-select))
      (h/from users-as-u)
      (cond-> auth-user (h/left-join follows-as-f
                                     [:and
                                      [:= :f.follows :u.id]
                                      [:= :f.user-id (:id auth-user)]]))
      (h/where [:= :u.username username])
      (hsql/format)))

(defn get-profile
  "Get a user profile"
  ([database username]
   (get-profile database username nil))
  ([database username auth-user]
   (jdbc/execute-one! (:datasource database)
                      (get-profile-query username auth-user)
                      {:builder-fn rs/as-unqualified-kebab-maps})))

(defn get-user-by-username
  "Get a user record by username"
  [database username]
  (jdbc/execute-one! (:datasource database)
                     (hsql/format {:select :*
                                   :from :users
                                   :where [:= :username username]})
                     {:builder-fn rs/as-unqualified-kebab-maps}))

(defn update-user
  "Update a user record"
  [database auth-user data]
  (try
    (jdbc/execute-one! (:datasource database)
                       (hsql/format {:update :users
                                     :set data
                                     :where [:= :id (:id auth-user)]
                                     :returning :*})
                       {:builder-fn rs/as-unqualified-kebab-maps})
    (catch org.postgresql.util.PSQLException e
      (handle-psql-exception e))))

(defn follow-user
  [database auth-user user]
  (jdbc/execute-one! (:datasource database)
                     (hsql/format {:insert-into :follows
                                   :values [[(:id auth-user) (:id user)]]
                                   :on-conflict []
                                   :do-nothing true})))

(defn unfollow-user
  "Unfollow a user."
  [database auth-user user]
  (jdbc/execute-one! (:datasource database)
                     (hsql/format {:delete-from :follows
                                   :where [:and
                                           [:= :user-id (:id auth-user)]
                                           [:= :follows (:id user)]]})))

(defn- sqlarray->vec
  [s]
  (vec (.getArray s)))

(defn- extract-tags
  [article]
  (update article :tag-list sqlarray->vec))

(defn- db-record->model
  [m]
  (let [ks (if (contains? m :following)
             [:following :username :bio :image]
             [:username :bio :image])
        author (select-keys m ks)]
    (assoc (reduce dissoc m ks) :author author)))

(def article-selects
  [:a.slug :a.title :a.description :a.body :a.created-at :a.updated-at])

(def tag-list-select
  [[:array_remove [:array_agg [:order-by :t.tag [:t.tag]]] :null] :tag-list])

(def favorites-count-select
  [{:select [[[:count :*]]]
    :from [[:favorites :favs]]
    :where [:= :favs.article :a.id]} :favorites-count])

(defn- article-group-by [auth-user]
  (let [group-by (vec (cons :a.id (concat article-selects profile-selects)))]
    (if (nil? auth-user)
      group-by
      (conj group-by :favorited :following))))

(def favorited-select
  [[:case [:is :g.article nil] false
    :else true
    :end] :favorited])

(defn- get-article-by-slug-query
  [slug auth-user]
  (-> (apply h/select (concat article-selects profile-selects))
      (cond-> auth-user (h/select favorited-select))
      (cond-> auth-user (h/select following-select))
      (h/select tag-list-select)
      (h/select favorites-count-select)
      (h/from article-as-a)
      (h/join-by :inner [users-as-u [:= :a.author :u.id]]
                 :left [article-tags-as-h [:= :h.article :a.id]]
                 :left [tags-as-t [:= :t.id :h.tag]])
      (cond-> auth-user (h/left-join
                         favorites-as-g [:and
                                         [:= :g.user-id (:id auth-user)]
                                         [:= :g.article :a.id]]))
      (cond-> auth-user (h/left-join
                         follows-as-f [:and
                                       [:= :f.user-id (:id auth-user)]
                                       [:= :f.follows :a.author]]))
      (h/where [:= :a.slug slug])
      (merge (apply h/group-by (article-group-by auth-user)))
      (hsql/format)))

(defn get-article-by-slug
  "Get an article by slug."
  ([database slug]
   (get-article-by-slug database slug nil))
  ([database slug auth-user]
   (let [db-articles
         (jdbc/execute-one! (:datasource database)
                            (get-article-by-slug-query slug auth-user)
                            {:builder-fn rs/as-unqualified-kebab-maps})]
     (when (seq db-articles) (-> db-articles
                                 (db-record->model)
                                 (extract-tags))))))

(defn- insert-tag
  [tx tag]
  (let [existing-tag
        (jdbc/execute-one! tx
                           ["select * from tags where tag=?" tag]
                           query-options)]
    (if (some? existing-tag)
      existing-tag
      (jdbc/execute-one! tx
                         ["insert into tags (tag) values (?)" tag]
                         update-options))))

(defn- link-article-and-tag [tx article tag]
  (jdbc/execute-one! tx
                     (hsql/format {:insert-into :article-tags
                                   :values [[(:id article) (:id tag)]]
                                   :on-conflict []
                                   :do-nothing true})
                     update-options))

(defn- create-article
  "Save sn article to the db. This does not handle tags.
  To save an article and tags, use create-article-with-tags."
  [tx article auth-user]
  (let [a (-> article
              (assoc :author (:id auth-user))
              (dissoc :tag-list))]
    (try
      (sql/insert! tx :articles a update-options)
      (catch org.postgresql.util.PSQLException e
        (handle-psql-exception e)))))

(defn create-article-with-tags
  [database article-with-tags auth-user]
  (jdbc/with-transaction [tx (:datasource database)]
    (let [tags (distinct (:tag-list article-with-tags))
          article (dissoc article-with-tags :tag-list)
          saved-article (create-article tx article auth-user)]
      (doseq [t tags]
        (let [saved-tag (insert-tag tx t)]
          (link-article-and-tag tx saved-article saved-tag)))))
  ;; This fetch needs to happen after the transaction above has completed.
  (get-article-by-slug database (:slug article-with-tags) auth-user))

(defn update-article
  "Update a record in the articles table"
  [database slug updates auth-user]
  (try (sql/update! (:datasource database)
                    :articles updates {:slug slug} update-options)
       (catch org.postgresql.util.PSQLException e
         (handle-psql-exception e)))
  (get-article-by-slug database (or (:slug updates) slug) auth-user))

(defn delete-article
  "Delete a record from the articles table"
  [database slug]
  (jdbc/with-transaction [tx (:datasource database)]
    (when-let [article (first (sql/find-by-keys tx :articles
                                                {:slug slug} query-options))]
      (sql/delete! tx :favorites {:article (:id article)})
      (sql/delete! tx :article_tags {:article (:id article)})
      (sql/delete! tx :comments {:article (:id article)})
      (sql/delete! tx :articles {:id (:id article)}))))

(def comment-selects
  [:c.id :c.created-at :c.updated-at :c.body])

(defn- get-comment-query [auth-user id]
  (-> (apply h/select (concat comment-selects profile-selects))
      (h/select following-select)
      (h/from comments-as-c)
      (h/left-join follows-as-f
                   [:and
                    [:= :f.user-id (:id auth-user)]
                    [:= :f.follows :c.author]])
      (h/inner-join users-as-u
                    [:= :c.author :u.id])
      (h/where [:= :c.id id])
      (hsql/format)))

(defn get-comment
  "Get a single comment by id."
  [database id auth-user]
  (when-let [c (jdbc/execute-one! (:datasource database)
                                  (get-comment-query auth-user id)
                                  {:builder-fn rs/as-unqualified-kebab-maps})]
    (db-record->model c)))

(defn create-comment
  "Add a record to the comments table"
  [database slug c auth-user]
  (when-let [c (jdbc/execute-one!
                (:datasource database)
                (hsql/format {:insert-into :comments
                              :columns [:article :body :author]
                              :values [[{:select [:id]
                                         :from :articles
                                         :where [:= :slug slug]}
                                        (:body c)
                                        (:id auth-user)]]})
                update-options)]
    (get-comment database (:id c) auth-user)))

(defn- get-article-comments-query
  [slug auth-user]
  (-> (apply h/select (concat comment-selects profile-selects))
      (cond-> auth-user (h/select following-select))
      (h/from article-as-a)
      ;; Used join-by because the joins had to be applied in a certain order.
      (h/join-by :inner [comments-as-c [:= :a.id :c.article]]
                 :inner [users-as-u [:= :c.author :u.id]])
      (cond-> auth-user (h/left-join follows-as-f
                                     [:and
                                      [:= :f.user-id (:id auth-user)]
                                      [:= :f.follows :c.author]]))
      (h/where [:= :a.slug slug])
      (h/order-by :c.id)
      (hsql/format)))

(defn get-article-comments
  "Get all comments for an article"
  ([database slug]
   (get-article-comments database slug nil))
  ([database slug auth-user]
   (when-let [cs (jdbc/execute! (:datasource database)
                                (get-article-comments-query slug auth-user)
                                {:builder-fn rs/as-unqualified-maps})]
     (map db-record->model cs))))

(defn delete-comment
  "Remove a record from the comment table"
  [database id]
  (jdbc/execute-one! (:datasource database) (hsql/format {:delete-from :comments
                                                          :where [:= :id id]})))

(defn- articles->multiple-articles
  [articles]
  {:articles (if-not (seq articles)
               []
               (map db-record->model articles))
   :articlesCount (if-not (seq articles)
                    0
                    (count articles))})

(def multiple-article-selects
  [:a.slug :a.title :a.description :a.created-at :a.updated-at])

(defn multiple-article-group-by [auth-user]
  (let [group-by (vec (cons :a.id (concat multiple-article-selects
                                          profile-selects)))]
    (if (nil? auth-user)
      group-by
      (conj group-by :favorited :following))))

(defn join-and-filter-username
  [filters]
  (let [f [:= :a.author :u.id]]
    (if (nil? (:author filters))
      f
      [:and f [:= :u.username (:author filters)]])))

(defn list-articles-query
  [filters auth-user]
  (-> (apply h/select (concat multiple-article-selects profile-selects))
      (cond-> auth-user (h/select favorited-select))
      (cond-> auth-user (h/select following-select))
      (h/select tag-list-select)
      (h/select favorites-count-select)
      (h/from article-as-a)
      (h/left-join article-tags-as-h [:= :h.article :a.id])
      (h/left-join tags-as-t [:= :t.id :h.tag])
      (h/inner-join users-as-u (join-and-filter-username filters))
      (cond-> (some? (:favorited filters))
        (h/join-by :inner [[:favorites :i] [:= :i.article :a.id]]
                   :inner [[:users :j] [[:and
                                         [:= :j.username (:favorited filters)]
                                         [:= :j.id :i.user-id]]]]))
      (cond-> auth-user
        (h/join-by :left [follows-as-f [:and
                                        [:= :f.user-id (:id auth-user)]
                                        [:= :f.follows :a.author]]]
                   :left [favorites-as-g [:and
                                          [:= :g.user-id (:id auth-user)]
                                          [:= :g.article :a.id]]]))
      (merge (apply h/group-by (multiple-article-group-by auth-user)))
      (cond-> (some? (:tag filters))
        (h/having [:= (:tag filters) [:any [:array_agg :t.tag]]]))
      (h/order-by [:a.updated-at :desc])
      (h/limit (or (:limit filters) 20))
      (h/offset (or (:offset filters) 0))
      (hsql/format)))

(defn list-articles
  ([database filters]
   (list-articles database filters nil))
  ([database filters auth-user]
   (let [articles (jdbc/execute!
                   (:datasource database)
                   (list-articles-query filters auth-user)
                   {:builder-fn rs/as-unqualified-kebab-maps})]
     (->> articles
          (map extract-tags)
          articles->multiple-articles))))

(def ^:private multi-article-group-by
  (conj (vec (cons :a.id (concat multiple-article-selects
                                 profile-selects))) :following :favorited))

(defn article-feed-query
  [filters auth-user]
  (-> (apply h/select (conj (concat multiple-article-selects profile-selects)
                            [[:inline true] :following]))
      (h/select tag-list-select)
      (h/select favorited-select)
      (h/select favorites-count-select)
      (h/from follows-as-f)
      (h/join-by :inner [article-as-a [:= :a.author :f.follows]]
                 :inner [users-as-u [:= :a.author :u.id]])
      (h/left-join [:favorites :g]
                   [:and
                    [:= :g.user-id :f.user-id]
                    [:= :g.article :a.id]])
      (h/left-join [:article-tags :h] [:= :h.article :a.id])
      (h/left-join [:tags :t] [:= :t.id :h.tag])
      (h/where [:= :f.user-id (:id auth-user)])
      (merge (apply h/group-by multi-article-group-by))
      (h/order-by [:a.updated-at :desc])
      (h/limit (or (:limit filters) 20))
      (h/offset (or (:offset filters) 0))
      (hsql/format)))

(defn article-feed
  [database filters auth-user]
  (let [articles (jdbc/execute! (:datasource database)
                                (article-feed-query filters auth-user)
                                {:builder-fn rs/as-unqualified-kebab-maps})]
    (->> articles
         (map extract-tags)
         articles->multiple-articles)))

(defn favorite-article
  [database slug auth-user]
  (try
    (jdbc/execute-one!
     (:datasource database)
     (hsql/format `{:insert-into
                    (:favorites {:select [[[:inline ~(:id auth-user)]] :id]
                                 :from :articles
                                 :where [:= :slug ~slug]})})
     update-options)
    (catch org.postgresql.util.PSQLException e
      (handle-psql-exception e)))
  (get-article-by-slug database slug auth-user))

(defn unfavorite-article
  [database slug auth-user]
  (jdbc/execute-one! (:datasource database)
                     (hsql/format {:delete-from :favorites
                                   :where [:and
                                           [:= :user-id (:id auth-user)]
                                           [:in :article
                                            {:select [:id]
                                             :from :articles
                                             :where [:= :slug slug]}]]}))
  (get-article-by-slug database slug auth-user))

(defn get-tags
  [database]
  (let [tags (jdbc/execute! (:datasource database)
                            (hsql/format {:select :tag
                                          :from :tags
                                          :order-by [:tag]})
                            {:builder-fn rs/as-unqualified-kebab-maps})]
    (map :tag tags)))

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
    (dt/read-as-instant)
    (let [ds (jdbc/get-datasource dbspec)
          migration-config {:datastore (ragtime-jdbc/sql-database dbspec)
                            :migrations (ragtime-jdbc/load-resources
                                         "migrations")}]
      (ragtime-repl/migrate migration-config)
      (assoc component
             :datasource ds
             :migration-config migration-config)))

  (stop [component]
    (println "Stopping database")
    (assoc component :datasource nil)))

(defn new-database [dbspec]
  (map->Database {:dbspec dbspec}))
