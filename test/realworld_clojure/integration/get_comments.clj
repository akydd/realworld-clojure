(ns realworld-clojure.integration.get-comments
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.integration.common :refer [get-comments-request get-login-token no-auth-comment-schema auth-comment-schema]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [cheshire.core :as json]
   [malli.core :as m]))

(deftest get-comments
  (testing "no article"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (get-comments-request "no-article")]
        (is (= 404 (:status r))))))

  (testing "article has no comments"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            article (test-utils/create-article db (:id user))
            r (get-comments-request (:slug article))
            comments (:comments (json/parse-string (:body r) true))]
        (is (= 200 (:status r)))
        (is (= 0 (count comments))))))

  (testing "no auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user-one (test-utils/create-user db)
            user-twp (test-utils/create-user db)
            article (test-utils/create-article db (:id user-one))
            c-1 (test-utils/create-comment db (:id article) (:id user-one))
            c-2 (test-utils/create-comment db (:id article) (:id user-twp))
            r (get-comments-request (:slug article))
            comments (:comments (json/parse-string (:body r) true))]
        (is (= 200 (:status r)))
        (is (= 2 (count comments)))
        (is (every? #(m/validate no-auth-comment-schema %) comments))
        (is (= (:body c-1) (:body (first comments))))
        (is (= (:body c-2) (:body (second comments))))
        (is (test-utils/comments-are-equal? c-1 (first comments)))
        (is (test-utils/comments-are-equal? c-2 (second comments))))))

  (testing "with auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user-one (test-utils/create-user db)
            user-two (test-utils/create-user db)
            article (test-utils/create-article db (:id user-one))
            c-1 (test-utils/create-comment db (:id article) (:id user-one))
            c-2 (test-utils/create-comment db (:id article) (:id user-two))
            token (get-login-token user-one)
            r (get-comments-request (:slug article) token)
            comments (:comments (json/parse-string (:body r) true))]
        (is (= 200 (:status r)))
        (is (= 2 (count comments)))
        (is (every? #(m/validate auth-comment-schema %) comments))
        (is (= (:body c-1) (:body (first comments))))
        (is (= (:body c-2) (:body (second comments))))
        (is (test-utils/comments-are-equal? c-1 (first comments)))
        (is (test-utils/comments-are-equal? c-2 (second comments)))))))
