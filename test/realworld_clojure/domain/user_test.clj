(ns realworld-clojure.domain.user-test
  (:require
   [com.stuartsierra.component :as component]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.domain.user :as user]
   [malli.generator :as mg]
   [next.jdbc.sql :as sql]
   [next.jdbc.result-set :as rs]
   [clojure.test :refer :all]
   [realworld-clojure.core :as core]
   [clojure.core :as c]))

(def query-options
  {:builder-fn rs/as-unqualified-lower-maps})

(defn clear-ds
  "Delete all data from datasource"
  [ds]
  (sql/query ds ["truncate comments, articles, users cascade"]))

(defmacro with-system
  [[bound-var binding-expr] & body]
  `(let [~bound-var (component/start ~binding-expr)]
     (try
       ~@body
       (finally
         (clear-ds (get-in ~bound-var [:database :datasource]))
         (component/stop ~bound-var)))))

(deftest register-invalid-input
  (with-system
    [sut (core/new-system (config/read-test-config))]
    (is (c/contains? (user/register-user (:user-controller sut) {}) :errors))))

(deftest register-already-exists
  (with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          test-user (mg/generate user/User)
          _ (sql/insert! db :users test-user)]
      (is (thrown-with-msg? Exception #"duplicate" (user/register-user (:user-controller sut) test-user))))))

(deftest register-success
  (with-system
    [sut (core/new-system (config/read-test-config))]
    (let [test-user (mg/generate user/User)
          new-user (user/register-user (:user-controller sut) test-user)]
      (is (every? new-user  [:email :username :password :token]))
      (is (= (:username test-user) (:username new-user)))
      (is (= (:email test-user) (:email new-user))))))

(deftest update-invalid-input
  (with-system
    [sut (core/new-system (config/read-test-config))]
    (let [input {:garbage "test"}
          db (get-in sut [:database :datasource])
          test-user (mg/generate user/User)
          saved-user (sql/insert! db :users test-user query-options)
          result (user/update-user (:user-controller sut) saved-user input)]
      (is (c/contains? result :errors)))))

(deftest update-username-exists
  (with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          auth-user (sql/insert! db :users {:username "auth" :email "auth" :password "auth"} query-options)
          _ (sql/insert! db :users {:username "taken" :email "taken" :password "taken"})]
      (is (thrown-with-msg? Exception #"duplicate" (user/update-user (:user-controller sut) auth-user {:username "taken"}))))))

(deftest update-email-exists
  (with-system
    [sut (core/new-system (config/read-test-config))]
    (let [db (get-in sut [:database :datasource])
          auth-user (sql/insert! db :users {:username "auth" :email "auth" :password "auth"} query-options)
          _ (sql/insert! db :users {:username "taken" :email "taken" :password "taken"})]
      (is (thrown-with-msg? Exception #"duplicate" (user/update-user (:user-controller sut) auth-user {:email "taken"}))))))
