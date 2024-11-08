(ns realworld-clojure.ports.handlers
  (:require [realworld-clojure.domain.user :as user]
            [realworld-clojure.domain.profile :as profile]))

(defrecord Handler [user-controller profile-controller])

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

(defn get-user
  "get a user"
  [handler id]
  (let [u (user/get-user (:user-controller handler) id)]
    (if (nil? u)
      {:status 404}
      {:status 200
       :body {:user (clean-user u)}})))

(defn update-user
  "Update a user"
  [handler id user]
  (let [u (user/update-user (:user-controller handler) id user)]
    (if (nil? u)
      {:status 404}
      (if (:errors u)
        {:status 422
         :body u}
        {:status 200
         :body {:user (clean-user u)}}))))

(defn get-profile
  "Get a user profile by username"
  [handler username id]
  (let [p (if (nil? id)
            (profile/get-profile (:profile-controller handler) username)
            (profile/get-profile (:profile-controller handler) username id))]
    (if (nil? p)
      {:status 404}
      {:status 200
       :body {:profile (clean-profile p)}})))

(defn follow-user
  "Follow a user"
  [handler id username]
  (let [p (profile/follow-user (:profile-controller handler) id username)]
    (if (nil? p)
      {:status 404}
      {:status 200
       :body {:profile (clean-profile p)}})))

(defn unfollow-user
  "Follow a user"
  [handler id username]
  (let [p (profile/unfollow-user (:profile-controller handler) id username)]
    (if (nil? p)
      {:status 404}
      {:status 200
       :body {:profile (clean-profile p)}})))
