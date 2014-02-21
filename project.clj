(defproject com.velisco/wilkins "0.2.0-SNAPSHOT"
  :description "Experimental lib for Clojure conditional reader using tagged literals"
  :url "http://github.com/miner/wilkins"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0-beta1"]]

  :profiles {:clj15  {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :clj14 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :beta1 {:dependencies [[org.clojure/clojure "1.6.0-beta1"]]}
             })
