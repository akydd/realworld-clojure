(set! *warn-on-reflection* true)

(ns realworld-clojure.adapters.db
  (:require
   [com.stuartsierra.component :as component]
   [honey.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.date-time :as dt]
   [next.jdbc.optional :as o]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [ragtime.jdbc :as ragtime-jdbc]
   [ragtime.repl :as ragtime-repl]))

(def ^:private query-options
  {:builder-fn o/as-unqualified-lower-maps})

(def ^:private update-options
  {:return-keys true
   :builder-fn rs/as-unqualified-lower-maps})

(defn- handle-psql-exception
  [e]
  (case (.getSQLState e)
    "23505" (throw (ex-info "duplicate record" {:type :duplicate}))
    (throw (ex-info "db error" {:type :unknown :state (.getSQLState e)} e))))

(defn insert-user
  "Insert `user` into `database`."
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
  "Get a user record by `id` from `database`."
  [database id]
  (jdbc/execute-one! (:datasource database)
                     (hsql/format {:select :*
                                   :from :users
                                   :where [:= :id id]})
                     {:builder-fn rs/as-unqualified-kebab-maps}))

(defn get-user-by-email
  "Get a user record by `email` from `database`."
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

(def ^:private article-as-a
  [:articles :a])

(def ^:private users-as-u
  [:users :u])

(def ^:private tags-as-t
  [:tags :t])

(def ^:private article-tags-as-h
  [:article-tags :h])

(def ^:private follows-as-f
  [:follows :f])

(def ^:private comments-as-c
  [:comments :c])

(def ^:private favorites-as-g
  [:favorites :g])

(def ^:private profile-selects
  [:u.username :u.bio :u.image])

(def ^:private following-select
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
  "Get a user profile for `username`."
  ([database username]
   (get-profile database username nil))
  ([database username auth-user]
   (jdbc/execute-one! (:datasource database)
                      (get-profile-query username auth-user)
                      {:builder-fn rs/as-unqualified-kebab-maps})))

(defn get-user-by-username
  "Get a user record by `username`."
  [database username]
  (jdbc/execute-one! (:datasource database)
                     (hsql/format {:select :*
                                   :from :users
                                   :where [:= :username username]})
                     {:builder-fn rs/as-unqualified-kebab-maps}))

(defn update-user
  "Update `auth-user` with `data`."
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
  "Mark `auth-user` as following `user`."
  [database auth-user user]
  (jdbc/execute-one! (:datasource database) (hsql/format {:insert-into :follows
                                                          :values [[(:id auth-user) (:id user)]]
                                                          :on-conflict []
                                                          :do-nothing true})))

(defn unfollow-user
  "Mark `auth-user` as not following `user`."
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

(def ^:private article-selects
  [:a.slug :a.title :a.description :a.body :a.created-at :a.updated-at])

(def ^:private tag-list-select
  [[:array_remove [:array_agg [:order-by :t.tag [:t.tag]]] :null] :tag-list])

(def ^:private favorites-count-select
  [{:select [[[:count :*]]]
    :from [[:favorites :favs]]
    :where [:= :favs.article :a.id]} :favorites-count])

(defn- article-group-by [auth-user]
  (let [group-bys (vec (cons :a.id (concat article-selects profile-selects)))]
    (if (nil? auth-user)
      group-bys
      (conj group-bys :favorited :following))))

(def ^:private favorited-select
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
  "Get an article by `slug`."
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
  "Save new `article`, authored by `auth-user`."
  [database article auth-user]
  (jdbc/with-transaction [tx (:datasource database)]
    (let [tags (distinct (:tag-list article))
          article-without-tags (dissoc article :tag-list)
          saved-article (create-article tx article-without-tags auth-user)]
      (doseq [t tags]
        (let [saved-tag (insert-tag tx t)]
          (link-article-and-tag tx saved-article saved-tag)))))
  ;; This fetch needs to happen after the transaction above has completed.
  (get-article-by-slug database (:slug article) auth-user))

(defn update-article
  "Update article having `slug`."
  [database slug updates auth-user]
  (try (sql/update! (:datasource database)
                    :articles updates {:slug slug} update-options)
       (catch org.postgresql.util.PSQLException e
         (handle-psql-exception e)))
  (get-article-by-slug database (or (:slug updates) slug) auth-user))

(defn delete-article
  "Delete article having `slug`."
  [database slug]
  (jdbc/with-transaction [tx (:datasource database)]
    (when-let [article (first (sql/find-by-keys tx :articles
                                                {:slug slug} query-options))]
      (sql/delete! tx :favorites {:article (:id article)})
      (sql/delete! tx :article_tags {:article (:id article)})
      (sql/delete! tx :comments {:article (:id article)})
      (sql/delete! tx :articles {:id (:id article)}))))

(def ^:private comment-selects
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
  "Get a single comment by `id`."
  [database id auth-user]
  (when-let [c (jdbc/execute-one! (:datasource database)
                                  (get-comment-query auth-user id)
                                  {:builder-fn rs/as-unqualified-kebab-maps})]
    (db-record->model c)))

(defn create-comment
  "Save new comment authored by `auth-user` for article having `slug`."
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
  "Get all comments for article having `slug`."
  ([database slug]
   (get-article-comments database slug nil))
  ([database slug auth-user]
   (when-let [cs (jdbc/execute! (:datasource database)
                                (get-article-comments-query slug auth-user)
                                {:builder-fn rs/as-unqualified-maps})]
     (map db-record->model cs))))

(defn delete-comment
  "Delete comment by `id`."
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

(def ^:private multiple-article-selects
  [:a.slug :a.title :a.description :a.created-at :a.updated-at])

(defn- multiple-article-group-by
  [auth-user]
  (let [group-bys (vec (cons :a.id (concat multiple-article-selects
                                           profile-selects)))]
    (if (nil? auth-user)
      group-bys
      (conj group-bys :favorited :following))))

(defn- join-and-filter-username
  [filters]
  (let [f [:= :a.author :u.id]]
    (if (nil? (:author filters))
      f
      [:and f [:= :u.username (:author filters)]])))

(defn- list-articles-query
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
  "List all artcles, orderd most recently, with `filters` applied.
  Supported filters are `:tag`, `:author`, `:favorited`, `:limit`, `:offset`."
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

(defn- article-feed-query
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
  "Return article feed for `auth-user`, with `filters` applied.
  Supported filters are `:limit` and `:offset`."
  [database filters auth-user]
  (let [articles (jdbc/execute! (:datasource database)
                                (article-feed-query filters auth-user)
                                {:builder-fn rs/as-unqualified-kebab-maps})]
    (->> articles
         (map extract-tags)
         articles->multiple-articles)))

(defn favorite-article
  "Mark article having `slug` as a favorite for `auth-user`."
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
  "Unmark article having `slug` as a favorite for `auth-user`."
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
  "Get all the tags."
  [database]
  (let [tags (jdbc/execute! (:datasource database)
                            (hsql/format {:select :tag
                                          :from :tags
                                          :order-by [:tag]})
                            {:builder-fn rs/as-unqualified-kebab-maps})]
    (map :tag tags)))

(defn migrate
  "Apply all unapplied migrations to the db."
  [database]
  (ragtime-repl/migrate (:migration-config database)))

(defn rollback
  "Rollbacka single db migration."
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

(defn new-database
  "Create a new Database component."
  [dbspec]
  (map->Database {:dbspec dbspec}))
