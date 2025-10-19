(ns realworld-clojure.integration.article-feed
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.integration.common :refer [article-feed-request get-login-token profiles-equal? article-matches-feed? article-feed-schema multiple-auth-article-schema]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [cheshire.core :as json]
   [malli.core :as m]
   [malli.error :as me]
   [java-time.api :as jt]))

(deftest article-feed

  (testing "no auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (article-feed-request "")]
        (is (= 401 (:status r))))))

  (testing "following no-one"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            author (test-utils/create-user db)
            _ (test-utils/create-article db (:id author))
            user (test-utils/create-user db)
            token (get-login-token user)
            r (article-feed-request "" token)
            body (-> r
                     (:body)
                     (json/parse-string true))
            articles (:articles body)]
        (is (= 200 (:status r)))
        (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                        (m/explain multiple-auth-article-schema)
                                                                        (me/humanize)))
        (is (zero? (count articles)))
        (is (zero? (:articlesCount body))))))

  (testing "following user with no articles"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user-one (test-utils/create-user db)
            user-two (test-utils/create-user db)
            user-three (test-utils/create-user db)
            _ (test-utils/create-article db (:id user-one))
            _ (test-utils/create-follows db user-two user-three)
            token (get-login-token user-two)
            r (article-feed-request "" token)
            body (-> r
                     (:body)
                     (json/parse-string true))]
        (is (= 200 (:status r)))
        (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                        (m/explain multiple-auth-article-schema)
                                                                        (me/humanize)))
        (is (zero? (count (:articles body))))
        (is (zero? (:articlesCount body))))))

  (testing "following multiple authors"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            author-one (test-utils/create-user db)
            author-two (test-utils/create-user db)
            user (test-utils/create-user db)
            _ (test-utils/create-follows db user author-one)
            _ (test-utils/create-follows db user author-two)
            a1 (test-utils/create-article db (:id author-one))
            a2 (test-utils/create-article db (:id author-two))
            token (get-login-token user)
            r (article-feed-request "" token)
            body (-> r
                     (:body)
                     (json/parse-string true))
            articles (:articles body)]
        (is (= 200 (:status r)))
        (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                        (m/explain multiple-auth-article-schema)
                                                                        (me/humanize)))
        (is (every? #(get-in % [:author :following]) articles))
        (is (= 2 (count articles)))
        (is (= 2 (:articlesCount body)))
        (is (true? (profiles-equal? author-one (:author (second articles)))))
        (is (true? (article-matches-feed? a1 (second articles))))
        (is (true? (profiles-equal? author-two (:author (first articles)))))
        (is (true? (article-matches-feed? a2 (first articles)))))))

  (testing "orders articles by most recently created"
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
            token (get-login-token user)
            r (article-feed-request "" token)
            body (-> r
                     (:body)
                     (json/parse-string true))
            articles (:articles body)]
        (is (= 200 (:status r)))
        (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                        (m/explain multiple-auth-article-schema)
                                                                        (me/humanize)))
        (is (= (:slug a3) (:slug (first articles))))
        (is (= (:slug a2) (:slug (second articles))))
        (is (= (:slug a1) (:slug (nth articles 2)))))))

  (testing "orders articles by most recently updated"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            author (test-utils/create-user db)
            user (test-utils/create-user db)
            _ (test-utils/create-follows db user author)
            now (jt/local-date-time)
            a1 (test-utils/create-article db (:id author) {:updatedat (jt/- now (jt/days 2))})
            a2 (test-utils/create-article db (:id author) {:updatedat (jt/- now (jt/days 1))})
            a3 (test-utils/create-article db (:id author) {:updatedat now})
            token (get-login-token user)
            r (article-feed-request "" token)
            body (-> r
                     (:body)
                     (json/parse-string true))
            articles (:articles body)]
        (is (= 200 (:status r)))
        (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                        (m/explain multiple-auth-article-schema)
                                                                        (me/humanize)))
        (is (= (:slug a3) (:slug (first articles))))
        (is (= (:slug a2) (:slug (second articles))))
        (is (= (:slug a1) (:slug (nth articles 2)))))))

  (testing "orders articles by prioritizing most recently updated over most recently created"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            author (test-utils/create-user db)
            user (test-utils/create-user db)
            _ (test-utils/create-follows db user author)
            now (jt/local-date-time)
            a1 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 3))
                                                           :updatedat now})
            a2 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 2))})
            a3 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 1))})
            token (get-login-token user)
            r (article-feed-request "" token)
            body (-> r
                     (:body)
                     (json/parse-string true))
            articles (:articles body)]
        (is (= 200 (:status r)))
        (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                        (m/explain multiple-auth-article-schema)
                                                                        (me/humanize)))
        (is (= (:slug a1) (:slug (first articles))))
        (is (= (:slug a3) (:slug (second articles))))
        (is (= (:slug a2) (:slug (nth articles 2))))))))
