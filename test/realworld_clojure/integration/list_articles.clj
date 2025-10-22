(ns realworld-clojure.integration.list-articles
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.integration.common :refer [list-articles-request multiple-no-auth-article-schema]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [cheshire.core :as json]
   [java-time.api :as jt]
   [malli.core :as m]
   [malli.error :as me]))

(deftest list-articles

  (testing "no articles, no auth"
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

  (testing "author filter, no auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            author-one (test-utils/create-user db)
            author-two (test-utils/create-user db)
            article-one (test-utils/create-article db (:id author-one))
            article-two (test-utils/create-article db (:id author-two))
            r (list-articles-request (str "?author=" (:username author-one)))
            body (-> r
                     (:body)
                     (json/parse-string true))
            articles (:articles body)]
        (is (= 200 (:status r)))
        (is (true? (m/validate multiple-no-auth-article-schema body)) (->> body
                                                                           (m/explain multiple-no-auth-article-schema)
                                                                           (me/humanize)))
        (is (= 1 (:articlesCount body)))
        (is (= 1 (count articles)))
        (is (= (:username author-one) (get-in (first articles) [:author :username]))))))

  (testing "favorited filter, no auth"
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
            r (list-articles-request (str "?favorited=" (:username user-one)))
            body (-> r
                     (:body)
                     (json/parse-string true))
            articles (:articles body)]
        (is (= 200 (:status r)))
        (is (true? (m/validate multiple-no-auth-article-schema body)) (->> body
                                                                           (m/explain multiple-no-auth-article-schema)
                                                                           (me/humanize)))
        (is (= 2 (:articlesCount body)))
        (is (= 2 (count articles)))
        (is (= (:title a-one-one) (:title (first articles))))
        (is (= (:title a-two-two) (:title (second articles)))))))

  (testing "orders articles by most recently created, no auth"
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
            r (list-articles-request "")
            body (-> r
                     (:body)
                     (json/parse-string true))
            articles (:articles body)]
        (is (= 200 (:status r)))
        (is (true? (m/validate multiple-no-auth-article-schema body)) (->> body
                                                                           (m/explain multiple-no-auth-article-schema)
                                                                           (me/humanize)))
        (is (= 3 (:articlesCount body)))
        (is (= 3 (count articles)))
        (is (= (:slug a3) (:slug (first articles))))
        (is (= (:slug a2) (:slug (second articles))))
        (is (= (:slug a1) (:slug (nth articles 2)))))))

  (testing "no articles, auth"))
