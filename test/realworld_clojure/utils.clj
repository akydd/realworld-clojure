(ns realworld-clojure.utils
  (:require
   [com.stuartsierra.component :as component]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]))

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
