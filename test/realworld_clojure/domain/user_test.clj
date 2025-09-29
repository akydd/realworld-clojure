(ns realworld-clojure.domain.user-test
  (:require
   [realworld-clojure.config-test :as config]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.domain.user :as user]
   [malli.generator :as mg]
   [next.jdbc.sql :as sql]
   [clojure.test :refer [deftest is testing]]
   [realworld-clojure.core :as core]
   [clojure.core :as c]))

(deftest login-user
  (testing "invalid input"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (is (c/contains? (user/login-user (:user-controller sut) {}) :errors))))

  (testing "no user found for email"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (is (nil? (user/login-user (:user-controller sut) {:email "no@user.com" :password "aaa"})))))

  (testing "wrong password"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            new-user (test-utils/create-user db)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unauthorized." (user/login-user (:user-controller sut) {:email (:email new-user) :password "!"})))))))

(deftest register-user
  (testing "invalid input"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (is (c/contains? (user/register-user (:user-controller sut) {}) :errors))))

  (testing "username already taken"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            test-user (test-utils/create-user db)
            duplicate-user (select-keys test-user [:username :email])]
        (is (thrown-with-msg? Exception #"duplicate" (user/register-user (:user-controller sut) (assoc duplicate-user :password "hi")))))))

  (testing "success"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [test-user (mg/generate user/User)
            new-user (user/register-user (:user-controller sut) test-user)]
        (is (every? new-user [:email :username :password :token]))
        (is (= (:username test-user) (:username new-user)))
        (is (= (:email test-user) (:email new-user)))))))

(deftest update-user
  (testing "invalid input"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [input {:garbage "test"}
            db (get-in sut [:database :datasource])
            auth-user (test-utils/create-user db)
            result (user/update-user (:user-controller sut) auth-user input)]
        (is (c/contains? result :errors)))))

  (testing "username exists"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            auth-user (test-utils/create-user db)
            other-user (test-utils/create-user db)]
        (is (thrown-with-msg? Exception #"duplicate" (user/update-user (:user-controller sut) auth-user {:username (:username other-user)}))))))

  (testing "email exists"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            auth-user (test-utils/create-user db)
            other-user (test-utils/create-user db)]
        (is (thrown-with-msg? Exception #"duplicate" (user/update-user (:user-controller sut) auth-user {:email (:email other-user)})))))))
