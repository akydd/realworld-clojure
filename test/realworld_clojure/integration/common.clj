(ns realworld-clojure.integration.common
  (:require
   [cheshire.core :as json]
   [realworld-clojure.config-test :as config]
   [org.httpkit.client :as http]
   [clojure.string :as str]))

(def port (get-in (config/read-test-config) [:server :port]))
(def host "http://localhost")
(def base-url (str host ":" port "/api"))

(defn- get-headers
  ([]
   {"Content-Type" "application/json"})
  ([token]
   {"Content-Type" "application/json"
    "Authorization" (str "Token " token)}))

;; Testing endpoints for all operations.

(defn get-user-request
  ([]
   @(http/get (str base-url "/user") {:headers (get-headers)}))
  ([token]
   @(http/get (str base-url "/user") {:headers (get-headers token)})))

(defn get-profile-request
  ([username]
   @(http/get (str base-url "/profiles/" username) {:headers (get-headers)}))
  ([username token]
   @(http/get (str base-url "/profiles/" username) {:headers (get-headers token)})))

(defn login-request
  ([user]
   @(http/post (str base-url "/users/login") {:headers (get-headers)
                                              :body (json/generate-string {:user (select-keys user [:email :password])})})))

(defn get-login-token
  [user]
  (let [r (login-request user)]
    (get-in (json/parse-string (:body r) true) [:user :token])))

(defn update-user-request
  ([user]
   @(http/put (str base-url "/user") {:headers (get-headers)
                                      :body (json/generate-string {:user user})}))
  ([user token]
   @(http/put (str base-url "/user") {:headers (get-headers token)
                                      :body (json/generate-string {:user user})})))

(defn register-request
  [user]
  @(http/post (str base-url "/users") {:headers (get-headers)
                                       :body (json/generate-string {:user (select-keys user [:username :password :email])})}))

(defn follow-user-request
  ([username]
   @(http/post (str base-url "/profiles/" username "/follow") {:headers (get-headers)}))
  ([username token]
   @(http/post (str base-url "/profiles/" username "/follow") {:headers (get-headers token)})))

(defn unfollow-user-request
  ([username]
   @(http/delete (str base-url "/profiles/" username "/follow") {:headers (get-headers)}))
  ([username token]
   @(http/delete (str base-url "/profiles/" username "/follow") {:headers (get-headers token)})))

(defn get-article-request
  ([slug]
   @(http/get (str base-url "/articles/" slug) {:headers (get-headers)}))
  ([slug token]
   @(http/get (str base-url "/articles/" slug) {:headers (get-headers token)})))

(defn create-article-request
  ([article]
   @(http/post (str base-url "/articles") {:headers (get-headers)
                                           :body (json/generate-string {:article article})}))
  ([article token]
   @(http/post (str base-url "/articles") {:headers (get-headers token)
                                           :body (json/generate-string {:article article})})))

(defn update-article-request
  ([slug update]
   @(http/put (str base-url "/articles/" slug) {:headers (get-headers)
                                                :body (json/generate-string {:article update})}))
  ([slug update token]
   @(http/put (str base-url "/articles/" slug) {:headers (get-headers token)
                                                :body (json/generate-string {:article update})})))

(defn delete-article-request
  ([slug]
   @(http/delete (str base-url "/articles/" slug) {:headers (get-headers)}))
  ([slug token]
   @(http/delete (str base-url "/articles/" slug) {:headers (get-headers token)})))

(defn create-comment-request
  ([slug comment]
   @(http/post (str base-url "/articles/" slug "/comments") {:headers (get-headers)
                                                             :body (json/generate-string {:comment comment})}))
  ([slug comment token]
   @(http/post (str base-url "/articles/" slug "/comments") {:headers (get-headers token)
                                                             :body (json/generate-string {:comment comment})})))

(defn get-comments-request
  ([slug]
   @(http/get (str base-url "/articles/" slug "/comments") {:headers (get-headers)}))
  ([slug token]
   @(http/get (str base-url "/articles/" slug "/comments") {:headers (get-headers token)})))

(defn delete-comment-request
  ([slug id]
   @(http/delete (str base-url "/articles/" slug "/comments/" id) {:headers (get-headers)}))
  ([slug id token]
   @(http/delete (str base-url "/articles/" slug "/comments/" id) {:headers (get-headers token)})))

(defn favorite-article-request
  ([slug]
   @(http/post (str base-url "/articles/" slug "/favorite") {:headers (get-headers)}))
  ([slug token]
   @(http/post (str base-url "/articles/" slug "/favorite") {:headers (get-headers token)})))

(defn unfavorite-article-request
  ([slug]
   @(http/delete (str base-url "/articles/" slug "/favorite") {:headers (get-headers)}))
  ([slug token]
   @(http/delete (str base-url "/articles/" slug "/favorite") {:headers (get-headers token)})))

(defn article-feed-request
  ([filter-str]
   @(http/get (str base-url "/articles/feed" filter-str) {:headers (get-headers)}))
  ([filter-str token]
   @(http/get (str base-url "/articles/feed" filter-str) {:headers (get-headers token)})))

;; helper comparison functions

(defn keys-match?
  [a b ks]
  (= (select-keys a ks) (select-keys b ks)))

(defn profiles-equal?
  [a b]
  (let [ks [:username :bio :image]]
    (keys-match? a b ks)))

(defn article-matches-input?
  [article input]
  (let [ks [:title :description :body]]
    (keys-match? article input ks)))

(defn article-matches-feed?
  [article feed]
  (let [ks [:title :description :createat :updatedat]]
    (keys-match? article feed ks)))

(defn slug-is-correct?
  [article]
  (= (:slug article) (-> (:title article)
                         str/lower-case
                         (str/replace #"W+" "-"))))

;; Response schemas, used for validation.

(def user-response-schema
  [:map {:closed true}
   [:username [:string {:min 1}]]
   [:token [:string {:min 1}]]
   [:email [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]])

(def no-auth-profile-schema
  [:map {:closed true}
   [:username [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]])

(def auth-profile-schema
  [:map {:closed true}
   [:username [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]
   [:following [:boolean]]])

(def no-auth-article-schema
  [:map {:closed true}
   [:slug [:string {:min 1}]]
   [:title [:string {:min 1}]]
   [:description [:string {:min 1}]]
   [:body [:string {:min 1}]]
   [:createdat [:string {:min 1}]]
   [:updatedat {:optional true} [:string {:min 1}]]
   [:favoritescount [:int]]
   [:author #'no-auth-profile-schema]])

(def auth-article-schema
  [:map {:closed true}
   [:slug [:string {:min 1}]]
   [:title [:string {:min 1}]]
   [:description [:string {:min 1}]]
   [:body [:string {:min 1}]]
   [:createdat [:string {:min 1}]]
   [:updatedat {:optional true} [:string {:min 1}]]
   [:favorited [:boolean]]
   [:favoritescount [:int]]
   [:author #'auth-profile-schema]])

(def no-auth-comment-schema
  [:map {:closed true}
   [:id :int]
   [:createdat [:string {:min 1}]]
   [:updatedat [:string {:min 1}]]
   [:body [:string {:min 1}]]
   [:author #'no-auth-profile-schema]])

(def auth-comment-schema
  [:map {:closed true}
   [:id :int]
   [:createdat [:string {:min 1}]]
   [:updatedat [:string {:min 1}]]
   [:body [:string {:min 1}]]
   [:author #'auth-profile-schema]])

(def article-feed-schema
  [:map {:closed true}
   [:slug [:string {:min 1}]]
   [:title [:string {:min 1}]]
   [:description [:string {:min 1}]]
   [:createdat [:string {:min 1}]]
   [:updatedat {:optional true} [:string {:min 1}]]
   [:favorited [:boolean]]
   [:favoritescount [:int {:min 0}]]
   [:author #'auth-profile-schema]])
