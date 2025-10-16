(ns realworld-clojure.integration.favorite-article
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.integration.common :refer [favorite-article-request get-login-token auth-article-schema]]
   [cheshire.core :as json]
   [malli.core :as m]
   [malli.error :as me]))

(deftest favorite-article
  (testing "no auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (favorite-article-request "slug")]
        (is (= 401 (:status r))))))

  (testing "no slug"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            token (get-login-token user)
            r (favorite-article-request "slug" token)]
        (is (= 404 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            article (test-utils/create-article db (:id user))
            token (get-login-token user)
            r (favorite-article-request (:slug article) token)
            a (:article (json/parse-string (:body r) true))]
        (is (= 200 (:status r)))
        (is (true? (m/validate auth-article-schema a)) (->> a
                                                            (m/explain auth-article-schema)
                                                            (me/humanize)))
        (is (true? (:favorited a)))
        (is (= 1 (:favoritescount a))))))

  (testing "already favorited"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            article (test-utils/create-article db (:id user))
            token (get-login-token user)
            _ (favorite-article-request (:slug article) token)
            r (favorite-article-request (:slug article) token)
            ;; a (:article (json/parse-string (:body r) true))
            ]
        (is (= 409 (:status r))))))

  (testing "tracks number of favorites"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            author (test-utils/create-user db)
            user-one (test-utils/create-user db)
            user-two (test-utils/create-user db)
            article (test-utils/create-article db (:id author))
            token-one (get-login-token user-one)
            _ (favorite-article-request (:slug article) token-one)
            token-two (get-login-token user-two)
            r (favorite-article-request (:slug article) token-two)
            returned-article (:article (json/parse-string (:body r) true))]
        (is (= 200 (:status r)))
        (is (true? (m/validate auth-article-schema returned-article)) (->> returned-article
                                                                           (m/explain auth-article-schema)
                                                                           (me/humanize)))
        (is (= 2 (:favoritescount returned-article)))))))
