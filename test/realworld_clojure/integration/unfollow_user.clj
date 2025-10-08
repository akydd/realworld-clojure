(ns realworld-clojure.integration.unfollow-user
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.integration.common :refer [get-login-token unfollow-user-request auth-profile-schema profiles-equal?]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [cheshire.core :as json]
   [malli.core :as m]))

(deftest unfolloow-user
  (testing "no auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            r (unfollow-user-request (:username user))]
        (is (= 401 (:status r))))))

  (testing "user doesn't exists"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            token (get-login-token user)
            r (unfollow-user-request "not-a-user" token)]
        (is (= 404 (:status r))))))

  (testing "user not followed"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user-one (test-utils/create-user db)
            user-two (test-utils/create-user db)
            token (get-login-token user-one)
            r (unfollow-user-request (:username user-two) token)
            body (json/parse-string (:body r) true)]
        (is (= 200 (:status r)))
        (is (true? (m/validate auth-profile-schema (:profile body))))
        (is (false? (get-in body [:profile :following])))
        (is (true? (profiles-equal? user-two (:profile body)))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user-one (test-utils/create-user db)
            user-two (test-utils/create-user db)
            _ (test-utils/create-follows db user-one user-two)
            token (get-login-token user-one)
            r (unfollow-user-request (:username user-two) token)
            body (json/parse-string (:body r) true)]
        (is (= 200 (:status r)))
        (is (true? (m/validate auth-profile-schema (:profile body))))
        (is (false? (get-in body [:profile :following])))
        (is (true? (profiles-equal? user-two (:profile body))))))))
