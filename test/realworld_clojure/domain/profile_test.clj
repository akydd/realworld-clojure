(ns realworld-clojure.domain.profile-test
  (:require
   [clojure.test :refer :all]
   [realworld-clojure.core :as core]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.domain.profile :as profile]
   [realworld-clojure.domain.user :as user]
   [malli.generator :as mg]
   [next.jdbc.sql :as sql]))

(deftest get-profile-not-exists
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (is (nil? (profile/get-profile (:profile-controller sut) "hello")))))

(deftest get-profile-exists
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          test-user (mg/generate user/User)
          _ (sql/insert! db :users test-user)
          p (profile/get-profile (:profile-controller sut) (:username test-user))]
      (is (= (:username test-user) (:username p)))
      (is (= (:bio test-user) (:bio p)))
      (is (= (:image test-user) (:image p))))))

(deftest get-profile-following
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          test-user (mg/generate user/User)
          following-user (mg/generate user/User)
          i (sql/insert! db :users test-user test-utils/query-options)
          j (sql/insert! db :users following-user test-utils/query-options)
          _ (sql/insert! db :follows {:user_id (:id j) :follows (:id i)})
          p (profile/get-profile (:profile-controller sut) (:username test-user) j)]
      (is (= (:username test-user) (:username p)))
      (is (true? (:following p)))
      (is (= (:bio test-user) (:bio p))))))
