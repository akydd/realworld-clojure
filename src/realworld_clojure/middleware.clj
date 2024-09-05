(ns realworld-clojure.middleware)

(defn wrap-exception [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500}))))
