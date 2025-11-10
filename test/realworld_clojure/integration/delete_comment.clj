(ns realworld-clojure.integration.delete-comment
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.core :as core]
   [realworld-clojure.integration.common :refer [delete-comment-request
                                                 get-login-token
                                                 get-comments-request]]
   [realworld-clojure.utils :as test-utils]))

(deftest no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (delete-comment-request "slug" 2)]
      (is (= 401 (:status r))))))

(deftest article-not-found
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          token (get-login-token user)
          r (delete-comment-request "no-article-here" 1 token)]
      (is (= 404 (:status r))))))

(deftest unparsable-comment-id
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article (test-utils/create-article db (:id user))
          token (get-login-token user)
          r (delete-comment-request (:slug article) "not-an-int" token)]
      (is (= 404 (:status r))))))

(deftest comment-id-not-found
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article (test-utils/create-article db (:id user))
          token (get-login-token user)
          r (delete-comment-request (:slug article) 3 token)]
      (is (= 404 (:status r))))))

(deftest success
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article (test-utils/create-article db (:id user))
          comment (test-utils/create-comment db (:id article) (:id user))
          token (get-login-token user)
          r (delete-comment-request (:slug article) (:id comment) token)
          s (get-comments-request (:slug article))
          comments (-> s
                       (:body)
                       (json/parse-string true)
                       (:comments))]
      (is (= 200 (:status r)))
      (is (= 200 (:status s)))
      (is (every? #(not= (:id comment) (:id %)) comments)))))

(deftest not-the-author
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          article (test-utils/create-article db (:id user-two))
          comment (test-utils/create-comment db (:id article) (:id user-one))
          token (get-login-token user-two)
          r (delete-comment-request (:slug article) (:id comment) token)
          s (get-comments-request (:slug article))
          comments (-> s
                       (:body)
                       (json/parse-string true)
                       (:comments))]
      (is (= 403 (:status r)))
      (is (= 200 (:status s)))
      (is (some #(= (:id comment) (:id %)) comments)))))
