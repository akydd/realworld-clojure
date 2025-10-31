(ns realworld-clojure.integration.authentication-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.integration.common :refer [login-request user-response-schema]]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [cheshire.core :as json]
   [malli.core :as m]
   [malli.error :as me]))

(deftest no-input
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (login-request)]
      (is (= 422 (:status r))))))

(deftest invalid-input
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (login-request "" "")]
      (is (= 422 (:status r))))))

(deftest no-user-found
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (login-request "hi" "hi:")]
      (is (= 403 (:status r))))))

(deftest wrong-password
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          r (login-request (:email user) "hi")]
      (is (= 403 (:status r))))))

(deftest success
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          r (login-request (:email user) (:password user))
          returned-user (-> r
                            (:body)
                            (json/parse-string true)
                            (:user))]
      (is (= 200 (:status r)))
      (is (true? (m/validate user-response-schema returned-user)) (->> returned-user
                                                                       (m/explain user-response-schema)
                                                                       (me/humanize)))
      (is (= (:username user) (:username returned-user))))))
