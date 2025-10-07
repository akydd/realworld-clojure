(ns realworld-clojure.integration.registration-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [cheshire.core :as json]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.integration.common :refer [register-request user-response-schema]]
   [malli.core :as m]
   [malli.generator :as mg]
   [realworld-clojure.domain.user :as user]))

(deftest registration
  (testing "invalid input"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (register-request {})]
        (is (= 422 (:status r))))))

  (testing "duplicate username"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            r (register-request user)]
        (is (= 409 (:status r))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [user (mg/generate user/user-schema)
            r (register-request user)
            parsed-body (json/parse-string (:body r) true)]
        (is (= 200 (:status r)))
        (is (true? (m/validate user-response-schema (:user parsed-body))))))))
