(ns realworld-clojure.ports.converters)

(defn user->user
  [user]
  (dissoc user :id :password))

(defn profile->profile
  [profile]
  (dissoc profile :id))

(defn article->article
  [article]
  (dissoc article :id))
