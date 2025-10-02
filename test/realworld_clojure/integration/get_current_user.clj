(ns realworld-clojure.integration.get-current-user
  (:require
   [org.httpkit.client :as http]
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [cheshire.core :as json]))

(def url "http://localhost:8099/api/user")

(defn send-request
  [token]
  @(http/get url {:headers {"Content-Type" "application/json" "Authorization" (str "Token " token)}}))

(deftest get-current-user
  (testing "no authentication"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r @(http/get url {:headers {"Content-Type" "application/json"}})]
        (is (= 401 (:status r))))))

  (testing "invalid token"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (send-request "bleh")]
        (is (= 401 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            login-response @(http/post
                             "http://localhost:8099/api/users/login"
                             {:headers {"Content-Type" "application/json"}
                              :body (json/generate-string {:user (select-keys user [:email :password])})})
            login-user (:user (json/parse-string (:body login-response) true))
            token (:token login-user)
            r (send-request token)
            current-user (:user (json/parse-string (:body r) true))]
        (is (= 200 (:status r)))
        (is (= login-user current-user))))))
