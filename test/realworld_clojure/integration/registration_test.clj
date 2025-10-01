(ns realworld-clojure.integration.registration-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [org.httpkit.client :as http]
   [cheshire.core :as json]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   (realworld-clojure.integration.common :refer [user-response-schema])
   [malli.core :as m]
   [malli.generator :as mg]
   [realworld-clojure.domain.user :as user]))

(def url "http://localhost:8099/api/users")

(defn send-request
  [body]
  @(http/post url {:headers {"Content-Type" "application/json"}
                   :body (json/generate-string body)}))

(deftest registration
  (testing "invalid input"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (send-request {})]
        (is (= 422 (:status r))))))

  (testing "duplicate username"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            r (send-request {:user (select-keys user [:username :password :email])})]
        (is (= 409 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [user (mg/generate user/User)
            r (send-request {:user user})
            parsed-body (json/parse-string (:body r) true)]
        (is (= 200 (:status r)))
        (is (true? (m/validate user-response-schema (:user parsed-body))))))))
