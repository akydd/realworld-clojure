(ns realworld-clojure.integration.delete-comment
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.integration.common :refer [delete-comment-request get-login-token]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [cheshire.core :as json]))

(deftest delete-comment
  (testing "no auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (delete-comment-request "slug" 2)]
        (is (= 401 (:status r))))))

  (testing "slug not found"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            token (get-login-token user)
            r (delete-comment-request "no-article-here" 1 token)]
        (is (= 404 (:status r))))))

  (testing "unparsable comment id"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            article (test-utils/create-article db (:id user))
            token (get-login-token user)
            r (delete-comment-request (:slug article) "not-an-int" token)]
        (is (= 404 (:status r))))))

  (testing "comment not found"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            article (test-utils/create-article db (:id user))
            token (get-login-token user)
            r (delete-comment-request (:slug article) 3 token)]
        (is (= 404 (:status r))))))

  (testing "not the author"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user-one (test-utils/create-user db)
            user-two (test-utils/create-user db)
            article (test-utils/create-article db (:id user-two))
            comment (test-utils/create-comment db (:id article) (:id user-one))
            token (get-login-token user-two)
            r (delete-comment-request (:slug article) (:id comment) token)]
        (is (= 403 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            article (test-utils/create-article db (:id user))
            comment (test-utils/create-comment db (:id article) (:id user))
            token (get-login-token user)
            r (delete-comment-request (:slug article) (:id comment) token)]
        (is (= 200 (:status r)))))))
