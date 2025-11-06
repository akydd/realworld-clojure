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

(defn- get-profile-query
  [username auth-user]
  (-> (h/select :u.username :u.bio :u.image)
      (cond-> auth-user (h/select [[:case [:is :f.follows nil] false
                                    :else true
                                    :end] :following]))
      (h/from [:users :u])
      (cond-> auth-user (h/left-join [:follows :f]
                                     [:and
                                      [:= :f.follows :u.id]
                                      [:= :f.user-id (:id auth-user)]]))
      (h/where [:= :u.username username])))

(defn get-profile
  "Get a user profile"
  ([database username]
   (get-profile database username nil))
  ([database username auth-user]
   (jdbc/execute-one! (:datasource database)
                      (hsql/format (get-profile-query username auth-user))
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

(defn get-article-by-slug
  "Get an article by slug."
  ([database slug]
   (let [db-articles
         (jdbc/execute-one! (:datasource database) ["select a.slug, a.title, a.description, a.body,
a.created_at, a.updated_at,
b.username, b.bio, b.image,
array_remove(array_agg(t.tag order by t.tag), null) as tag_list,
(select count(*)
from favorites as f
where f.article = a.id) as favorites_count
from articles as a
inner join users as b
on a.author = b.id
left join article_tags h
on h.article = a.id
left join tags t
on t.id = h.tag
where a.slug = ?
group by a.id, a.slug, a.title, a.description, a.body, a.created_at, a.updated_at, b.username, b.bio, b.image", slug] {:builder-fn rs/as-unqualified-kebab-maps})]
     (when (seq db-articles) (-> db-articles
                                 (db-record->model)
                                 (extract-tags)))))

  ([database slug auth-user]
   (let [db-articles
         (jdbc/execute-one! (:datasource database) ["select a.slug, a.title, a.description, a.body,
a.created_at, a.updated_at,
b.username, b.bio, b.image,
case when favs.article is not null then true else false end as favorited,
case when g.follows is not null then true else false end as following,
array_remove(array_agg(t.tag order by t.tag), null) as tag_list,
(select count(*)
from favorites as f
where f.article = a.id) as favorites_count
from articles as a
inner join users as b
on a.author = b.id
left join favorites as favs
on favs.user_id = ? and favs.article = a.id
left join follows as g
on g.user_id = ? and g.follows = a.author
left join article_tags h
on h.article = a.id
left join tags t
on t.id = h.tag
where a.slug = ?
group by a.id, a.slug, a.title, a.description, a.body, a.created_at, a.updated_at, b.username, b.bio, b.image, favorited, following
", (:id auth-user), (:id auth-user), slug] {:builder-fn rs/as-unqualified-kebab-maps})]
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
  (try (sql/update! (:datasource database) :articles updates {:slug slug} update-options)
       (catch org.postgresql.util.PSQLException e
         (handle-psql-exception e)))
  (get-article-by-slug database (or (:slug updates) slug) auth-user))

(defn delete-article
  "Delete a record from the articles table"
  [database slug]
  (jdbc/with-transaction [tx (:datasource database)]
    (when-let [article (first (sql/find-by-keys tx :articles {:slug slug} query-options))]
      (sql/delete! tx :favorites {:article (:id article)})
      (sql/delete! tx :article_tags {:article (:id article)})
      (sql/delete! tx :comments {:article (:id article)})
      (sql/delete! tx :articles {:id (:id article)}))))

(defn get-comment
  "Get a single comment by id."
  [database id auth-user]
  (when-let [c (jdbc/execute-one! (:datasource database) ["select c.id,
c.created_at, c.updated_at,
c.body,
u.username, u.bio, u.image,
case when f.follows is null then false else true end as following
from comments as c
inner join users as u
on c.author = u.id
left join follows as f
on f.user_id = ? and f.follows = c.author
where c.id = ?", (:id auth-user) id] {:builder-fn rs/as-unqualified-kebab-maps})]
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

(defn get-article-comments
  "Get all comments for an article"
  ([database slug]
   (when-let [cs (jdbc/execute! (:datasource database) ["select c.id, c.body,
c.created_at, c.updated_at,
u.username, u.bio, u.image
from articles as a
inner join comments as c
on a.id = c.article
inner join users as u
on c.author = u.id
where a.slug=? order by c.id", slug] {:builder-fn rs/as-unqualified-maps})]
     (map db-record->model cs)))
  ([database slug auth-user]
   (when-let [cs (jdbc/execute! (:datasource database) ["select c.id, c.body,
c.created_at, c.updated_at,
u.username, u.bio, u.image,
case when f.follows is null then false else true end as following
from articles as a
inner join comments as c
on a.id = c.article
inner join users as u
on c.author = u.id
left join follows as f
on f.user_id = ? and f.follows = c.author
where a.slug=? order by c.id", (:id auth-user), slug] {:builder-fn rs/as-unqualified-maps})]
     (map db-record->model cs))))

(defn delete-comment
  "Remove a record from the comment table"
  [database id]
  (sql/delete! (:datasource database) :comments {:id id}))

(defn- articles->multiple-articles
  [articles]
  {:articles (if-not (seq articles)
               []
               (map db-record->model articles))
   :articlesCount (if-not (seq articles)
                    0
                    (count articles))})

(defn- join-and-filter-user
  [filters]
  (str " inner join users as b on a.author = b.id "
       (when (:author filters)
         (str " and b.username='" (:author filters) "' "))))

(defn- join-and-filter-favorite
  [filters]
  (when (:favorited filters)
    (str " inner join favorites as i on i.article = a.id inner join users as j on j.username='" (:favorited filters) "' and i.user_id = j.id ")))

(defn- join-tags []
  " left join article_tags as s on a.id = s.article left join tags as t on t.id = s.tag ")

(defn- filter-tag [filters]
  (when (:tag filters)
    (str " having '" (:tag filters) "' = ANY(array_agg(t.tag)) ")))

(defn- list-articles-sql-no-auth
  [filters]
  (str
   "select a.slug, a.title, a.description, a.created_at, a.updated_at, "
   " array_remove(array_agg(t.tag order by t.tag), null) as tag_list, "
   " b.username, b.bio, b.image,"
   " (select count(*)"
   " from favorites as f"
   " where f.article = a.id) as favorites_count"
   " from articles as a "
   (join-tags)
   (join-and-filter-user filters)
   (join-and-filter-favorite filters)
   " group by a.id, a.slug, a.title, a.description, a.created_at, a.updated_at, b.username, b.bio, b.image "
   (filter-tag filters)
   " order by a.updated_at desc "
   " limit ? offset ?"))

(defn- list-articles-sql-with-auth [filters]
  (str
   "select a.slug, a.title, a.description, a.created_at, a.updated_at,"
   " array_remove(array_agg(t.tag order by t.tag), null) as tag_list,"
   " b.username, b.bio, b.image,"
   " case when g.follows is null then false else true end as following,"
   " case when h.article is null then false else true end as favorited,"
   " (select count(*) "
   " from favorites as f"
   " where f.article = a.id) as favorites_count"
   " from articles as a"
   (join-tags)
   (join-and-filter-user filters)
   (join-and-filter-favorite filters)
   " left join follows as g on g.user_id = ? and g.follows = a.author"
   " left join favorites as h on h.user_id = ? and h.article = a.id"
   " group by a.id, a.slug, a.title, a.description, a.created_at, a.updated_at, b.username, b.bio, b.image, following, favorited"
   (filter-tag filters)
   " order by a.updated_at desc"
   " limit ? offset ?"))

(defn list-articles
  ([database filters]
   (let [limit (or (:limit filters) 20)
         offset (or (:offset filters) 0)
         articles (jdbc/execute!
                   (:datasource database)
                   [(list-articles-sql-no-auth filters) limit offset]
                   {:builder-fn rs/as-unqualified-kebab-maps})]
     (->> articles
          (map extract-tags)
          articles->multiple-articles)))
  ([database filters auth-user]
   (let [limit (or (:limit filters) 20)
         offset (or (:offset filters) 0)
         articles (jdbc/execute!
                   (:datasource database)
                   [(list-articles-sql-with-auth filters) (:id auth-user) (:id auth-user) limit offset]
                   {:builder-fn rs/as-unqualified-kebab-maps})]
     (->> articles
          (map extract-tags)
          articles->multiple-articles))))

(defn article-feed
  [database filters auth-user]
  (let [limit (or (:limit filters) 20)
        offset (or (:offset filters) 0)
        articles (jdbc/execute! (:datasource database) ["select a.slug, a.title, a.description,
a.created_at, a.updated_at,
u.username, u.bio, u.image,
array_remove(array_agg(t.tag order by t.tag), null) as tag_list,
true as following,
case when g.article is null then false else true end as favorited,
(select count(*)
from favorites as favs
where favs.article = a.id) as favorites_count
from follows as f
inner join articles as a
on a.author = f.follows
inner join users as u
on a.author = u.id
left join favorites as g
on g.user_id = f.user_id and g.article = a.id
left join article_tags h
on h.article = a.id
left join tags t
on t.id = h.tag
where f.user_id = ?
group by a.id, a.slug, a.title, a.description, a.created_at, a.updated_at, u.username, u.bio, u.image,
following, favorited
order by a.updated_at desc
limit ?
offset ?", (:id auth-user), limit, offset] {:builder-fn rs/as-unqualified-kebab-maps})]
    (->> articles
         (map extract-tags)
         articles->multiple-articles)))

(defn favorite-article
  [database slug auth-user]
  (try
    (jdbc/execute-one! (:datasource database) ["insert into favorites (user_id, article)
select ?, id from articles
where slug = ?", (:id auth-user), slug] update-options)
    (catch org.postgresql.util.PSQLException e
      (handle-psql-exception e)))
  (get-article-by-slug database slug auth-user))

(defn unfavorite-article
  [database slug auth-user]
  (jdbc/execute-one! (:datasource database) ["delete from favorites
where user_id = ? and article in
(select id from articles
where slug = ?)" , (:id auth-user), slug])
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
