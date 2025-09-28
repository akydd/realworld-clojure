(ns realworld-clojure.domain.profile-test
  (:require
   [clojure.test :refer :all]
   [realworld-clojure.core :as core]
   [realworld-clojure.utils :as u]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.domain.profile :as profile]))

(deftest get-profile-not-exists
  (u/with-system
    [sut (core/new-system (config/read-test-config))]
    (is (nil? (profile/get-profile (:profile-controller sut) "hello")))))

(deftest get-profile
  (u/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          test-user (u/create-user db)
          p (profile/get-profile (:profile-controller sut) (:username test-user))]
      (is (u/profile-matches-user? p test-user)))))

(deftest get-profile-authenticated
  (u/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          test-user (u/create-user db)
          follower (u/create-user db)
          _ (u/create-follows db follower test-user)
          p (profile/get-profile (:profile-controller sut) (:username test-user) follower)]
      (is (u/profile-matches-user? p test-user))
      (is (true? (:following p))))))

(deftest follow-user
  (u/with-system [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          followed-user (u/create-user db)
          follower-user (u/create-user db)
          profile (profile/follow-user (:profile-controller sut) follower-user (:username followed-user))]
      (is (u/profile-matches-user? profile followed-user))
      (is (true? (:following profile))))))

(deftest unfollow-user
  (u/with-system [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          followed-user (u/create-user db)
          follower-user (u/create-user db)
          _ (u/create-follows db follower-user followed-user)
          profile (profile/unfollow-user (:profile-controller sut) follower-user (:username followed-user))]
      (is (u/profile-matches-user? profile followed-user))
      (is (false? (:following profile))))))
