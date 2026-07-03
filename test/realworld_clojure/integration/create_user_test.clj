(ns realworld-clojure.integration.create-user-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [malli.generator :as mg]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.core :as core]
   [realworld-clojure.domain.user :as user]
   [realworld-clojure.integration.common :refer [register-request]]
   [realworld-clojure.utils :as test-utils]))

(deftest invalid-input
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (register-request {:garbage "hi"})]
      (is (= 422 (:status r))))))

(deftest duplicate-username
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (assoc (mg/generate user/user-schema) :username (:username user-one))
          r (register-request user-two)
          error (-> r
                    (:body)
                    (json/parse-string true)
                    (get-in [:errors :username 0]))]
      (is (= 409 (:status r)))
      (is (= "has already been taken" error)))))
