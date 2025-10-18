(ns realworld-clojure.integration.article-feed
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.integration.common :refer [article-feed-request get-login-token]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [cheshire.core :as json]))

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
            returned-articles (-> r
                                  (:body)
                                  (json/parse-string true)
                                  (:articles))]
        (is (= 200 (:status r)))
        (is (zero? (count returned-articles))))))

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
            returned-articles (-> r
                                  (:body)
                                  (json/parse-string true)
                                  (:articles))]
        (is (= 200 (:status r)))
        (is (zero? (count returned-articles)))))))
