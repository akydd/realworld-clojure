(ns realworld-clojure.integration.authentication-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.integration.common :refer [login-request user-response-schema]]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [cheshire.core :as json]
   [malli.core :as m]))

(deftest authentication
  (testing "invalid input"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (login-request {})]
        (is (= 422 (:status r))))))

  (testing "no user found"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (login-request {:email "hi" :password "hi"})]
        (is (= 403 (:status r))))))

  (testing "wrong password"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            r (login-request (assoc user :password "hi"))]
        (is (= 403 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            r (login-request user)
            parsed-body (json/parse-string (:body r) true)]
        (is (= 200 (:status r)))
        (is (true? (m/validate user-response-schema (:user parsed-body))))))))
