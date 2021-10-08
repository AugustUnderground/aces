(defproject aces "0.1.0-SNAPSHOT"
  :description "Analog Circuit Characterization Environment Server AC²ES"
  :url "https://github.com/augustunderground/aces"
  :license {:name "MIT"
            :url "https://spdx.org/licenses/MIT.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [edlab.eda/characterization "0.0.1"]
                 [http-kit "2.5.3"] 
                 [compojure "1.6.2"]
                 [ring "1.9.4"]
                 [ring/ring-json "0.5.1"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/tools.cli "1.0.206"]]
  :main ^:skip-aot aces.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                                  "-Xms500m" "-Xmx2g"]}})
