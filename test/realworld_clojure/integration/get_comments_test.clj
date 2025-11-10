(ns realworld-clojure.integration.get-comments-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is]]
   [malli.core :as m]
   [malli.error :as me]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.core :as core]
   [realworld-clojure.integration.common :refer [get-comments-request
                                                 get-login-token
                                                 no-auth-comment-schema
                                                 auth-comment-schema
                                                 assert-comments-match]]
   [realworld-clojure.utils :as test-utils]))

(deftest no-article
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (get-comments-request "no-article")]
      (is (= 404 (:status r))))))

(deftest article-has-no-comments
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user (test-utils/create-user db)
          article (test-utils/create-article db (:id user))
          r (get-comments-request (:slug article))
          comments (-> r
                       (:body)
                       (json/parse-string true)
                       (:comments))]
      (is (= 200 (:status r)))
      (is (empty? comments)))))

(defn- validate-comments
  ([response expected-comments authors]
   (let [comments (-> response
                      (:body)
                      (json/parse-string true)
                      (:comments))]
     (is (= 200 (:status response)))
     (doseq [c comments]
       (is (m/validate no-auth-comment-schema c)
           (->> c
                (m/explain no-auth-comment-schema c)
                (me/humanize))))
     (assert-comments-match (map (fn [a b c] {:expected a
                                              :actual b
                                              :author c})
                                 expected-comments
                                 comments
                                 authors))))
  ([response expected-comments authors follows]
   (let [comments (-> response
                      (:body)
                      (json/parse-string true)
                      (:comments))]
     (is (= 200 (:status response)))
     (doseq [c comments]
       (is (m/validate auth-comment-schema c)
           (->> c
                (m/explain auth-comment-schema c)
                (me/humanize))))
     (assert-comments-match (map (fn [a b c d] {:expected a
                                                :actual b
                                                :author c
                                                :follows d})
                                 expected-comments
                                 comments
                                 authors
                                 follows)))))

(deftest no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          article (test-utils/create-article db (:id user-one))
          c-1 (test-utils/create-comment db (:id article) (:id user-one))
          c-2 (test-utils/create-comment db (:id article) (:id user-two))
          r (get-comments-request (:slug article))]
      (validate-comments r [c-1 c-2] [user-one user-two]))))

(deftest with-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          _ (test-utils/create-follows db user-one user-two)
          article (test-utils/create-article db (:id user-one))
          c-1 (test-utils/create-comment db (:id article) (:id user-one))
          c-2 (test-utils/create-comment db (:id article) (:id user-two))
          token (get-login-token user-one)
          r (get-comments-request (:slug article) token)]
      (validate-comments r [c-1 c-2] [user-one user-two] [false true]))))
