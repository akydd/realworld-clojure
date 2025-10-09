(ns realworld-clojure.integration.get-article
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.integration.common :refer [get-article-request get-login-token no-auth-article-schema auth-article-schema]]
   [malli.core :as m]
   [cheshire.core :as json]
   [malli.error :as me]))

(deftest get-article
  (testing "does not exist, no auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (get-article-request "no-slug")]
        (is (= 404 (:status r))))))

  (testing "does not exist, authenticated"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            token (get-login-token user)
            r (get-article-request "no-slug" token)]
        (is (= 404 (:status r))))))

  (testing "exists, no auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            article (test-utils/create-article db (:id user))
            r (get-article-request (:slug article))
            returned-article (:article (json/parse-string (:body r) true))]
        (is (= 200 (:status r)))
        (is (true? (m/validate no-auth-article-schema returned-article)) (->> returned-article
                                                                              (m/explain no-auth-article-schema)
                                                                              (me/humanize))))))

  (testing "exists, authenticated, not following the author"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            author (test-utils/create-user db)
            article (test-utils/create-article db (:id author))
            user (test-utils/create-user db)
            token (get-login-token user)
            r (get-article-request (:slug article) token)
            returned-article (:article (json/parse-string (:body r) true))]
        (is (= 200 (:status r)))
        (is (true? (m/validate auth-article-schema returned-article)) (->> returned-article
                                                                           (m/explain auth-article-schema)
                                                                           (me/humanize)))
        (is (false? (get-in returned-article [:author :following]))))))

  (testing "exists, authenticated, following the author"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            author (test-utils/create-user db)
            article (test-utils/create-article db (:id author))
            user (test-utils/create-user db)
            _ (test-utils/create-follows db user author)
            token (get-login-token user)
            r (get-article-request (:slug article) token)
            returned-article (:article (json/parse-string (:body r) true))]
        (is (= 200 (:status r)))
        (is (true? (m/validate auth-article-schema returned-article)) (->> returned-article
                                                                           (m/explain auth-article-schema)
                                                                           (me/humanize)))
        (is (true? (get-in returned-article [:author :following])))))))
