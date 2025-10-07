(ns realworld-clojure.integration.update-user
  (:require
   [clojure.test :refer [deftest testing is]]
   [cheshire.core :as json]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [malli.core :as m]
   [realworld-clojure.integration.common :refer [update-user-request login-request user-response-schema]]))

(deftest update-user
  (testing "no authentication"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            _ (test-utils/create-user db)
            r (update-user-request {:bio "hello there"})]
        (is (= 401 (:status r))))))

  (testing "wrong authentication"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            _ (test-utils/create-user db)
            r (update-user-request {:bio "hello there"} "fake-token")]
        (is (= 403 (:status r))))))

  (testing "invalid input"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            login-response (login-request user)
            body (json/parse-string (:body login-response) true)
            token (get-in body [:user :token])
            r (update-user-request {:garbage "hi"} token)]
        (is (= 422 (:status r))))))

  (testing "duplicate username"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user-one (test-utils/create-user db)
            user-two (test-utils/create-user db)
            login-response (login-request user-two)
            body (json/parse-string (:body login-response) true)
            token (get-in body [:user :token])
            r (update-user-request {:username (:username user-one)} token)]
        (is (= 409 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            login-response (login-request user)
            body (json/parse-string (:body login-response) true)
            token (get-in body [:user :token])
            r (update-user-request {:username "Bilbo Baggins"} token)
            parsed-update-body (json/parse-string (:body r) true)]
        (is (= 200 (:status r)))
        (is (true? (m/validate user-response-schema (:user parsed-update-body))))
        (is (= "Bilbo Baggins" (get-in parsed-update-body [:user :username])))))))
