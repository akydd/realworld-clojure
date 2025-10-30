(ns realworld-clojure.integration.common
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [is]]
   [realworld-clojure.config-test :as config]
   [org.httpkit.client :as http]
   [clojure.string :as str]
   [java-time.api :as jt]))

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

(defn list-articles-request
  ([filter-str]
   @(http/get (str base-url "/articles" filter-str) {:headers (get-headers)}))
  ([filter-str token]
   @(http/get (str base-url "/articles" filter-str) {:headers (get-headers token)})))

(defn get-tags-request
  []
  @(http/get (str base-url "/tags") {:headers (get-headers)}))

;; helper comparison functions

(defn keys-match?
  [a b ks]
  (doseq [k ks]
    (is (= (k a) (k b)) (str "Expected " (k a) " but got " (k b)))))

(defn profiles-equal?
  [a b]
  (is (= (:username a) (:username b)) "usernames do not match")
  (is (= (:bio a) (:bio b)) "bios do not match")
  (is (= (:image a) (:image b)) "images do not match"))

(defn validate-article-vs-input
  [article input]
  (is (= (:title article) (:title input)) "titles do not match")
  (is (= (:description article) (:description input)) "descriptions do not match")
  (is (= (:body article) (:body input)) "bodies do not match")
  (is (= (:tag-list article) (seq (sort (distinct (:tag-list input))))) "tag lists do not match"))

(defn instance->str [i]
  (-> i
      (jt/truncate-to :millis)
      (.toString)))

(defn articles-match-feed?
  [test-cases]
  (doseq [{:keys [article author feed follows]} test-cases]
    (let [;; article's timestamps, returned from the db, are javaa.sql.Timestamps.
            ;; But the timestamps in feed, returned from parsing the json, are strings.
            ;; To compare them, convert article's timestamps to strings.
          expected-created-at (instance->str (:createdat article))
          expected-updated-at (when (some? (:updatedat article))
                                (instance->str (:updatedat article)))]

      (is (= (:title article) (:title feed)) "titles do not match")
      (is (= (:description article) (:description feed)) "descriptions do not match")
      (is (= (:slug article) (:slug feed)) "slugs do not match")
      (is (= (:tag-list article) (seq (sort (distinct (:tag-list feed))))) "tag lists do not match")
      (is (= expected-created-at (:createdat feed)) "createdats do not match")
      (when (some? (:updatedat article))
        (is (= expected-updated-at (:updatedat feed)) "updatedats do not match"))
      (profiles-equal? author (:author feed))
      (when (some? follows)
        (is (= follows (get-in feed [:author :following])) "following does not match")))))

(defn article-matches-article?
  [a author b]
  (let [article-ks [:title :body :description :slug :tag-list]
        ;; article's timestamps, returned from the db, are javaa.sql.Timestamps.
        ;; But the timestamps in feed, returned from parsing the json, are strings.
        ;; To compare them, convert article's timestamps to strings.
        expected-created-at (instance->str (:createdat a))
        expected-updated-at (when (some? (:updatedat a))
                              (instance->str (:updatedat a)))]
    (and
     (keys-match? a b article-ks)
     (is (= expected-created-at (:createdat b)))
     (when (some? (:updatedat b))
       (is (= expected-updated-at (:updatedat b))))
     (profiles-equal? author (:author b))
     (is (true? (get-in b [:author :following]))))))

(defn validate-slug
  [article]
  (is (= (:slug article) (-> (:title article)
                             str/lower-case
                             (str/replace #"W+" "-"))) "slug is incorrect"))

;; Response schemas, used for validation.

(def user-response-schema
  [:map {:closed true}
   [:username [:string {:min 1}]]
   [:token [:string {:min 1}]]
   [:email [:string {:min 1}]]
   [:bio [:maybe :string]]
   [:image [:maybe :string]]])

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
   [:author #'no-auth-profile-schema]
   [:tag-list {:optional true} [:vector {:min 1} :string]]])

(def no-auth-article-feed-schema
  [:map {:closed true}
   [:slug [:string {:min 1}]]
   [:title [:string {:min 1}]]
   [:description [:string {:min 1}]]
   [:createdat [:string {:min 1}]]
   [:updatedat {:optional true} [:string {:min 1}]]
   [:favoritescount [:int]]
   [:author #'no-auth-profile-schema]
   [:tag-list {:optional true} [:vector {:min 1} :string]]])

(def multiple-no-auth-article-schema
  [:map {:closed true}
   [:articles [:vector {:min 0} #'no-auth-article-feed-schema]]
   [:articlesCount [:int {:min 0}]]])

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
   [:author #'auth-profile-schema]
   [:tag-list {:optional true} [:vector {:min 1} :string]]])

(def auth-article-feed-schema
  [:map {:closed true}
   [:slug [:string {:min 1}]]
   [:title [:string {:min 1}]]
   [:description [:string {:min 1}]]
   [:createdat [:string {:min 1}]]
   [:updatedat {:optional true} [:string {:min 1}]]
   [:favorited [:boolean]]
   [:favoritescount [:int]]
   [:author #'auth-profile-schema]
   [:tag-list {:optional true} [:vector {:min 1} :string]]])

(def multiple-auth-article-schema
  [:map {:closed true}
   [:articles [:vector {:min 0} #'auth-article-feed-schema]]
   [:articlesCount [:int {:min 0}]]])

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

(def tags-schema
  [:map {:closed  true}
   [:tags [:vector {:min 0} :string]]])
