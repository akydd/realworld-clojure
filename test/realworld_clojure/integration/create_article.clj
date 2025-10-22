(ns realworld-clojure.integration.create-article
  (:require
   [clojure.test :refer [deftest is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [malli.generator :as mg]
   [realworld-clojure.integration.common :refer [create-article-request get-login-token auth-article-schema article-matches-input? slug-is-correct? get-tags-request]]
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
          input (assoc (mg/generate article/article-schema) :title (:title article-one))
          token (get-login-token user)
          r (create-article-request input token)]
      (is (= 409 (:status r))))))

(deftest success-no-tags
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          input (mg/generate article/article-schema)
          user (test-utils/create-user db)
          token (get-login-token user)
          r (create-article-request input token)
          article (:article (json/parse-string (:body r) true))]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-article-schema article)) (->> article
                                                                (m/explain auth-article-schema)
                                                                (me/humanize)))
      (is (true? (article-matches-input? article input)))
      (is (slug-is-correct? article)))))

(deftest success-empty-tags
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          input (assoc (mg/generate article/article-schema) :tag-list [])
          user (test-utils/create-user db)
          token (get-login-token user)
          r (create-article-request input token)
          body (-> r
                   (:body)
                   (json/parse-string true))
          article (:article body)]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-article-schema article)) (->> article
                                                                (m/explain auth-article-schema)
                                                                (me/humanize))))))

(deftest success-existing-tags)

(deftest success-new-and-existing-tags)
