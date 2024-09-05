(ns realworld-clojure.domain.user
  (:require [realworld-clojure.adapters.db :as db]
            [malli.core :as m]
            [malli.error :as me]))
(def User
  [:map
   [:username [:string {:min 1}]]
   [:password [:string {:min 1}]]
   [:email [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]])

(defn register-user
  "Register a user"
  [user]
  (if (m/validate User user)
    {:user (db/insert-user user)}
    {:errors (me/humanize (m/explain User user))}))
