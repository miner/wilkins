(ns miner.wilkins-test
  (:use clojure.test
        miner.wilkins))

(defn clj14? []
  #x/condf [clr false [clj "1.4+"] true])

(defn jdk16? []
  #x/condf [jdk1.6.* true])


(deftest running-on-clr []
  (let [{:keys [major minor]} *clojure-version*]
    (is (= (clj14?) (or (> major 1) (and (== major 1) (>= minor 4)))))))

(deftest running-on-jdk16 []
  (let [jdk (System/getProperty "java.version")]
    (is (= (jdk16?) (.startsWith jdk "1.6.")))))

             
