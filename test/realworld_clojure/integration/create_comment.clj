(ns realworld-clojure.integration.create-comment
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.integration.common :refer [create-comment-request get-login-token auth-comment-schema]]
   [malli.generator :as mg]
   [realworld-clojure.domain.comment :as comment]
   [cheshire.core :as json]
   [malli.core :as m]
   [malli.error :as me]))

(deftest create-comment
  (testing "no auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [c (mg/generate comment/comment-create-schema)
            r (create-comment-request "slug" c)]
        (is (= 401 (:status r))))))

  (testing "no article"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            c (mg/generate comment/comment-create-schema)
            token (get-login-token user)
            r (create-comment-request "no-article" c token)]
        (is (= 404 (:status r))))))

  (testing "invalid input"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            article (test-utils/create-article db (:id user))
            c {:garbage "hello"}
            token (get-login-token user)
            r (create-comment-request (:slug article) c token)]
        (is (= 422 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            article (test-utils/create-article db (:id user))
            c (mg/generate comment/comment-create-schema)
            token (get-login-token user)
            r (create-comment-request (:slug article) c token)
            comment (:comment (json/parse-string (:body r) true))]
        (is (= 200 (:status r)))
        (is (true? (m/validate auth-comment-schema comment)) (->> comment
                                                                  (m/explain auth-comment-schema)
                                                                  (me/humanize)))
        (is (false? (get-in comment [:author :following])))))))
