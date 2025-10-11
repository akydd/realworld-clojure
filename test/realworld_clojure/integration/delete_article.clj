(ns realworld-clojure.integration.delete-article
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.integration.common :refer [delete-article-request get-login-token]]))

(deftest delete-article
  (testing "no auhh"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (delete-article-request "slug")]
        (is (= 401 (:status r))))))

  (testing "not the author"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user-one (test-utils/create-user db)
            user-two (test-utils/create-user db)
            article (test-utils/create-article db (:id user-one))
            token (get-login-token user-two)
            r (delete-article-request (:slug article) token)]
        (is (= 403 (:status r))))))

  (testing "no article found"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            token (get-login-token user)
            r (delete-article-request "no-article" token)]
        (is (= 404 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            article (test-utils/create-article db (:id user))
            token (get-login-token user)
            r (delete-article-request (:slug article) token)]
        (is (= 200 (:status r)))))))
