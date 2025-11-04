(ns realworld-clojure.integration.unfavorite-article
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.integration.common :refer [unfavorite-article-request
                                                 get-login-token
                                                 favorite-article-request
                                                 auth-article-schema]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [cheshire.core :as json]
   [malli.core :as m]
   [malli.error :as me]))

(deftest unfavorite-article

  (testing "no auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (unfavorite-article-request "a-slug")]
        (is (= 401 (:status r))))))

  (testing "no article found"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))
       (let [db (get-in sut [:database :datasource])
             user (test-utils/create-user db)
             token (get-login-token user)
             r (unfavorite-article-request "no-article" token)]
         (is (= 404 (:status r))))]))

  (testing "marks a favorited article as unfavorited"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            article (test-utils/create-article db (:id user))
            token (get-login-token user)
            _ (favorite-article-request (:slug article) token)
            r (unfavorite-article-request (:slug article) token)
            returned-article (:article (json/parse-string (:body r) true))]
        (is (= 200 (:status r)))
        (is (true? (m/validate auth-article-schema returned-article))
            (->> returned-article
                 (m/explain auth-article-schema)
                 (me/humanize)))
        (is (false? (:favorited returned-article)))
        (is (zero? (:favoritesCount returned-article))))))

  (testing "leaves an unfavorited article as unfavorited"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            article (test-utils/create-article db (:id user))
            token (get-login-token user)
            r (unfavorite-article-request (:slug article) token)
            returned-article (-> r
                                 (:body)
                                 (json/parse-string true)
                                 (:article))]
        (is (= 200 (:status r)))
        (is (true? (m/validate auth-article-schema returned-article))
            (->> returned-article
                 (m/explain auth-article-schema)
                 (me/humanize)))
        (is (false? (:favorited returned-article)))
        (is (zero? (:favoritesCount returned-article)))))))
