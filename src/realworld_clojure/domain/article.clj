(ns realworld-clojure.domain.article
  (:require
   [malli.core :as m]
   [realworld-clojure.adapters.db :as db]
   [realworld-clojure.domain.converters :as c]
   [malli.error :as me]
   [clojure.string :as string]
   [buddy.auth :refer [throw-unauthorized]]))

(def Article
  [:map {:closed true}
   [:title [:string {:min 1}]]
   [:description [:string {:min 1}]]
   [:body [:string {:min 1}]]])

(defrecord ArticleController [database])

(defn text-to-slug
  "Given a string, return it formatted to a slug"
  [title]
  (-> title
      string/lower-case
      (string/replace #"\W+" "-")))

(defn- author->profile [author following]
  (-> author
      (c/user-db->profile)
      (assoc :following following)))

(defn create-article
  [controller article auth-user]
  (if (m/validate Article article)
    (let [title (:title article)
          article-to-save (assoc article :author (:id auth-user)
                                 :slug (text-to-slug title))
          saved-article (db/create-article (:database controller) article-to-save)
          author (db/get-user (:database controller) (:id auth-user))
          following (db/following? (:database controller) auth-user author)
          author-profile (author->profile author following)]
      (assoc saved-article :author author-profile))
    {:errors (me/humanize (m/explain Article article))}))

(def ArticleUpdate
  [:map {:closed true}
   [:title {:optional true} [:string {:min 1}]]
   [:description {:optional true} [:string {:min 1}]]
   [:body {:optional true} [:string {:min 1}]]])

(defn update-title [article-update]
  (if (:title article-update)
    (assoc article-update :slug (text-to-slug (:title article-update)))
    article-update))

(defn update-article
  "Update an article, given its slug."
  [controller slug article-update auth-user]
  (if (m/validate ArticleUpdate article-update)
    (when-let [article (db/get-article-by-slug (:database controller) slug)]
      (if (= (:author article) (:id auth-user))
        (let [author (db/get-user (:database controller) (:author article))
              updated-article (-> article-update
                                  (update-title)
                                  (db/update-article (:database controller) (:id article)))
              following (db/following? (:database controller) auth-user author)
              author-profile (author->profile author following)]
          (assoc updated-article :author author-profile))
        (throw-unauthorized)))
    {:errors (me/humanize (m/explain ArticleUpdate article-update))}))

(defn delete-article
  "Delete an article, given its slug. Must belong to auth-user."
  [controller slug auth-user]
  (when-let [article (db/get-article-by-slug (:database controller) slug)]
    (if (= (:author article) (:id auth-user))
      (db/delete-article (:database controller) (:id article))
      (throw-unauthorized))))

(defn new-article-controller []
  (map->ArticleController {}))
