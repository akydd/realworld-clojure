(ns realworld-clojure.integration.list-articles
  (:require
   [clojure.test :refer [deftest is]]
   [realworld-clojure.integration.common :refer [list-articles-request multiple-no-auth-article-schema multiple-auth-article-schema articles-match-feed?]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [cheshire.core :as json]
   [java-time.api :as jt]
   [malli.core :as m]
   [malli.error :as me]))

(deftest no-articles-no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (list-articles-request "")
          body (-> r
                   (:body)
                   (json/parse-string true))]
      (is (= 200 (:status r)))
      (is (true? (m/validate multiple-no-auth-article-schema body)) (->> body
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
     (is (true? (m/validate multiple-no-auth-article-schema body)) (->> body
                                                                        (m/explain multiple-no-auth-article-schema)
                                                                        (me/humanize)))
     (is (= (count expected-articles) (:articlesCount body)))
     (articles-match-feed? (map (fn [a b c] {:article a
                                             :author b
                                             :feed c}) expected-articles expected-authors (:articles body)))))
  ([response expected-articles expected-authors expected-follows]
   (let [body (-> response
                  (:body)
                  (json/parse-string true))]
     (is (= 200 (:status response)))
     (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                     (m/explain multiple-auth-article-schema)
                                                                     (me/humanize)))
     (is (= (count expected-articles) (:articlesCount body)))
     (articles-match-feed? (map (fn [a b c d] {:article a
                                               :author b
                                               :feed c
                                               :follows d}) expected-articles expected-authors (:articles body) expected-follows)))))

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

(deftest filter-by-favorited-no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author-one (test-utils/create-user db)
          now (jt/local-date-time)
          yesterday (jt/- now (jt/days 1))
          a-one-one (test-utils/create-article db (:id author-one) {:createdat now})
          a-one-two (test-utils/create-article db (:id author-one) {:createdat now})
          author-two (test-utils/create-user db)
          a-two-one (test-utils/create-article db (:id author-two) {:createdat yesterday})
          a-two-two (test-utils/create-article db (:id author-two) {:createdat yesterday})
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          _ (test-utils/fav-article db user-one a-one-one)
          _ (test-utils/fav-article db user-one a-two-two)
          _ (test-utils/fav-article db user-two a-one-two)
          _ (test-utils/fav-article db user-two a-two-one)
          r (list-articles-request (str "?favorited=" (:username user-one)))]
      (validate-response r [a-one-one a-two-two] [author-one author-two]))))

(deftest order-by-most-recent-no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          user (test-utils/create-user db)
          _ (test-utils/create-follows db user author)
          now (jt/local-date-time)
          a1 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 2))})
          a2 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 1))})
          a3 (test-utils/create-article db (:id author) {:createdat now})
          r (list-articles-request "")]
      (validate-response r [a3 a2 a1] [author author author]))))
