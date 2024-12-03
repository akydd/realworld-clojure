(ns realworld-clojure.domain.user-test
  (:require
   [realworld-clojure.config-test :as config]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.domain.user :as user]
   [malli.generator :as mg]
   [next.jdbc.sql :as sql]
   [clojure.test :refer :all]
   [realworld-clojure.core :as core]
   [clojure.core :as c]))


(deftest register-invalid-input
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (is (c/contains? (user/register-user (:user-controller sut) {}) :errors))))

(deftest register-already-exists
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          test-user (mg/generate user/User)
          _ (sql/insert! db :users test-user)]
      (is (thrown-with-msg? Exception #"duplicate" (user/register-user (:user-controller sut) test-user))))))

(deftest register-success
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [test-user (mg/generate user/User)
          new-user (user/register-user (:user-controller sut) test-user)]
      (is (every? new-user  [:email :username :password :token]))
      (is (= (:username test-user) (:username new-user)))
      (is (= (:email test-user) (:email new-user))))))

(deftest update-invalid-input
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [input {:garbage "test"}
          db (get-in sut [:database :datasource])
          test-user (mg/generate user/User)
          saved-user (sql/insert! db :users test-user test-utils/query-options)
          result (user/update-user (:user-controller sut) saved-user input)]
      (is (c/contains? result :errors)))))

(deftest update-username-exists
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          auth-user (sql/insert! db :users {:username "auth" :email "auth" :password "auth"} test-utils/query-options)
          _ (sql/insert! db :users {:username "taken" :email "taken" :password "taken"})]
      (is (thrown-with-msg? Exception #"duplicate" (user/update-user (:user-controller sut) auth-user {:username "taken"}))))))

(deftest update-email-exists
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          auth-user (sql/insert! db :users {:username "auth" :email "auth" :password "auth"} test-utils/query-options)
          _ (sql/insert! db :users {:username "taken" :email "taken" :password "taken"})]
      (is (thrown-with-msg? Exception #"duplicate" (user/update-user (:user-controller sut) auth-user {:email "taken"}))))))
