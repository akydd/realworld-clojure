(ns realworld-clojure.integration.get-tags
  (:require
   [clojure.test :refer [deftest is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.integration.common :refer [get-tags-request tags-schema]]
   [malli.core :as m]
   [cheshire.core :as json]
   [malli.error :as me]))

(defn- validate-response
  [response expected-tags]
  (let [body (-> response
                 (:body)
                 (json/parse-string true))]
    (is (= 200 (:status response)))
    (is (true? (m/validate tags-schema body)) (->> body
                                                   (m/explain tags-schema)
                                                   (me/humanize)))
    (is (= expected-tags (:tags body)) "tags do not match")))

(deftest no-tags
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          r (get-tags-request)]
      (validate-response r []))))

(deftest some-tags-ordered
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          _ (test-utils/create-article db (:id author) {:tag-list ["tag-one" "tag-two" "tag-three"]})
          r (get-tags-request)]
      (validate-response r ["tag-one" "tag-three" "tag-two"]))))
