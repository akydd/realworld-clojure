(ns realworld-clojure.domain.article
  (:require
   [malli.core :as m]
   [realworld-clojure.adapters.db :as db]
   [malli.error :as me]
   [java-time.api :as jt]))

(def Article
  [:map
   [:title [:string {:min 1}]]
   [:description [:string {:min 1}]]
   [:body [:string {:min 1}]]])

(defrecord ArticleController [database])

(defn description-to-slug
  "Given a string, return it formatted to a slug"
  [description]
  description)

(defn create-article
  [controller article id]
  (if (m/validate Article article)
    (let [description (:description article)
          article-to-save (->
                           article
                           (assoc :author id 
                                  :slug (description-to-slug description)
                                  :createdAt (jt/local-date-time)))]
      (db/create-article (:database controller) article-to-save))
    {:errors (me/humanize (m/explain Article article))}))

(defn new-article-controller []
  (map->ArticleController {}))
