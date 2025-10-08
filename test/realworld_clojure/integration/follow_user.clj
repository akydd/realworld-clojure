(ns realworld-clojure.integration.follow-user
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.integration.common :refer [follow-user-request get-login-token auth-profile-schema profiles-equal?]]
   [cheshire.core :as json]
   [malli.core :as m]))

(deftest follow-user
  (testing "no auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            r (follow-user-request (:username user))]
        (is (= 401 (:status r))))))

  (testing "user does not exist"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            token (get-login-token user)
            r (follow-user-request "not-a-user" token)]
        (is (= 404 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user-one (test-utils/create-user db)
            user-two (test-utils/create-user db)
            token (get-login-token user-one)
            r (follow-user-request (:username user-two) token)
            body (json/parse-string (:body r) true)]
        (is (= 200 (:status r)))
        (is (true? (m/validate auth-profile-schema (:profile body))))
        (is (true? (get-in body [:profile :following])))
        (is (true? (profiles-equal? user-two (:profile body)))))))

  (testing "already following user"))
