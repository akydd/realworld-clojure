(ns user
  (:require
   [realworld-clojure.core :as core]
   [clojure.core :as c]
   [clojure.tools.namespace.repl :as repl]
   [realworld-clojure.adapters.db :as db]
   [realworld-clojure.domain.profile :as profile]
   [realworld-clojure.domain.article :as article]
   [realworld-clojure.config :as config]))

(def system nil)

(defn init
  "Constructs the dev system"
  []
  (alter-var-root #'system (c/constantly (core/new-system (config/read-config)))))

(defn start []
  (alter-var-root #'system core/start))

(defn stop []
  (alter-var-root #'system
                  (fn [s] (when s (core/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (repl/refresh-all :after 'user/go))


; (db/get-profile-by-username (:database system) "alan")

; (profile/get-profile (:profile-controller system) 12 "alan")

; (article/create-article (:article-controller system) {})

; (db/get-article-by-slug (:database system) "test12")
