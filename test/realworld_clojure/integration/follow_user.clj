(ns realworld-clojure.integration.follow-user
  (:require
   [clojure.test :refer [deftest is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.integration.common :refer [follow-user-request get-login-token auth-profile-schema profiles-equal?]]
   [cheshire.core :as json]
   [malli.core :as m]
   [malli.error :as me]))

(deftest no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          r (follow-user-request (:username user))]
      (is (= 401 (:status r))))))

(deftest user-does-not-exist
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          token (get-login-token user)
          r (follow-user-request "not-a-user" token)]
      (is (= 404 (:status r))))))

(deftest success
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          token (get-login-token user-one)
          r (follow-user-request (:username user-two) token)
          profile (-> r
                      (:body)
                      (json/parse-string true)
                      (:profile))]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-profile-schema profile)) (->> profile
                                                                (m/explain auth-profile-schema)
                                                                (me/humanize)))
      (is (true? (:following profile)))
      (is (true? (profiles-equal? user-two profile))))))

(deftest already-following-user
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          token (get-login-token user-one)
          _ (follow-user-request (:username user-two) token)
          r (follow-user-request (:username user-two) token)
          profile (-> r
                      (:body)
                      (json/parse-string true)
                      (:profile))]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-profile-schema profile)) (->> profile
                                                                (m/explain auth-profile-schema)
                                                                (me/humanize)))
      (is (true? (:following profile)))
      (is (true? (profiles-equal? user-two profile))))))
