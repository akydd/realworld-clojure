(ns realworld-clojure.ports.handlers
  (:require [realworld-clojure.domain.user :as user]
            [realworld-clojure.domain.profile :as profile]
            [realworld-clojure.domain.article :as article]))

(defrecord Handler [user-controller profile-controller article-controller])

(defn new-handler []
  (map->Handler {}))

(defn health [_handler _req]
  {:status 200
   :body {:status "Alive"}})

(defn clean-user
  "Format the user object before returning it to web based api responses"
  [user]
  (dissoc user :id :password))

(defn clean-profile
  "Format a user object into a profile before returning it in web based api responses"
  [profile]
  (dissoc profile :id))

(defn clean-article
  "Format the article object before returning it in web based api responses"
  [article]
  (dissoc article :id))

(defn register-user
  "Register a user"
  [handler user]
  (let [u (user/register-user (:user-controller handler) user)]
    (if (u :errors)
      {:status 422
       :body u}
      {:status 200
       :body {:user (clean-user u)}})))

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
         :body {:user (clean-user u)}}))))

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
         :body {:user (clean-user u)}}))))

(defn get-profile
  "Get a user profile by username"
  [handler username auth-user]
  (let [p (if (nil? auth-user)
            (profile/get-profile (:profile-controller handler) username)
            (profile/get-profile (:profile-controller handler) username auth-user))]
    (if (nil? p)
      {:status 404}
      {:status 200
       :body {:profile (clean-profile p)}})))

(defn follow-user
  "Follow a user"
  [handler auth-user username]
  (let [p (profile/follow-user (:profile-controller handler) auth-user username)]
    (if (nil? p)
      {:status 404}
      {:status 200
       :body {:profile (clean-profile p)}})))

(defn unfollow-user
  "Follow a user"
  [handler auth-user username]
  (let [p (profile/unfollow-user (:profile-controller handler) auth-user username)]
    (if (nil? p)
      {:status 404}
      {:status 200
       :body {:profile (clean-profile p)}})))

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
