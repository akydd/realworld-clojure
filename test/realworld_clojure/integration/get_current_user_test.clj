(ns realworld-clojure.integration.get-current-user-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.core :as core]
   [realworld-clojure.integration.common :refer [get-user-request
                                                 login-request]]
   [realworld-clojure.utils :as test-utils]))

(deftest no-authentication
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (get-user-request)]
      (is (= 401 (:status r))))))

(deftest invalid-token
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (get-user-request "bleh")]
      (is (= 401 (:status r))))))

(deftest success
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          login-response (login-request (:email user) (:password user))
          login-user (:user (json/parse-string (:body login-response) true))
          token (:token login-user)
          r (get-user-request token)
          current-user (:user (json/parse-string (:body r) true))]
      (is (= 200 (:status r)))
      (is (= login-user current-user)))))
