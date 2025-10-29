(ns realworld-clojure.adapters.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.optional :as o]
            [next.jdbc.date-time :as dt]
            [com.stuartsierra.component :as component]
            [ragtime.repl :as ragtime-repl]
            [ragtime.jdbc :as ragtime-jdbc]
            [java-time.api :as jt]))

(def query-options
  {:builder-fn o/as-unqualified-lower-maps})

(def update-options
  {:return-keys true
   :builder-fn o/as-unqualified-lower-maps})

(defn- handle-psql-exception
  [e]
  (case (.getSQLState e)
    "23505" (throw (ex-info "duplicate record" {:type :duplicate}))
    (throw (ex-info "db error" {:type :unknown :state (.getSQLState e)} e))))

(defn insert-user
  "Insert record into user table"
  [database user]
  (try
    (sql/insert! (:datasource database) :users user update-options)
    (catch org.postgresql.util.PSQLException e
      (handle-psql-exception e))))

(defn get-user
  "Get a user record from user table"
  [database id]
  (sql/get-by-id (:datasource database) :users id query-options))

(defn get-user-by-email
  "Get a user record by email"
  [database email]
  (first (sql/find-by-keys (:datasource database) :users {:email email} query-options)))

(defn get-profile
  "Get a user profile"
  ([database username]
   (jdbc/execute-one! (:datasource database) ["select username, bio, image from users where username = ?" username] query-options))
  ([database username auth-user]
   (jdbc/execute-one! (:datasource database) ["select u.username, u.bio, u.image,
case when f.follows is null then false else true end as following
from users u
left join follows as f
on f.user_id = ? and f.follows = u.id
where u.username = ?", (:id auth-user), username] query-options)))

(defn get-user-by-username
  "Get a user record by username"
  [database username]
  (first (sql/find-by-keys (:datasource database) :users {:username username} query-options)))

(defn update-user
  "Update a user record"
  [database auth-user data]
  (try
    (sql/update! (:datasource database) :users data {:id (:id auth-user)} update-options)
    (catch org.postgresql.util.PSQLException e
      (handle-psql-exception e))))

(defn follow-user
  [database auth-user user]
  (jdbc/execute-one! (:datasource database) ["insert into follows (user_id, follows)
values (?, ?) on conflict do nothing", (:id auth-user) (:id user)]))

(defn unfollow-user
  "Unfollow a user."
  [database auth-user user]
  (sql/delete! (:datasource database) :follows {:user_id (:id auth-user) :follows (:id user)}))

(defn- sqlarray->vec
  [s]
  (vec (.getArray s)))

(defn- extract-tags
  [article]
  (if (empty? (.getArray (:taglist article)))
    (dissoc article :taglist)
    (dissoc (assoc article :tag-list (sqlarray->vec (:taglist article))) :taglist)))

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
a.createdat, a.updatedat,
b.username, b.bio, b.image,
array_remove(array_agg(t.tag order by t.tag), null) as taglist,
(select count(*)
from favorites as f
where f.article = a.id) as favoritescount
from articles as a
inner join users as b
on a.author = b.id
left join article_tags h
on h.article = a.id
left join tags t
on t.id = h.tag
where a.slug = ?
group by a.id, a.slug, a.title, a.description, a.body, a.createdat, a.updatedat, b.username, b.bio, b.image", slug] query-options)]
     (when (seq db-articles) (-> db-articles
                                 (db-record->model)
                                 (extract-tags)))))

  ([database slug auth-user]
   (let [db-articles
         (jdbc/execute-one! (:datasource database) ["select a.slug, a.title, a.description, a.body,
a.createdat, a.updatedat,
b.username, b.bio, b.image,
case when favs.article is not null then true else false end as favorited,
case when g.follows is not null then true else false end as following,
array_remove(array_agg(t.tag order by t.tag), null) as taglist,
(select count(*)
from favorites as f
where f.article = a.id) as favoritescount
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
group by a.id, a.slug, a.title, a.description, a.body, a.createdat, a.updatedat, b.username, b.bio, b.image, favorited, following
", (:id auth-user), (:id auth-user), slug] query-options)]
     (when (seq db-articles) (-> db-articles
                                 (db-record->model)
                                 (extract-tags))))))

(defn- insert-tag
  [tx tag]
  (let [existing-tag
        (jdbc/execute-one! tx ["select * from tags where tag=?" tag] query-options)]
    (if (some? existing-tag)
      existing-tag
      (jdbc/execute-one! tx ["insert into tags (tag) values (?)" tag] update-options))))

(defn- link-article-and-tag [tx article tag]
  (jdbc/execute-one! tx ["insert into article_tags (article, tag) values (?, ?) on conflict do nothing" (:id article) (:id tag)] update-options))

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
  (let [updated-at (jt/local-date-time)]
    (try (sql/update! (:datasource database) :articles (assoc updates :updatedat updated-at) {:slug slug} update-options)
         (catch org.postgresql.util.PSQLException e
           (handle-psql-exception e)))
    (get-article-by-slug database (or (:slug updates) slug) auth-user)))

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
c.createdat, c.updatedat,
c.body,
u.username, u.bio, u.image,
case when f.follows is null then false else true end as following
from comments as c
inner join users as u
on c.author = u.id
left join follows as f
on f.user_id = ? and f.follows = c.author
where c.id = ?", (:id auth-user) id] query-options)]
    (db-record->model c)))

(defn create-comment
  "Add a record to the comments table"
  [database slug comment auth-user]
  (when-let [c (jdbc/execute-one! (:datasource database) ["insert into comments (article, body, author)
select id, ?, ?
from articles
where slug = ?", (:body comment), (:id auth-user), slug] update-options)]
    (get-comment database (:id c) auth-user)))

(defn get-article-comments
  "Get all comments for an article"
  ([database slug]
   (when-let [cs (jdbc/execute! (:datasource database) ["select c.id, c.body,
c.createdat, c.updatedat,
u.username, u.bio, u.image
from articles as a
inner join comments as c
on a.id = c.article
inner join users as u
on c.author = u.id
where a.slug=?", slug] query-options)]
     (map db-record->model cs)))
  ([database slug auth-user]
   (when-let [cs (jdbc/execute! (:datasource database) ["select c.id, c.body,
c.createdat, c.updatedat,
u.username, u.bio, u.image,
case when f.follows is null then false else true end as following
from articles as a
inner join comments as c
on a.id = c.article
inner join users as u
on c.author = u.id
left join follows as f
on f.user_id = ? and f.follows = c.author
where a.slug=?", (:id auth-user), slug] query-options)]
     (map db-record->model cs))))

(defn delete-comment
  "Remove a record from the comment table"
  [database id]
  (sql/delete! (:datasource database) :comments {:id id}))

(defn- articles->multiple-articles
  [articles]
  {:articles (if (nil? articles)
               []
               (map db-record->model articles))
   :articlesCount (if (nil? articles)
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

(defn list-articles
  ([database filters]
   (let [limit (or (:limit filters) 20)
         offset (or (:offset filters) 0)
         articles (jdbc/execute! (:datasource database) [(str "select a.slug, a.title, a.description,
a.createdat, a.updatedat,
b.username, b.bio, b.image,
(select count(*)
from favorites as f
where f.article = a.id) as favoritescount
from articles as a"
                                                              (join-and-filter-user filters)
                                                              (join-and-filter-favorite filters)
                                                              "order by case when a.updatedat is not null then a.updatedat else a.createdat end desc
limit ?
offset ?"), limit, offset] query-options)]
     (articles->multiple-articles articles)))
  ([database filters auth-user]
   (let [limit (or (:limit filters) 20)
         offset (or (:offset filters) 0)
         articles (jdbc/execute! (:datasource database) [(str "select a.slug, a.title, a.description,
a.createdat, a.updatedat,
b.username, b.bio, b.image,
case when g.follows is null then false else true end as following,
case when h.article is null then false else true end as favorited,
(select count(*)
from favorites as f
where f.article = a.id) as favoritescount
from articles as a"
                                                              (join-and-filter-user filters)
                                                              (join-and-filter-favorite filters)
                                                              "left join follows as g
on g.user_id = ? and g.follows = a.author
left join favorites as h
on h.user_id = ? and h.article = a.id
order by case when a.updatedat is not null then a.updatedat else a.createdat end desc
limit ?
offset ?"), (:id auth-user), (:id auth-user), limit, offset] query-options)]
     (articles->multiple-articles articles))))

(defn article-feed
  [database filters auth-user]
  (let [limit (or (:limit filters) 20)
        offset (or (:offset filters) 0)
        articles (jdbc/execute! (:datasource database) ["select a.slug, a.title, a.description,
a.createdat, a.updatedat,
u.username, u.bio, u.image,
array_remove(array_agg(t.tag order by t.tag), null) as taglist,
true as following,
case when g.article is null then false else true end as favorited,
(select count(*)
from favorites as favs
where favs.article = a.id) as favoritescount
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
group by a.slug, a.title, a.description, a.createdat, a.updatedat, u.username, u.bio, u.image,
following, favorited, favoritescount
order by case when a.updatedat is not null then a.updatedat else a.createdat end desc
limit ?
offset ?", (:id auth-user), limit, offset] query-options)]
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
  (let [tags (jdbc/execute! (:datasource database) ["select tag from tags"] query-options)]
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
