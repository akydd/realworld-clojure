(ns realworld-clojure.domain.comment
  (:require
   [malli.error :as me]
   [malli.core :as m]
   [realworld-clojure.domain.converters :as c]
   [realworld-clojure.adapters.db :as db]))

(defrecord CommentController [database])

(def CommentCreate
  [:map
   [:body [:string {:min 1}]]])

(defn add-comment-to-article
  "Add a new comment to an article."
  [controller comment article-slug auth-user]
  (if (m/validate CommentCreate comment)
    (when-let [article (db/get-article-by-slug (:database controller) article-slug)]
      (let [comment-to-save (assoc comment :article (:id article) :author (:id auth-user))
            saved-comment (db/create-comment (:database controller) comment-to-save)]
        (when saved-comment
          (assoc saved-comment :author (c/user-db->profile auth-user)))))
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
