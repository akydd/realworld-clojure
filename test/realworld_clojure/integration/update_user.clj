(ns realworld-clojure.integration.update-user
  (:require
   [clojure.test :refer [deftest testing is]]
   [org.httpkit.client :as http]
   [cheshire.core :as json]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [mali.core :as m]
   [realworld-clojure.integration.common :refer [user-response-schema]]))

(def url "http://localhost:8099/api/user")
(def login-url "http://localhost:8099/api/users/login")

(defn- send-request
  ([user]
   @(http/put url {:headers {"Content-Type" "application/json"}
                   :body (json/generate-string {:user user})}))
  ([user token]
   @(http/put url {:headers {"Content-Type" "application/json"
                             "Authorization" (str "Token " token)}
                   :body (json/generate-string {:user user})})))

(defn- login-user
  [user]
  @(http/post login-url {:headers {"Content-Type" "application/json"}
                         :body (json/generate-string {:user (select-keys user [:email :password])})}))

(deftest update-user
  (testing "no authentication"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            _ (test-utils/create-user db)
            r (send-request {:bio "hello there"})]
        (is (= 401 (:status r))))))

  (testing "wrong authentication"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            _ (test-utils/create-user db)
            r (send-request {:bio "hello there"} "fake-token")]
        (is (= 403 (:status r))))))

  (testing "invalid input"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            login-response (login-user user)
            body (json/parse-string (:body login-response) true)
            token (get-in body [:user :token])
            r (send-request {:garbage "hi"} token)]
        (is (= 422 (:status r))))))

  (testing "duplicate username"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user-one (test-utils/create-user db)
            user-two (test-utils/create-user db)
            login-response (login-user user-two)
            body (json/parse-string (:body login-response) true)
            token (get-in body [:user :token])
            r (send-request {:username (:username user-one)} token)]
        (is (= 409 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            login-response (login-user user)
            body (json/parse-string (:body login-response) true)
            token (get-in body [:user :token])
            r (send-request {:username "Bilbo Baggins"} token)
            parsed-update-body (json/parse-string (:body r) true)]
        (is (= 200 (:status r)))
        (is (true? (m/validate? user-response-schema (:user parsed-update-body))))
        (is (= "Bilbo Baggins" (get-in parsed-update-body [:user :username])))))))
