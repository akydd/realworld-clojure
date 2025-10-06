(ns realworld-clojure.integration.get-profile
  (:require
   [clojure.test :refer [deftest is testing]]
   [org.httpkit.client :as http]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [malli.core :as m]
   [realworld-clojure.integration.common :refer [no-auth-profile-schema]]
   [cheshire.core :as json]))

(def url "http://localhost:8099/api/profiles")

(defn- send-request
  ([username]
   @(http/get (str url "/" username) {:headers {"Content-Type" "application/json"}}))
  ([username token]
   @(http/get (str url "/" username) {:headers {"Content-Type" "application/json"
                                                "Authorization" (str "Token " token)}})))

(deftest get-profile
  (testing "without auth")
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          r (send-request (:username user))
          body (json/parse-string (:body r) true)]
      (is (= 200 (:status r)))
      (is (= (:username user) (get-in body [:profile :username])))
      (is (true? (m/validate no-auth-profile-schema (:profile body)))))))
