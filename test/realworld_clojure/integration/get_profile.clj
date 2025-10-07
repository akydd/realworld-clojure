(ns realworld-clojure.integration.get-profile
  (:require
   [clojure.test :refer [deftest is testing]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [malli.core :as m]
   [realworld-clojure.integration.common :refer [no-auth-profile-schema get-profile-request]]
   [cheshire.core :as json]))

(deftest get-profile
  (testing "without auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            r (get-profile-request (:username user))
            body (json/parse-string (:body r) true)]
        (is (= 200 (:status r)))
        (is (= (:username user) (get-in body [:profile :username])))
        (is (true? (m/validate no-auth-profile-schema (:profile body)))))))

  (testing "with auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            r (get-profile-request (:username user))
            body (json/parse-string (:body r) true)]
        (is (= 200 (:status r)))
        (is (= (:username user) (get-in body [:profile :username])))
        (is (true? (m/validate no-auth-profile-schema (:profile body))))))))
