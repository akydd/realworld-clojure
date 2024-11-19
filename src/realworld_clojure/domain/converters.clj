(ns realworld-clojure.domain.converters)

(defn user-db->profile
  "Convert a user to a profile"
  [user]
  (select-keys user [:username :bio :image]))
