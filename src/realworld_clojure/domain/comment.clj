(ns realworld-clojure.domain.comment
  (:require
   [malli.error :as me]
   [malli.core :as m]
   [realworld-clojure.adapters.db :as db]
   [buddy.auth :refer [throw-unauthorized]]))

(defrecord CommentController [database])

(def comment-create-schema
  [:map
   [:body [:string {:min 1}]]])

(defn create-comment
  "Add a new comment to an article."
  [controller slug comment auth-user]
  (if (m/validate comment-create-schema comment)
    (when (db/get-article-by-slug (:database controller) slug)
      (db/create-comment (:database controller) slug comment auth-user))
    {:errors (me/humanize (m/explain comment-create-schema comment))}))

(defn delete-comment
  "Delete a comment"
  [controller _article-slug id auth-user]
  (let [c (db/get-comment (:database controller) id auth-user)]
    (if (= (:username auth-user) (get-in c [:author :username]))
      (db/delete-comment (:database controller) id)
      (throw-unauthorized))))

(defn get-article-comments
  "Get all comments for an article"
  ([controller slug]
   (db/get-article-comments (:database controller) slug))
  ([controller slug auth-user]
   (db/get-article-comments (:database controller) slug auth-user)))

(defn new-comment-controller []
  (map->CommentController {}))
