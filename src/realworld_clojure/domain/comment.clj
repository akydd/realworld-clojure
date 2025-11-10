(ns realworld-clojure.domain.comment
  (:require
   [buddy.auth :refer [throw-unauthorized]]
   [malli.core :as m]
   [malli.error :as me]
   [realworld-clojure.adapters.db :as db]))

(defrecord CommentController [database])

(def comment-create-schema
  "Schema for the `c` param to [[create-comment]]."
  [:map
   [:body [:string {:min 1}]]])

(defn create-comment
  "Add a new comment to an article."
  [controller slug c auth-user]
  (if (m/validate comment-create-schema c)
    (when (db/get-article-by-slug (:database controller) slug)
      (db/create-comment (:database controller) slug c auth-user))
    {:errors (me/humanize (m/explain comment-create-schema c))}))

(defn delete-comment
  "Delete comment with `id` for article having `slug`."
  [controller slug id auth-user]
  (when (db/get-article-by-slug (:database controller) slug)
    (when-let [c (db/get-comment (:database controller) id auth-user)]
      (if (= (:username auth-user) (get-in c [:author :username]))
        (db/delete-comment (:database controller) id)
        (throw-unauthorized)))))

(defn get-article-comments
  "Get all comments for article with `slug`."
  ([controller slug]
   (when (db/get-article-by-slug (:database controller) slug)
     (db/get-article-comments (:database controller) slug)))
  ([controller slug auth-user]
   (when (db/get-article-by-slug (:database controller) slug)
     (db/get-article-comments (:database controller) slug auth-user))))

(defn new-comment-controller
  "Create a new CommentControoler."
  []
  (map->CommentController {}))
