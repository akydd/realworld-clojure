(ns realworld-clojure.config-test
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]))

(defn read-test-config []
  (aero/read-config (io/resource "test-config.edn")))
