(ns realworld-clojure.integration.get-current-user
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.integration.common :refer [get-user-request login-request]]
   [cheshire.core :as json]))

(deftest get-current-user
  (testing "no authentication"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (get-user-request)]
        (is (= 401 (:status r))))))

  (testing "invalid token"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (get-user-request "bleh")]
        (is (= 401 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            login-response (login-request user)
            login-user (:user (json/parse-string (:body login-response) true))
            token (:token login-user)
            r (get-user-request token)
            current-user (:user (json/parse-string (:body r) true))]
        (is (= 200 (:status r)))
        (is (= login-user current-user))))))
