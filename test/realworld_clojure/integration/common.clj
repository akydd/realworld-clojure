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
  ([]
   @(http/post (str base-url "/users/login") {:headers (get-headers)
                                              :body (json/generate-string {})}))
  ([email password]
   @(http/post (str base-url "/users/login") {:headers (get-headers)
                                              :body (json/generate-string {:user {:email email
                                                                                  :password password}})})))

(defn get-login-token
  [user]
  (let [r (login-request (:email user) (:password user))]
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

(defn profiles-equal?
  [expected actual]
  (is (= (:username expected) (:username actual)) "usernames do not match")
  (is (= (:bio expected) (:bio actual)) "bios do not match")
  (is (= (:image expected) (:image actual)) "images do not match"))

(defn instance->str [i]
  (-> i
      (jt/truncate-to :millis)
      (.toString)))

(defn assert-comment-matches
  ([expected actual author follows]
   (let [expected-created-at (instance->str (:created-at expected))
         expected-updated-at (instance->str (:updated-at expected))]
     (is (= (:id expected) (:id actual)) "ids do not match")
     (is (= expected-created-at (:createdAt actual)) "created-ats do not match")
     (is (= expected-updated-at (:updatedAt actual)) "updated-ats do not match")
     (is (= (:body expected) (:body actual)) "bodies do not match")
     (profiles-equal? author (:author actual))
     (when (some? follows)
       (is (= follows (get-in actual [:author :following])) "follows does not match")))))

(defn assert-comments-match
  [test-cases]
  (doseq [{:keys [expected actual author follows]} test-cases]
    (assert-comment-matches expected actual author follows)))

(defn validate-article-vs-input
  [article input]
  (is (= (:title article) (:title input)) "titles do not match")
  (is (= (:description article) (:description input)) "descriptions do not match")
  (is (= (:body article) (:body input)) "bodies do not match")
  (if (nil? (:tag-list input))
    (is (= [] (:tagList article)) "tagList should be []")
    (is (= (:tagList article) (sort (distinct (:tag-list input)))) "tag lists do not match")))

(defn- assert-article-matches-feed [article feed author follows]
  (let [;; article's timestamps, returned from the db, are javaa.sql.Timestamps.
          ;; But the timestamps in feed, returned from parsing the json, are strings.
          ;; To compare them, convert article's timestamps to strings.
        expected-created-at (instance->str (:created-at article))
        expected-updated-at (instance->str (:updated-at article))]
    (is (= (:title article) (:title feed)) "titles do not match")
    (is (= (:description article) (:description feed)) "descriptions do not match")
    (is (= (:slug article) (:slug feed)) "slugs do not match")
    (if (nil? (:tag-list article))
      (is (= [] (:tagList feed)) "tagList should be empty")
      (is (= (:tag-list article) (:tagList feed)) "tag lists do not match"))
    (is (= expected-created-at (:createdAt feed)) "createdats do not match")
    (is (= expected-updated-at (:updatedAt feed)) "updatedats do not match")
    (profiles-equal? author (:author feed))
    (when (some? follows)
      (is (= follows (get-in feed [:author :following])) "following does not match"))))

(defn articles-match-feed?
  [test-cases]
  (doseq [{:keys [article author feed follows]} test-cases]
    (assert-article-matches-feed article feed author follows)))

(defn article-matches-article?
  ([expected-article author article-from-json]
   (let [;; article's timestamps, returned from the db, are javaa.sql.Timestamps.
         ;; But the timestamps in feed, returned from parsing the json, are strings.
         ;; To compare them, convert article's timestamps to strings.
         expected-created-at (instance->str (:created-at expected-article))
         expected-updated-at (instance->str (:updated-at expected-article))]
     (is (= (:title expected-article) (:title article-from-json)) "titles do not match")
     (is (= (:body expected-article) (:body article-from-json)) "bodies do not match")
     (is (= (:description expected-article) (:description article-from-json)) "descriptions do not match")
     (is (= (:slug expected-article) (:slug article-from-json)) "slugs do not match")
     (if (nil? (:tag-list expected-article))
       (is (= [] (:tagList article-from-json)) "tagList should be empty")
       (is (= (:tag-list expected-article) (:tagList article-from-json)) "tag lists do not match"))
     (is (= expected-created-at (:createdAt article-from-json)) "created-ats do not match")
     (is (= expected-updated-at (:updatedAt article-from-json)) "updated-ats do not match")
     (profiles-equal? author (:author article-from-json))))
  ([expected-article author article-from-json follows]
   (article-matches-article? expected-article author article-from-json)
   (is (= follows (get-in article-from-json [:author :following])) "follows does not match")))

(defn validate-slug
  [article]
  (is (= (:slug article) (-> (:title article)
                             str/lower-case
                             (str/replace #"W+" "-"))) "slug is incorrect"))

;; Response schemas, used for validation.
;; Note that the keywords here are in camelCase, to reflect the requirement that the
;; fieldnames returned in the json body are also camelCase.

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
   [:bio [:maybe :string]]
   [:image [:maybe :string]]])

(def auth-profile-schema
  [:map {:closed true}
   [:username [:string {:min 1}]]
   [:bio [:maybe :string]]
   [:image [:maybe :string]]
   [:following [:boolean]]])

(def no-auth-article-schema
  [:map {:closed true}
   [:slug [:string {:min 1}]]
   [:title [:string {:min 1}]]
   [:description [:string {:min 1}]]
   [:body [:string {:min 1}]]
   [:createdAt [:string {:min 1}]]
   [:updatedAt [:string {:min 1}]]
   [:favoritesCount [:int]]
   [:author #'no-auth-profile-schema]
   [:tagList [:maybe [:vector :string]]]])

(def no-auth-article-feed-schema
  [:map {:closed true}
   [:slug [:string {:min 1}]]
   [:title [:string {:min 1}]]
   [:description [:string {:min 1}]]
   [:createdAt [:string {:min 1}]]
   [:updatedAt [:maybe :string]]
   [:favoritesCount [:int]]
   [:author #'no-auth-profile-schema]
   [:tagList [:maybe [:vector :string]]]])

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
   [:createdAt [:string {:min 1}]]
   [:updatedAt [:string {:min 1}]]
   [:favorited [:boolean]]
   [:favoritesCount [:int]]
   [:author #'auth-profile-schema]
   [:tagList [:maybe [:vector :string]]]])

(def auth-article-feed-schema
  [:map {:closed true}
   [:slug [:string {:min 1}]]
   [:title [:string {:min 1}]]
   [:description [:string {:min 1}]]
   [:createdAt [:string {:min 1}]]
   [:updatedAt [:maybe :string]]
   [:favorited [:boolean]]
   [:favoritesCount [:int]]
   [:author #'auth-profile-schema]
   [:tagList [:maybe [:vector :string]]]])

(def multiple-auth-article-schema
  [:map {:closed true}
   [:articles [:vector {:min 0} #'auth-article-feed-schema]]
   [:articlesCount [:int {:min 0}]]])

(def no-auth-comment-schema
  [:map {:closed true}
   [:id :int]
   [:createdAt [:string {:min 1}]]
   [:updatedAt [:string {:min 1}]]
   [:body [:string {:min 1}]]
   [:author #'no-auth-profile-schema]])

(def auth-comment-schema
  [:map {:closed true}
   [:id :int]
   [:createdAt [:string {:min 1}]]
   [:updatedAt [:string {:min 1}]]
   [:body [:string {:min 1}]]
   [:author #'auth-profile-schema]])

(def tags-schema
  [:map {:closed  true}
   [:tags [:vector {:min 0} :string]]])
