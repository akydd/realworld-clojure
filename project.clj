(defproject realworld-clojure "0.1.0-SNAPSHOT"
  :description "An implementation of the real-world API"
  :url "https://realworld-docs.netlify.app/"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [aero "1.1.6"]
                 [metosin/malli "0.16.4"]
                 [compojure "1.6.1"]
                 [http-kit "2.3.0"]
                 [ring/ring-json "0.5.1"]
                 [ring/ring-core "1.12.1"]
                 [org.clojure/clojure "1.11.3"]
                 [com.github.seancorfield/next.jdbc "1.3.939"]
                 [org.postgresql/postgresql "42.7.3"]
                 [clj-test-containers "0.7.4"]
                 [ragtime "0.8.0"]
                 [com.stuartsierra/component "1.1.0"]
                 [buddy/buddy-hashers "2.0.167"]
                 [buddy/buddy-sign "3.5.351"]
                 [buddy/buddy-auth "3.0.1"]
                 [cambium/cambium.core         "1.1.1"]
                 [cambium/cambium.codec-simple "1.0.0"]
                 [cambium/cambium.logback.core "0.4.6"]
                 [clojure.java-time "1.4.2"]]

  :main ^:skip-aot realworld-clojure.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "1.5.0"]
                                  [org.clojure/java.classpath "1.1.0"]]}})
