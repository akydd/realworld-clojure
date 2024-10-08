(ns realworld-clojure.adapters.db-test
  (:require [clj-test-containers.core :as tc]
            [clojure.test :refer :all]
            [next.jdbc :as jdbc]))
(deftest testing-db-attempt
  (testing "Does the container woek?"
    (let [pw "password"
          test-db (-> (tc/create {:image-name "postgres:12.1"
                                  :exposed-ports [5432]
                                  :env-vars {"POSTGRES_PASSWORD" pw}})
                      tc/start!)]

      (let [ds (jdbc/get-datasource {:dbtype "postgresql"
                                     :dbname "postgres" 
                                     :user "postgres"
                                     :password pw
                                     :host (:host test-db)
                                     :port (get (:mapped-ports test-db) 5432)})]

        (is (= [{:one 1 :two 2}] (with-open [conn (jdbc/get-connection ds)]
                                   (jdbc/execute! conn ["SELECT 1 ONE, 2 TWO"])))))

      (tc/stop! test-db))))
