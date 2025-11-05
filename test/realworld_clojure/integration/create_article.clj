(ns realworld-clojure.integration.create-article
  (:require
   [clojure.test :refer [deftest is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [malli.generator :as mg]
   [realworld-clojure.integration.common :refer [create-article-request
                                                 get-login-token
                                                 auth-article-schema
                                                 validate-article-vs-input
                                                 validate-slug]]
   [realworld-clojure.domain.article :as article]
   [malli.core :as m]
   [malli.error :as me]
   [cheshire.core :as json]))

(deftest no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [article (mg/generate article/article-schema)
          r (create-article-request article)]
      (is (= 401 (:status r))))))

(deftest invalid-input
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          token (get-login-token user)
          r (create-article-request {:garbage "hi"} token)]
      (is (= 422 (:status r))))))

(deftest duplicate-slug
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article-one (test-utils/create-article db (:id user))
          input (assoc (mg/generate article/article-schema)
                       :title (:title article-one))
          token (get-login-token user)
          r (create-article-request input token)]
      (is (= 409 (:status r))))))

(deftest no-tags
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          input (dissoc (mg/generate article/article-schema) :tag-list)
          user (test-utils/create-user db)
          token (get-login-token user)
          r (create-article-request input token)
          article (:article (json/parse-string (:body r) true))]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-article-schema article))
          (->> article
               (m/explain auth-article-schema)
               (me/humanize)))
      (validate-article-vs-input article input)
      (validate-slug article)
      (is (zero? (count (:tag-list article)))))))

(deftest empty-tags
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          input (assoc (mg/generate article/article-schema) :tag-list [])
          user (test-utils/create-user db)
          token (get-login-token user)
          r (create-article-request input token)]
      (is (= 422 (:status r))))))

(deftest new-tags
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          new-tags ["tag-one" "tag-two"]
          input (assoc (mg/generate article/article-schema) :tag-list new-tags)
          user (test-utils/create-user db)
          token (get-login-token user)
          r (create-article-request input token)
          article (-> r
                      (:body)
                      (json/parse-string true)
                      (:article))]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-article-schema article))
          (->> article
               (m/explain auth-article-schema)
               (me/humanize)))
      (validate-article-vs-input article input)
      (validate-slug article))))

(deftest duplicate-tags-in-input
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          new-tags ["tag-one" "tag-one"]
          input (assoc (mg/generate article/article-schema) :tag-list new-tags)
          user (test-utils/create-user db)
          token (get-login-token user)
          r (create-article-request input token)
          article (-> r
                      (:body)
                      (json/parse-string true)
                      (:article))]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-article-schema article))
          (->> article
               (m/explain auth-article-schema)
               (me/humanize)))
      (validate-article-vs-input article input)
      (validate-slug article))))

(deftest existing-tag
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          tags ["tag-one"]
          input (assoc (mg/generate article/article-schema) :tag-list tags)
          user (test-utils/create-user db)
          _ (test-utils/create-article db (:id user) {:tag-list tags})
          token (get-login-token user)
          r (create-article-request input token)
          article (-> r
                      (:body)
                      (json/parse-string true)
                      (:article))]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-article-schema article))
          (->> article
               (m/explain auth-article-schema)
               (me/humanize)))
      (validate-article-vs-input article input)
      (validate-slug article))))
