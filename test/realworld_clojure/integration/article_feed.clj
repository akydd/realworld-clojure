(ns realworld-clojure.integration.article-feed
  (:require
   [clojure.test :refer [deftest is]]
   [realworld-clojure.integration.common :refer [article-feed-request get-login-token profiles-equal? article-matches-feed? multiple-auth-article-schema]]
   [realworld-clojure.utils :as test-utils]
   [realworld-clojure.core :as core]
   [realworld-clojure.config-test :as config]
   [cheshire.core :as json]
   [malli.core :as m]
   [malli.error :as me]
   [java-time.api :as jt]
   [clojure.pprint :as pp]))

(deftest no-auth
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [r (article-feed-request "")]
      (is (= 401 (:status r))))))

(deftest following-no-one
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          _ (test-utils/create-article db (:id author))
          user (test-utils/create-user db)
          token (get-login-token user)
          r (article-feed-request "" token)
          body (-> r
                   (:body)
                   (json/parse-string true))
          articles (:articles body)]
      (is (= 200 (:status r)))
      (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                      (m/explain multiple-auth-article-schema)
                                                                      (me/humanize)))
      (is (zero? (count articles)))
      (is (zero? (:articlesCount body))))))

;; TODO: turn this into a custom assertion.
(defn- validate-response [response expected-articles expected-authors]
  (let [body (-> response
                 (:body)
                 (json/parse-string true))]
    (is (= 200 (:status response)))
    (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                    (m/explain multiple-auth-article-schema)
                                                                    (me/humanize)))
    ;;(is (= expected-articles (:articles body)))
    (is (= (count expected-articles) (:articlesCount body)))
    (is (every? true? (map article-matches-feed? expected-articles expected-authors (:articles body))) (str "Expected articles: " (pp/pprint expected-articles) " but got " (pp/pprint (:articles body))))))

(deftest following-user-with-no-articles
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          user-three (test-utils/create-user db)
          _ (test-utils/create-article db (:id user-one))
          _ (test-utils/create-follows db user-two user-three)
          r (article-feed-request "" (get-login-token user-two))]
      (validate-response r [] []))))

(deftest following-user-one-article-no-tags
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          user-three (test-utils/create-user db)
          _ (test-utils/create-article db (:id user-one))
          article (test-utils/create-article db (:id user-three) {:tag-list []})
          _ (test-utils/create-follows db user-two user-three)
          r (article-feed-request "" (get-login-token user-two))]
      (validate-response r [article] [user-three]))))

(deftest following-user-multiple-articles-no-tags
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          user-three (test-utils/create-user db)
          _ (test-utils/create-article db (:id user-one))
          now (jt/local-date-time)
          article-one (test-utils/create-article db (:id user-three) {:title "article-one"
                                                                      :tag-list []
                                                                      :createdat (jt/- now (jt/days 1))})
          article-two (test-utils/create-article db (:id user-three) {:title "article-two"
                                                                      :tag-list []
                                                                      :createdat now})
          _ (test-utils/create-follows db user-two user-three)
          r (article-feed-request "" (get-login-token user-two))]
      (validate-response r [article-two article-one] [user-three user-three]))))

(deftest following-user-one-article-one-tag
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          user-three (test-utils/create-user db)
          _ (test-utils/create-article db (:id user-one))
          article (test-utils/create-article db (:id user-three) {:tag-list ["one-tag"]})
          _ (test-utils/create-follows db user-two user-three)
          token (get-login-token user-two)
          r (article-feed-request "" token)]
      (validate-response r [article] [user-three]))))

(deftest following-user-multiple-articles-one-tag-same
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          user-three (test-utils/create-user db)
          _ (test-utils/create-article db (:id user-one))
          now (jt/local-date-time)
          article-one (test-utils/create-article db (:id user-three) {:title "article-one"
                                                                      :tag-list ["my-tag"]
                                                                      :createdat (jt/- now (jt/days 1))})
          article-two (test-utils/create-article db (:id user-three) {:title "article-two"
                                                                      :tag-list ["my-tag"]
                                                                      :createdat now})
          _ (test-utils/create-follows db user-two user-three)
          r (article-feed-request "" (get-login-token user-two))]
      (validate-response r [article-two article-one] [user-three user-three]))))

(deftest following-user-multiple-articles-one-tag-different
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          user-three (test-utils/create-user db)
          _ (test-utils/create-article db (:id user-one))
          now (jt/local-date-time)
          article-one (test-utils/create-article db (:id user-three) {:title "article-one"
                                                                      :tag-list ["my-tag"]
                                                                      :createdat (jt/- now (jt/days 1))})
          article-two (test-utils/create-article db (:id user-three) {:title "article-two"
                                                                      :tag-list ["my-other-tag"]
                                                                      :createdat now})
          _ (test-utils/create-follows db user-two user-three)
          r (article-feed-request "" (get-login-token user-two))]
      (validate-response r [article-two article-one] [user-three user-three]))))

(deftest following-user-one-article-multiple-tags
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          user-one (test-utils/create-user db)
          user-two (test-utils/create-user db)
          user-three (test-utils/create-user db)
          _ (test-utils/create-article db (:id user-one))
          article (test-utils/create-article db (:id user-three) {:tag-list ["one-tag" "two-tag" "three-tag"]})
          _ (test-utils/create-follows db user-two user-three)
          token (get-login-token user-two)
          r (article-feed-request "" token)]
      (validate-response r [article] [user-three]))))

(deftest following-multiple-authors
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author-one (test-utils/create-user db)
          author-two (test-utils/create-user db)
          user (test-utils/create-user db)
          _ (test-utils/create-follows db user author-one)
          _ (test-utils/create-follows db user author-two)
          now (jt/local-date-time)
          a1 (test-utils/create-article db (:id author-one) {:createdat (jt/- now (jt/days 1))})
          a2 (test-utils/create-article db (:id author-two) {:createdat now})
          token (get-login-token user)
          r (article-feed-request "" token)]
      (validate-response r [a2 a1] [author-two author-one]))))

(deftest order-by-most-recently-created
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          user (test-utils/create-user db)
          _ (test-utils/create-follows db user author)
          now (jt/local-date-time)
          a1 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 2))})
          a2 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 1))})
          a3 (test-utils/create-article db (:id author) {:createdat now})
          token (get-login-token user)
          r (article-feed-request "" token)
          body (-> r
                   (:body)
                   (json/parse-string true))
          articles (:articles body)]
      (is (= 200 (:status r)))
      (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                      (m/explain multiple-auth-article-schema)
                                                                      (me/humanize)))
      (is (= 3 (:articlesCount body)))
      (is (= 3 (count articles)))
      (is (= (:slug a3) (:slug (first articles))))
      (is (= (:slug a2) (:slug (second articles))))
      (is (= (:slug a1) (:slug (nth articles 2)))))))

(deftest order-by-most-recently-updated
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          user (test-utils/create-user db)
          _ (test-utils/create-follows db user author)
          now (jt/local-date-time)
          a1 (test-utils/create-article db (:id author) {:updatedat (jt/- now (jt/days 2))})
          a2 (test-utils/create-article db (:id author) {:updatedat (jt/- now (jt/days 1))})
          a3 (test-utils/create-article db (:id author) {:updatedat now})
          token (get-login-token user)
          r (article-feed-request "" token)
          body (-> r
                   (:body)
                   (json/parse-string true))
          articles (:articles body)]
      (is (= 200 (:status r)))
      (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                      (m/explain multiple-auth-article-schema)
                                                                      (me/humanize)))
      (is (= 3 (:articlesCount body)))
      (is (= 3 (count articles)))
      (is (= (:slug a3) (:slug (first articles))))
      (is (= (:slug a2) (:slug (second articles))))
      (is (= (:slug a1) (:slug (nth articles 2)))))))

(deftest order-by-most-recently-updated-then-created
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          user (test-utils/create-user db)
          _ (test-utils/create-follows db user author)
          now (jt/local-date-time)
          a1 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 3))
                                                         :updatedat now})
          a2 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 2))})
          a3 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 1))})
          token (get-login-token user)
          r (article-feed-request "" token)
          body (-> r
                   (:body)
                   (json/parse-string true))
          articles (:articles body)]
      (is (= 200 (:status r)))
      (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                      (m/explain multiple-auth-article-schema)
                                                                      (me/humanize)))
      (is (= 3 (:articlesCount body)))
      (is (= 3 (count articles)))
      (is (= (:slug a1) (:slug (first articles))))
      (is (= (:slug a3) (:slug (second articles))))
      (is (= (:slug a2) (:slug (nth articles 2)))))))

(deftest valid-limit-filter
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          user (test-utils/create-user db)
          _ (test-utils/create-follows db user author)
          now (jt/local-date-time)
          a1 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 2))})
          a2 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 1))})
          a3 (test-utils/create-article db (:id author) {:createdat now})
          token (get-login-token user)
          r (article-feed-request "?limit=2" token)
          body (-> r
                   (:body)
                   (json/parse-string true))
          articles (:articles body)]
      (is (= 200 (:status r)))
      (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                      (m/explain multiple-auth-article-schema)
                                                                      (me/humanize)))
      (is (= 2 (:articlesCount body)))
      (is (= 2 (count articles)))
      (is (= (:slug a3) (:slug (first articles))))
      (is (= (:slug a2) (:slug (second articles)))))))

(deftest default-limit
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          user (test-utils/create-user db)
          _ (test-utils/create-follows db user author)
          _ (dotimes [i 25]
              (test-utils/create-article db (:id author) {:tag-list [(str "tag-" i)]
                                                          :title (str "article-" i)}))
          r (article-feed-request "" (get-login-token user))
          body (-> r
                   (:body)
                   (json/parse-string true))
          articles (:articles body)]
      (is (= 200 (:status r)))
      (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                      (m/explain multiple-auth-article-schema)
                                                                      (me/humanize)))
      (is (= 20 (:articlesCount body)))
      (is (= 20 (count articles))))))

(deftest invalid-limit-filter
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          user (test-utils/create-user db)
          _ (test-utils/create-follows db user author)
          _ (test-utils/create-article db (:id author))
          token (get-login-token user)
          r (article-feed-request "?limit=0" token)]
      (is (= 422 (:status r))))))

(deftest valid-offset-filter
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          user (test-utils/create-user db)
          _ (test-utils/create-follows db user author)
          now (jt/local-date-time)
          a1 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 2))})
          a2 (test-utils/create-article db (:id author) {:createdat (jt/- now (jt/days 1))})
          a3 (test-utils/create-article db (:id author) {:createdat now})
          token (get-login-token user)
          r (article-feed-request "?offset=1" token)
          body (-> r
                   (:body)
                   (json/parse-string true))
          articles (:articles body)]
      (is (= 200 (:status r)))
      (is (true? (m/validate multiple-auth-article-schema body)) (->> body
                                                                      (m/explain multiple-auth-article-schema)
                                                                      (me/humanize)))
      (is (= 2 (:articlesCount body)))
      (is (= 2 (count articles)))
      (is (= (:slug a2) (:slug (first articles))))
      (is (= (:slug a1) (:slug (second articles)))))))

(deftest invalid-offset-filter
  (test-utils/with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          author (test-utils/create-user db)
          user (test-utils/create-user db)
          _ (test-utils/create-follows db user author)
          _ (test-utils/create-article db (:id author))
          token (get-login-token user)
          r (article-feed-request "?offset=-1" token)]
      (is (= 422 (:status r))))))

