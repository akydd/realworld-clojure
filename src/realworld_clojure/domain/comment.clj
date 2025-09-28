(ns realworld-clojure.domain.comment
  (:require
   [malli.error :as me]
   [malli.core :as m]
   [realworld-clojure.adapters.db :as db]))

(defrecord CommentController [database])

(def CommentCreate
  [:map
   [:body [:string {:min 1}]]])

(defn create-comment
  "Add a new comment to an article."
  [controller slug comment auth-user]
  (if (m/validate CommentCreate comment)
    (when (db/get-article-by-slug (:database controller) slug)
      (db/create-comment (:database controller) slug comment auth-user))
    {:errors (me/humanize (m/explain CommentCreate comment))}))

(defn delete-comment
  "Delete a comment"
  [controller article-slug comment-id auth-user])

(defn get-comments-for-article
  "Get all comments for an article"
  ([controller article-slug])
  ([controller article-slug auth-user]))

(defn new-comment-controller []
  (map->CommentController {}))
