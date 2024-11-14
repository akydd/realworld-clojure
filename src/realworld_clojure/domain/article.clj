(ns realworld-clojure.domain.article
  (:require
   [malli.core :as m]
   [realworld-clojure.adapters.db :as db]
   [malli.error :as me]
   [java-time.api :as jt]
   [clojure.string :as string]
   [buddy.auth :refer [throw-unauthorized]]))

(def Article
  [:map
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

(defn create-article
  [controller article auth-user]
  (if (m/validate Article article)
    (let [title (:title article)
          article-to-save (->
                           article
                           (assoc :author (:id auth-user)
                                  :slug (text-to-slug title)
                                  :createdAt (jt/local-date-time)))
          saved-article (db/create-article (:database controller) article-to-save)
          author (db/get-user (:database controller) auth-user)]
      (assoc saved-article :author author :follows false))
    {:errors (me/humanize (m/explain Article article))}))

(def ArticleUpdate
  [:map
   [:title {:optional true} [:string {:min 1}]]
   [:description {:optional true} [:string {:min 1}]]
   [:body {:optional true} [:string {:min 1}]]])

(defn update-article
  "Update an article, given its slug."
  [controller slug article-update auth-user]
  (if (m/validate ArticleUpdate article-update)
    (when-let [article (db/get-article-by-slug (:database controller) slug)]
      (if (= (:author article) (:id auth-user))
        (db/update-article (:database controller) (:id article) (assoc article-update :updatedAt (jt/local-date-time)))
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
