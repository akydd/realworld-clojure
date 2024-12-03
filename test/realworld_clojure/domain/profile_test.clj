(ns realworld-clojure.domain.profile-test
  (:require
   [clojure.test :refer :all]
   [realworld-clojure.core :as core]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.domain.profile :as profile]))

(deftest get-profile-not-exists
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (is (nil? (profile/get-profile (:profile-controller sut) "hello")))))
