(ns realworld-clojure.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]))

(defn read-config []
  (aero/read-config (io/resource "config.edn")))

(read-config)
