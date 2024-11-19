(ns realworld-clojure.domain.user-test
  (:require
   [com.stuartsierra.component :as component]
   [realworld-clojure.config-test :as config]
   [clojure.test :refer :all]

   [realworld-clojure.core :as core]))

(defmacro with-system
  [[bound-var binding-expr] & body]
  `(let [~bound-var (component/start ~binding-expr)]
     (try
       ~@body
       (finally
         (component/stop ~bound-var)))))

(deftest greeting-test
  (with-system 
    [sut (core/new-system (config/read-test-config))]
    (is (= 0 0))))
