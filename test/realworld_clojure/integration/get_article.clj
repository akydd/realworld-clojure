(ns realworld-clojure.integration.get-article
  (:require
   [clojure.test :refer [deftest is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.integration.common :refer [get-article-request
                                                 get-login-token
                                                 no-auth-article-schema
                                                 auth-article-schema
                                                 article-matches-article?]]
   [malli.core :as m]
   [cheshire.core :as json]
   [malli.error :as me]))

(deftest does-not-exist-no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (get-article-request "no-slug")]
      (is (= 404 (:status r))))))

(deftest does-not-exist-authenticated
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          token (get-login-token user)
          r (get-article-request "no-slug" token)]
      (is (= 404 (:status r))))))

(deftest no-auth-no-tags
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article (test-utils/create-article db (:id user) {:tag-list []})
          r (get-article-request (:slug article))
          returned-article (-> r
                               (:body)
                               (json/parse-string true)
                               (:article))]
      (is (= 200 (:status r)))
      (is (true? (m/validate no-auth-article-schema returned-article))
          (->> returned-article
               (m/explain no-auth-article-schema)
               (me/humanize)))
      (article-matches-article? article user returned-article))))

(deftest authenticated-not-following-author
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          article (test-utils/create-article db (:id author))
          user (test-utils/create-user db)
          token (get-login-token user)
          r (get-article-request (:slug article) token)
          returned-article (:article (json/parse-string (:body r) true))]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-article-schema returned-article))
          (->> returned-article
               (m/explain auth-article-schema)
               (me/humanize)))
      (article-matches-article? article author returned-article false))))

(deftest authenticated-following-author
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          article (test-utils/create-article db (:id author))
          user (test-utils/create-user db)
          _ (test-utils/create-follows db user author)
          r (get-article-request (:slug article) (get-login-token user))
          returned-article (-> r
                               (:body)
                               (json/parse-string true)
                               (:article))]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-article-schema returned-article))
          (->> returned-article
               (m/explain auth-article-schema)
               (me/humanize)))
      (article-matches-article? article author returned-article true))))

(deftest single-tag
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          tags ["my-tag"]
          article (test-utils/create-article db (:id user) {:tag-list tags})
          r (get-article-request (:slug article))
          returned-article (-> r
                               (:body)
                               (json/parse-string true)
                               (:article))]
      (is (= 200 (:status r)))
      (is (true? (m/validate no-auth-article-schema returned-article))
          (->> returned-article
               (m/explain no-auth-article-schema)
               (me/humanize)))
      (article-matches-article? article user returned-article))))

(deftest authenticated-single-tag
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          tags ["my-tag"]
          article (test-utils/create-article db (:id user) {:tag-list tags})
          r (get-article-request (:slug article) (get-login-token user))
          returned-article (-> r
                               (:body)
                               (json/parse-string true)
                               (:article))]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-article-schema returned-article))
          (->> returned-article
               (m/explain no-auth-article-schema)
               (me/humanize)))
      (article-matches-article? article user returned-article false))))

(deftest multiple-tags
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          tags ["my-tag" "my-other-tag"]
          article (test-utils/create-article db (:id user) {:tag-list tags})
          r (get-article-request (:slug article))
          returned-article (-> r
                               (:body)
                               (json/parse-string true)
                               (:article))]
      (is (= 200 (:status r)))
      (is (true? (m/validate no-auth-article-schema returned-article))
          (->> returned-article
               (m/explain no-auth-article-schema)
               (me/humanize)))
      (article-matches-article? article user returned-article))))

(deftest authenticated-multiple-tags
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          tags ["my-tag" "my-other-tag"]
          article (test-utils/create-article db (:id user) {:tag-list tags})
          r (get-article-request (:slug article) (get-login-token user))
          returned-article (-> r
                               (:body)
                               (json/parse-string true)
                               (:article))]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-article-schema returned-article))
          (->> returned-article
               (m/explain no-auth-article-schema)
               (me/humanize)))
      (article-matches-article? article user returned-article false))))
