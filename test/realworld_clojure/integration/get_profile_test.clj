(ns realworld-clojure.integration.get-profile-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [malli.core :as m]
   [malli.error :as me]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.core :as core]
   [realworld-clojure.integration.common :refer [no-auth-profile-schema
                                                 auth-profile-schema
                                                 get-profile-request
                                                 get-login-token
                                                 profiles-equal?]]
   [realworld-clojure.utils :as test-utils]))

(defn- extract-profile
  [r]
  (-> r
      (:body)
      (json/parse-string true)
      (:profile)))

(defn- validate-profile
  ([r user]
   (let [profile (extract-profile r)]
     (is (= 200 (:status r)))
     (is (true? (m/validate no-auth-profile-schema profile))
         (->> profile
              (m/explain no-auth-profile-schema)
              (me/humanize)))
     (profiles-equal? user profile)))
  ([r user following]
   (let [profile (extract-profile r)]
     (is (= 200 (:status r)))
     (is (true? (m/validate auth-profile-schema profile))
         (->> profile
              (m/explain auth-profile-schema)
              (me/humanize)))
     (profiles-equal? user profile)
     (is (= following (:following profile))))))

(deftest without-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          r (get-profile-request (:username user))]
      (validate-profile r user))))

(deftest with-auth-not-following
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          user-two (test-utils/create-user db)
          token (get-login-token user-two)
          r (get-profile-request (:username user) token)]
      (validate-profile r user false))))

(deftest with-auth-following
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          _ (test-utils/create-follows db user-one user-two)
          token (get-login-token user-one)
          r (get-profile-request (:username user-two) token)]
      (validate-profile r user-two true))))
