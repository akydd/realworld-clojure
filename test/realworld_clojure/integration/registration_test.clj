(ns realworld-clojure.integration.registration-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [malli.core :as m]
   [malli.error :as me]
   [malli.generator :as mg]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.core :as core]
   [realworld-clojure.domain.user :as user]
   [realworld-clojure.integration.common :refer [register-request
                                                 user-response-schema]]
   [realworld-clojure.utils :as test-utils]))

(deftest invalid-input
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (register-request {})]
      (is (= 422 (:status r))))))

(deftest duplicate-username
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          r (register-request user)]
      (is (= 409 (:status r))))))

(deftest success
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [input (mg/generate user/user-schema)
          r (register-request input)
          user (-> r
                   (:body)
                   (json/parse-string true)
                   (:user))]
      (is (= 200 (:status r)))
      (is (true? (m/validate user-response-schema user))
          (->> user
               (m/explain user-response-schema)
               (me/humanize)))
      (is (= (:username input) (:username user)))
      (is (= (:email input) (:email user)))
      (is (some? (:token user))))))
