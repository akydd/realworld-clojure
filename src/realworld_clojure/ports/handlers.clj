(ns realworld-clojure.ports.handlers
  (:require [realworld-clojure.domain.user :as user]
            [realworld-clojure.domain.profile :as profile]
            [realworld-clojure.domain.article :as article]
            [realworld-clojure.domain.comment :as comment]
            [realworld-clojure.ports.converters :as converters]
            [clojure.string :as str]))

(defrecord Handler [user-controller profile-controller article-controller comment-controller])

(defn new-handler []
  (map->Handler {}))

(defn health [_handler _req]
  {:status 200
   :body {:status "Alive"}})

;; Handlers that return a user need to first convert the user data before returning it.

(defn get-user
  "Get current user."
  [handler auth-user headers]
  (let [token (second (str/split (get headers "authorization") #" "))
        u (user/get-user (:user-controller handler) auth-user token)]
    (if (u :errors)
      {:status 422
       :body u}
      {:status 200
       :body {:user (converters/user->user u)}})))

(defn register-user
  "Register a user"
  [handler user]
  (let [u (user/register-user (:user-controller handler) user)]
    (if (u :errors)
      {:status 422
       :body u}
      {:status 200
       :body {:user (converters/user->user u)}})))

(defn login-user
  "Login a user"
  [handler user]
  (let [u (user/login-user (:user-controller handler) user)]
    (if (nil? u)
      {:status 403}
      (if (:errors u)
        {:status 422
         :body u}
        {:status 200
         :body {:user (converters/user->user u)}}))))

(defn update-user
  "Update a user"
  [handler auth-user user]
  (let [u (user/update-user (:user-controller handler) auth-user user)]
    (if (nil? u)
      {:status 404}
      (if (:errors u)
        {:status 422
         :body u}
        {:status 200
         :body {:user (converters/user->user u)}}))))

;; Handlers that returna  profile do not need to strip the id from the profile body,
;; because the id is not included in the profiles when retrieved from the db.

(defn get-profile
  "Get a user profile by username"
  [handler username auth-user]
  (let [p (if (nil? auth-user)
            (profile/get-profile (:profile-controller handler) username)
            (profile/get-profile (:profile-controller handler) username auth-user))]
    (if (nil? p)
      {:status 404}
      {:status 200
       :body {:profile p}})))

(defn follow-user
  "Follow a user"
  [handler auth-user username]
  (let [p (profile/follow-user (:profile-controller handler) auth-user username)]
    (if (nil? p)
      {:status 404}
      {:status 200
       :body {:profile p}})))

(defn unfollow-user
  "Follow a user"
  [handler auth-user username]
  (let [p (profile/unfollow-user (:profile-controller handler) auth-user username)]
    (if (nil? p)
      {:status 404}
      {:status 200
       :body {:profile p}})))

(defn get-article-by-slug
  "Get article by slug."
  [handler slug auth-user]
  (let [a (if (nil? auth-user)
            (article/get-article-by-slug (:article-controller handler) slug)
            (article/get-article-by-slug (:article-controller handler) slug auth-user))]
    (if (nil? a)
      {:status 404}
      {:status 200
       :body {:article a}})))

(defn- params->filters
  [params]
  (let [ks [:limit :offset]
        to-int (select-keys params ks)
        converted-ints (reduce (fn [acc [k v]] (assoc acc k (Integer/parseInt v))) {} to-int)]
    (merge params converted-ints)))

(defn list-articles
  "Get list of articles, filtered"
  [handler params auth-user]
  (let [filters (params->filters params)
        articles (if (nil? auth-user)
                   (article/list-articles (:article-controller handler) filters)
                   (article/list-articles (:article-controller handler) filters auth-user))]
    {:status 200
     :body {:articles articles}}))

(defn article-feed
  [handler parms auth-user])

(defn create-article
  "Create an article"
  [handler article auth-user]
  (let [a (article/create-article (:article-controller handler) article auth-user)]
    (if (nil? a)
      {:status 404}
      (if (:errors a)
        {:status 422
         :body a}
        {:status 200
         :body {:article a}}))))

(defn update-article
  "Update an article"
  [handler slug article auth-user]
  (let [a (article/update-article (:article-controller handler) slug article auth-user)]
    (if (nil? a)
      {:status 404}
      (if (:errors a)
        {:status 422
         :body a}
        {:status 200
         :body {:article a}}))))

(defn delete-article
  "Delete an article"
  [handler slug auth-user]
  (let [a (article/delete-article (:article-controller handler) slug auth-user)]
    (if (nil? a)
      {:status 404}
      {:status 200})))

(defn favorite-article
  "Mark article as a favorite for auth-user."
  [handler slug auth-user]
  (let [a (article/favorite-article (:article-controller handler) slug auth-user)]
    (if (nil? a)
      {:status 404}
      {:status 200
       :body {:article a}})))

(defn unfavorite-article
  "Unfavorite an article for the auth-user."
  [handler slug auth-user]
  (let [a (article/unfavorite-aarticle (:article-controller handler) slug auth-user)]
    (if (nil? a)
      {:status 400}
      {:status 200
       :body {:article a}})))

(defn create-comment
  "Create a new comment for an article"
  [handler slug comment auth-user]
  (let [c (comment/create-comment (:comment-controller handler) slug comment auth-user)]
    (if (nil? c)
      {:status 404}
      (if (:errors c)
        {:status 422
         :body c}
        {:status 200
         :body {:comment c}}))))

(defn get-comments
  "Get comments for an article"
  [handler slug auth-user]
  (let [comments (if (nil? auth-user)
                   (comment/get-article-comments (:article-controller handler) slug)
                   (comment/get-article-comments (:article-controller handler) slug auth-user))]
    (if (nil? comments)
      {:status 404}
      {:status 200
       :body {:comments comments}})))

(defn delete-comment
  "Delete comment"
  [handler slug id auth-user]
  (let [p (comment/delete-comment (:article-controller handler) slug id auth-user)]
    (if (nil? p)
      {:status 404}
      {:status 200})))
