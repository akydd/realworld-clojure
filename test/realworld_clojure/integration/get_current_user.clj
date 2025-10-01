(ns realworld-clojure.integration.get-current-user
  (:require
   [org.httpkit.client :as http]
   [clojure.test :refer [deftest testing is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]))

(def url "http://localhost:8099/api/user")

(defn send-request
  [token]
  @(http/get url {:headers {"Content-Type" "application/json" "Authorization" (str "Token " token)}}))

(deftest get-current-user
  (testing "no authentication"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r @(http/get url {:headers {"Content-Type" "application/json"}})]
        (is (= 401 (:status r))))))

  (testing "invalid token"
    (test-utils/with-system
      [sut (core/new-system (config/read-test-config))]
      (let [r (send-request "bleh")]
        (is (= 401 (:status r))))))

  (testing "success"))
