(ns realworld-clojure.domain.article-test
  (:require
   [clojure.test :refer :all]
   [realworld-clojure.utils :as u]
   [realworld-clojure.config-test :as config]
   [malli.generator :as mg]
   [realworld-clojure.core :as core]
   [realworld-clojure.domain.article :as article]))

(defn article-matches-input?
  [a input]
  (let [keys [:title :description :body]]
    (= (select-keys input keys) (select-keys a keys))))

(defn article-matches-update-input?
  [a update-input]
  (let [ks (keys update-input)]
    (= (select-keys update-input ks) (select-keys a ks))))

(deftest create-article-not-followed
  (u/with-system [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (u/create-user db)
          input (mg/generate article/Article)
          a (article/create-article (:article-controller sut) input author)]
      (is (article-matches-input? a input))
      (is (u/profile-matches-user? (:author a) author))
      (is (not (nil? (:createdat a))))
      (is (false? (get-in a [:author :following]))))))

(deftest update-article-authentication-passed
  (u/with-system [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (u/create-user db)
          a (u/create-article db (:id author))
          input (mg/generate article/ArticleUpdate)
          updated (article/update-article (:article-controller sut) (:slug a) input author)]
      (is (article-matches-update-input? updated input))
      (is (u/profile-matches-user? (:author updated) author))
      (is (not (nil? (:updatedat a))))
      (is (= false (get-in updated [:author :following]))))))
