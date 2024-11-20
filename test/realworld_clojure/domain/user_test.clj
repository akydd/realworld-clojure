(ns realworld-clojure.domain.user-test
  (:require
   [com.stuartsierra.component :as component]
   [realworld-clojure.config-test :as config]
   [realworld-clojure.domain.user :as user]
   [next.jdbc.sql :as sql]
   [clojure.test :refer :all]
   [realworld-clojure.core :as core]
   [clojure.core :as c]))

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
          test-user {:username "user2" :email "user2@user" :password "password"}
          _ (sql/insert! db :users test-user)]
      (is (thrown-with-msg? Exception #"duplicate" (user/register-user (:user-controller sut) test-user))))))
