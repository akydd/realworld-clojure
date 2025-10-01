(ns realworld-clojure.integration.common)

(def user-response-schema
  [:map {:closed true}
   [:username [:string {:min 1}]]
   [:token [:string {:min 1}]]
   [:email [:string {:min 1}]]
   [:bio {:optional true} [:string {:min 1}]]
   [:image {:optional true} [:string {:min 1}]]])
