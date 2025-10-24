(ns realworld-clojure.integration.update-article
  (:require
   [clojure.test :refer [deftest is]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [malli.generator :as mg]
   [realworld-clojure.domain.article :as article]
   [realworld-clojure.integration.common :refer [update-article-request get-login-token auth-article-schema]]
   [malli.core :as m]
   [cheshire.core :as json]
   [malli.error :as me]))

(deftest no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [update (mg/generate article/article-update-schema)
          r (update-article-request "slug" update)]
      (is (= 401 (:status r))))))

(deftest not-found
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          update (mg/generate article/article-update-schema)
          r (update-article-request "no-article-here" update (get-login-token user))]
      (is (= 404 (:status r))))))

(deftest invalid-input
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article (test-utils/create-article db (:id user))
          update {:garbage "hi"}
          token (get-login-token user)
          r (update-article-request (:slug article) update token)]
      (is (= 422 (:status r))))))

(deftest different-author
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))
     (let [db (get-in sut [:database :datasource])
           user-one (test-utils/create-user db)
           user-two (test-utils/create-user db)
           article (test-utils/create-article db (:id user-one))
           update (mg/generate article/article-update-schema)
           token (get-login-token user-two)
           r (update-article-request (:slug article) update token)]
       (is (= 403 (:status r))))]))

(deftest new-title-makes-new-slug
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article (test-utils/create-article db (:id user))
          new-title (str (:title article) " updated")
          update {:title new-title}
          token (get-login-token user)
          r (update-article-request (:slug article) update token)
          updated-article (:article (json/parse-string (:body r) true))]
      (is (= 200 (:status r)))
      (is (true? (m/validate auth-article-schema updated-article)) (->> updated-article
                                                                        (m/explain auth-article-schema)
                                                                        (me/humanize)))
      (is (= (:title updated-article) new-title))
      (is (= (:slug updated-article) (str (:slug article) "-updated"))))))

(deftest duplicate-slug
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article-one (test-utils/create-article db (:id user))
          article-two (test-utils/create-article db (:id user))
          update {:title (:title article-one)}
          token (get-login-token user)
          r (update-article-request (:slug article-two) update token)]
      (is (= 409 (:status r))))))

(deftest success
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article (test-utils/create-article db (:id user))
          update (mg/generate article/article-update-schema)
          token (get-login-token user)
          r (update-article-request (:slug article) update token)]
      (is (= 200 (:status r))))))
