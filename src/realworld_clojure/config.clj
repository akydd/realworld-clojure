(ns realworld-clojure.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]))

(defn read-config
  "Read the system configs"
  []
  (aero/read-config (io/resource "config.edn")))
