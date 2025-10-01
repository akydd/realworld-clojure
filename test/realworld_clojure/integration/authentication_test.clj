(ns realworld-clojure.integration.authentication-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [org.httpkit.client :as http]
   [cheshire.core :as json]
   [malli.core :as m]))

;; TODO: get the port from the config
(def url "http://localhost:8099/api/users/login")

(def user-response-schema
  [:map {:closed true}
   [:username [:string {:min 1}]]
   [:token [:string {:min 1}]]
   [:email [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]])

(defn send-request
  [body]
  @(http/post url {:headers  {"Content-Type" "application/json"}
                   :body (json/generate-string body)}))

(deftest authentication
  (testing "invalid input"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (send-request {})]
        (is (= 422 (:status r))))))

  (testing "no user found"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (send-request {:user {:email "hi" :password "hi"}})]
        (is (= 403 (:status r))))))

  (testing "wrong password"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            r (send-request {:user {:email (:email user) :password "hi"}})]
        (is (= 403 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            r (send-request {:user (select-keys user [:email :password])})
            parsed-body (json/parse-string (:body r) true)]
        (is (= 200 (:status r)))
        (is (true? (m/validate user-response-schema (:user parsed-body))))))))
