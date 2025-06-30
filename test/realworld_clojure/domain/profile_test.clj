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

(defn profile-matches-user?
  "Returns true if the profile's fields match that of the user."
  [profile user]
  (let [keys [:username :bio :image]]
    (= (select-keys profile keys) (select-keys user keys))))

(defn create-user
  "Save a test user to the db."
  [db]
  (sql/insert! db :users (mg/generate user/User) test-utils/query-options))

(deftest get-profile-exists
  (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            test-user (create-user db)
            p (profile/get-profile (:profile-controller sut) (:username test-user))]
        (is (profile-matches-user? p test-user)))))

(defn create-follows
  "Save a fdllowing record to the db."
  [db follower followed]
  (sql/insert! db :follows {:user_id (:id follower) :follows (:id followed)}))

(deftest get-profile-following
  (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [db (get-in sut [:database :datasource])
            test-user (create-user db)
            follower (create-user db)
            _ (create-follows db follower test-user)
            p (profile/get-profile (:profile-controller sut) (:username test-user) follower)]
        (is (profile-matches-user? p test-user))
        (is (true? (:following p))))))

(deftest follow-user
  (test-utils/with-system [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          followed-user (create-user db)
          follower-user (create-user db)
          profile (profile/follow-user (:profile-controller sut) follower-user (:username followed-user))]
      (is (profile-matches-user? profile followed-user))
      (is (true? (:following profile))))))

(deftest unfollow-user
  (test-utils/with-system [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          followed-user (create-user db)
          follower-user (create-user db)
          _ (create-follows db follower-user followed-user)
          profile (profile/unfollow-user (:profile-controller sut) follower-user (:username followed-user))]
      (is (profile-matches-user? profile followed-user))
      (is (false? (:following profile))))))
