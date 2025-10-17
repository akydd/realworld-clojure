(ns realworld-clojure.integration.get-profile
  (:require
   [clojure.test :refer [deftest is testing]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [malli.core :as m]
   [realworld-clojure.integration.common :refer [no-auth-profile-schema auth-profile-schema get-profile-request get-login-token]]
   [cheshire.core :as json]))

(deftest get-profile
  (testing "without auth"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            r (get-profile-request (:username user))
            profile (-> r
                        (:body)
                        (json/parse-string true)
                        (:profile))]
        (is (= 200 (:status r)))
        (is (= (:username user) (:username profile)))
        (is (true? (m/validate no-auth-profile-schema profile))))))

  (testing "with auth, not following"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user (test-utils/create-user db)
            user-two (test-utils/create-user db)
            token (get-login-token user-two)
            r (get-profile-request (:username user) token)
            profile (-> r
                        (:body)
                        (json/parse-string true)
                        (:profile))]
        (is (= 200 (:status r)))
        (is (= (:username user) (:username profile)))
        (is (true? (m/validate auth-profile-schema profile)))
        (is (false? (:following profile))))))

  (testing "with auth, following"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            user-one (test-utils/create-user db)
            user-two (test-utils/create-user db)
            _ (test-utils/create-follows db user-one user-two)
            token (get-login-token user-one)
            r (get-profile-request (:username user-two) token)
            profile (-> r
                        (:body)
                        (json/parse-string true)
                        (:profile))]
        (is (= 200 (:status r)))
        (is (true? (m/validate auth-profile-schema profile)))
        (is (= (:username user-two) (:username profile)))
        (is (true? (:following profile)))))))
