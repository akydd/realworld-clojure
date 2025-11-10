(ns realworld-clojure.integration.delete-article-test
  (:require
   [clojure.test :refer [deftest is]]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.core :as core]
   [realworld-clojure.integration.common :refer [delete-article-request
                                                 get-login-token
                                                 get-article-request]]
   [realworld-clojure.utils :as test-utils]))

(deftest no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (delete-article-request "slug")]
      (is (= 401 (:status r))))))

(deftest not-the-author
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          article (test-utils/create-article db (:id user-one))
          token (get-login-token user-two)
          r (delete-article-request (:slug article) token)]
      (is (= 403 (:status r))))))

(deftest no-article-found
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          token (get-login-token user)
          r (delete-article-request "no-article" token)]
      (is (= 404 (:status r))))))

(deftest success
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article (test-utils/create-article db (:id user))
          token (get-login-token user)
          r (delete-article-request (:slug article) token)
          s (get-article-request (:slug article))]
      (is (= 200 (:status r)))
      (is (= 404 (:status s))))))

(deftest favorited-article
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article (test-utils/create-article db (:id user))
          _ (test-utils/fav-article db user article)
          r (delete-article-request (:slug article) (get-login-token user))
          s (get-article-request (:slug article))]
      (is (= 200 (:status r)))
      (is (= 404 (:status s))))))

(deftest article-with-comment
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article (test-utils/create-article db (:id user))
          _ (test-utils/create-comment db (:id article) (:id user))
          r (delete-article-request (:slug article) (get-login-token user))
          s (get-article-request (:slug article))]
      (is (= 200 (:status r)))
      (is (= 404 (:status s))))))
