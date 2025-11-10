(ns realworld-clojure.integration.update-user
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [malli.core :as m]
   [malli.error :as me]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.core :as core]
   [realworld-clojure.integration.common :refer [update-user-request
                                                 user-response-schema
                                                 get-login-token]]
   [realworld-clojure.utils :as test-utils]))

(deftest no-authentication
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          _ (test-utils/create-user db)
          r (update-user-request {:bio "hello there"})]
      (is (= 401 (:status r))))))

(deftest wrong-authentication
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          _ (test-utils/create-user db)
          r (update-user-request {:bio "hello there"} "fake-token")]
      (is (= 403 (:status r))))))

(deftest invalid-input
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          token (get-login-token user)
          r (update-user-request {:garbage "hi"} token)]
      (is (= 422 (:status r))))))

(deftest duplicate-username
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          token (get-login-token user-two)
          r (update-user-request {:username (:username user-one)} token)]
      (is (= 409 (:status r))))))

(deftest success
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          token (get-login-token user)
          r (update-user-request {:username "Bilbo Baggins"} token)
          returned-user (-> r
                            (:body)
                            (json/parse-string true)
                            (:user))]
      (is (= 200 (:status r)))
      (is (true?
           (m/validate user-response-schema returned-user))
          (->> returned-user
               (m/explain user-response-schema)
               (me/humanize)))
      (is (= "Bilbo Baggins" (:username returned-user))))))
