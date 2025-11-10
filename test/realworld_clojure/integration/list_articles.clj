(ns realworld-clojure.integration.list-articles
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [java-time.api :as jt]
   [malli.core :as m]
   [malli.error :as me]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.core :as core]
   [realworld-clojure.integration.common :refer [list-articles-request
                                                 multiple-no-auth-article-schema
                                                 multiple-auth-article-schema
                                                 articles-match-feed?
                                                 get-login-token]]
   [realworld-clojure.utils :as test-utils]))

(deftest no-articles-no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (list-articles-request "")
          body (-> r
                   (:body)
                   (json/parse-string true))]
      (is (= 200 (:status r)))
      (is (true? (m/validate multiple-no-auth-article-schema body))
          (->> body
               (m/explain multiple-no-auth-article-schema)
               (me/humanize)))
      (is (zero? (:articlesCount body)))
      (is (empty? (:articles body))))))

(deftest no-articles-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          r (list-articles-request "" (get-login-token user))
          body (-> r
                   (:body)
                   (json/parse-string true))]
      (is (= 200 (:status r)))
      (is (true? (m/validate multiple-no-auth-article-schema body))
          (->> body
               (m/explain multiple-no-auth-article-schema)
               (me/humanize)))
      (is (zero? (:articlesCount body)))
      (is (empty? (:articles body))))))

(defn- validate-response
  ([response expected-articles expected-authors]
   (let [body (-> response
                  (:body)
                  (json/parse-string true))]
     (is (= 200 (:status response)))
     (is (true? (m/validate multiple-no-auth-article-schema body))
         (->> body
              (m/explain multiple-no-auth-article-schema)
              (me/humanize)))
     (is (= (count expected-articles) (:articlesCount body)))
     (articles-match-feed? (map (fn [a b c] {:article a
                                             :author b
                                             :feed c})
                                expected-articles expected-authors
                                (:articles body)))))
  ([response expected-articles expected-authors expected-follows]
   (let [body (-> response
                  (:body)
                  (json/parse-string true))]
     (is (= 200 (:status response)))
     (is (true? (m/validate multiple-auth-article-schema body))
         (->> body
              (m/explain multiple-auth-article-schema)
              (me/humanize)))
     (is (= (count expected-articles) (:articlesCount body)))
     (articles-match-feed?
      (map (fn [a b c d] {:article a
                          :author b
                          :feed c
                          :follows d})
           expected-articles expected-authors (:articles body)
           expected-follows)))))

(deftest no-filter-no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          article (test-utils/create-article db (:id author))
          r (list-articles-request "")]
      (validate-response r [article] [author]))))

(deftest no-filter-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          article (test-utils/create-article db (:id author))
          r (list-articles-request "" (get-login-token author))]
      (validate-response r [article] [author] [false]))))

(deftest filter-by-author-no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author-one (test-utils/create-user db)
          author-two (test-utils/create-user db)
          article-one (test-utils/create-article db (:id author-one))
          article-two (test-utils/create-article db (:id author-two))
          r (list-articles-request (str "?author=" (:username author-one)))]
      (validate-response r [article-one] [author-one]))))

(deftest filter-by-author-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author-one (test-utils/create-user db)
          author-two (test-utils/create-user db)
          article-one (test-utils/create-article db (:id author-one))
          article-two (test-utils/create-article db (:id author-two))
          r (list-articles-request (str "?author=" (:username author-one))
                                   (get-login-token author-one))]
      (validate-response r [article-one] [author-one] [false false]))))

(deftest filter-by-favorited-no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author-one (test-utils/create-user db)
          now (jt/local-date-time)
          yesterday (jt/- now (jt/days 1))
          a-one-one (test-utils/create-article db (:id author-one)
                                               {:updated-at now})
          a-one-two (test-utils/create-article db (:id author-one)
                                               {:updated-at now})
          author-two (test-utils/create-user db)
          a-two-one (test-utils/create-article db (:id author-two)
                                               {:updated-at yesterday})
          a-two-two (test-utils/create-article db (:id author-two)
                                               {:updated-at yesterday})
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          _ (test-utils/fav-article db user-one a-one-one)
          _ (test-utils/fav-article db user-one a-two-two)
          _ (test-utils/fav-article db user-two a-one-two)
          _ (test-utils/fav-article db user-two a-two-one)
          r (list-articles-request (str "?favorited=" (:username user-one)))]
      (validate-response r [a-one-one a-two-two] [author-one author-two]))))

(deftest filter-by-favorited-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author-one (test-utils/create-user db)
          now (jt/local-date-time)
          yesterday (jt/- now (jt/days 1))
          a-one-one (test-utils/create-article db (:id author-one)
                                               {:updated-at now})
          a-one-two (test-utils/create-article db (:id author-one)
                                               {:updated-at now})
          author-two (test-utils/create-user db)
          a-two-one (test-utils/create-article db (:id author-two)
                                               {:updated-at yesterday})
          a-two-two (test-utils/create-article db (:id author-two)
                                               {:updated-at yesterday})
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          _ (test-utils/fav-article db user-one a-one-one)
          _ (test-utils/fav-article db user-one a-two-two)
          _ (test-utils/fav-article db user-two a-one-two)
          _ (test-utils/fav-article db user-two a-two-one)
          r (list-articles-request (str "?favorited=" (:username user-one))
                                   (get-login-token user-two))]
      (validate-response r [a-one-one a-two-two] [author-one author-two]
                         [false false]))))

(deftest filter-by-tag-no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          now (jt/local-date-time)
          article-one (test-utils/create-article db (:id author)
                                                 {:tag-list ["tag-one"]
                                                  :updated-at now})
          article-two (test-utils/create-article
                       db (:id author)
                       {:tag-list ["tag-two"]
                        :updated-at (jt/- now (jt/days 1))})
          article-three (test-utils/create-article
                         db (:id author)
                         {:tag-list ["tag-one" "tag-two"]
                          :updated-at (jt/- now (jt/days 2))})
          r (list-articles-request "?tag=tag-one")]
      (validate-response r [article-one article-three] [author author]))))

(deftest filter-by-tag-no-results
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          now (jt/local-date-time)
          _ (test-utils/create-article db (:id author) {:tag-list []
                                                        :updated-at now})
          _ (test-utils/create-article db (:id author)
                                       {:tag-list ["tag-two"]
                                        :updated-at (jt/- now (jt/days 1))})
          _ (test-utils/create-article db (:id author)
                                       {:tag-list ["tag-three"]
                                        :updated-at (jt/- now (jt/days 2))})
          r (list-articles-request "?tag=tag-one")]
      (validate-response r [] []))))

(deftest filter-by-tag-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          now (jt/local-date-time)
          article-one (test-utils/create-article
                       db (:id author)
                       {:tag-list ["tag-one"]
                        :updated-at now})
          article-two (test-utils/create-article
                       db (:id author)
                       {:tag-list ["tag-two"]
                        :updated-at (jt/- now (jt/days 1))})
          article-three (test-utils/create-article
                         db (:id author)
                         {:tag-list ["tag-one" "tag-two"]
                          :updated-at (jt/- now (jt/days 2))})
          r (list-articles-request "?tag=tag-one" (:id author))]
      (validate-response r [article-one article-three] [author author]))))

(deftest order-by-most-recent-no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          now (jt/local-date-time)
          a1 (test-utils/create-article db (:id author)
                                        {:updated-at (jt/- now (jt/days 2))})
          a2 (test-utils/create-article db (:id author)
                                        {:updated-at (jt/- now (jt/days 1))})
          a3 (test-utils/create-article db (:id author) {:updated-at now})
          r (list-articles-request "")]
      (validate-response r [a3 a2 a1] [author author author]))))

(deftest order-by-most-recent-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          now (jt/local-date-time)
          a1 (test-utils/create-article db (:id author)
                                        {:updated-at (jt/- now (jt/days 2))})
          a2 (test-utils/create-article db (:id author)
                                        {:updated-at (jt/- now (jt/days 1))})
          a3 (test-utils/create-article db (:id author) {:updated-at now})
          r (list-articles-request "" (get-login-token author))]
      (validate-response r [a3 a2 a1] [author author author]
                         [false false false]))))

(deftest followed-users
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author-one (test-utils/create-user db)
          author-two (test-utils/create-user db)
          user (test-utils/create-user db)
          _ (test-utils/create-follows db user author-two)
          now (jt/local-date-time)
          a1 (test-utils/create-article db (:id author-two)
                                        {:updated-at (jt/- now (jt/days 2))})
          a2 (test-utils/create-article db (:id author-one)
                                        {:updated-at (jt/- now (jt/days 1))})
          a3 (test-utils/create-article db (:id author-two) {:updated-at now})
          r (list-articles-request "" (get-login-token user))]
      (validate-response r [a3 a2 a1] [author-two author-one author-two]
                         [true false true]))))
