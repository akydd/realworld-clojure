(ns realworld-clojure.domain.article
  (:require
   [malli.core :as m]
   [realworld-clojure.adapters.db :as db]
   [malli.error :as me]
   [clojure.string :as str]
   [buddy.auth :refer [throw-unauthorized]]))

(def article-schema
  [:map {:closed true}
   [:title [:string {:min 1}]]
   [:description [:string {:min 1}]]
   [:body [:string {:min 1}]]])

(defrecord ArticleController [database])

(defn str->slug
  "Given a string, return it formatted to a slug"
  [s]
  (-> s
      str/lower-case
      (str/replace #"\W+" "-")))

(defn get-article-by-slug
  "Get an article by slug."
  ([controller slug]
   (db/get-article-by-slug (:database controller) slug))
  ([controller slug auth-user]
   (db/get-article-by-slug (:database controller) slug auth-user)))

(defn create-article
  [controller article auth-user]
  (if (m/validate article-schema article)
    (let [a (assoc article :slug (str->slug (:title article)))]
      (db/create-article (:database controller) a auth-user))
    {:errors (me/humanize (m/explain article-schema article))}))

(def article-update-schema
  [:map {:closed true}
   [:title {:optional true} [:string {:min 1}]]
   [:description {:optional true} [:string {:min 1}]]
   [:body {:optional true} [:string {:min 1}]]])

(defn update-slug
  [updates]
  (if (:title updates)
    (assoc updates :slug (str->slug (:title updates)))
    updates))

(defn update-article
  "Update an article, given its slug."
  [controller slug updates auth-user]
  (if (m/validate article-update-schema updates)
    (when-let [article (db/get-article-by-slug (:database controller) slug)]
      ;; We don't have the author id at this level, but usernames are unique.
      (if (= (get-in article [:author :username]) (:username auth-user))
        (db/update-article (:database controller) slug (update-slug updates) auth-user)
        (throw-unauthorized)))
    {:errors (me/humanize (m/explain article-update-schema updates))}))

(defn delete-article
  "Delete an article, given its slug. Must belong to auth-user."
  [controller slug auth-user]
  (when-let [article (db/get-article-by-slug (:database controller) slug)]
    ;; We don't have the author id at this level, but usernames are unique.
    (if (= (get-in article [:author :username]) (:username auth-user))
      (db/delete-article (:database controller) slug)
      (throw-unauthorized))))

(def list-articles-filter-schema
  [:map {:closed true}
   [:tag {:optional true} [:string {:min 1}]]
   [:author {:optional true} [:string {:min 1}]]
   [:favorited {:optional true} [:string {:min 1}]]
   [:limit {:optional true} [:int {:min 1}]]
   [:offset {:optional true} [:int {:min 0}]]])

(defn list-articles
  ([controller filters]
   (if (m/validate list-articles-filter-schema filters)
     (db/list-articles (:database controller) filters)
     {:errors (me/humanize (m/explain list-articles-filter-schema filters))}))
  ([controller filters auth-user]
   (if (m/validate list-articles-filter-schema filters)
     (db/list-articles (:database controller) filters auth-user)
     {:errors (me/humanize (m/explain list-articles-filter-schema filters))})))

(defn favorite-article
  [controller slug auth-user]
  (db/favorite-article (:database controller) slug auth-user))

(defn unfavorite-aarticle
  [controller slug auth-user]
  (db/unfavorite-article (:database controller) slug auth-user))

(defn new-article-controller []
  (map->ArticleController {}))
