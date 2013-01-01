(ns miner.wilkins-test
  (:use clojure.test
        miner.wilkins))

(defn clj14? []
  #x/condf [clr false [clj "1.4+"] true])

(defn jdk16? []
  #x/condf [jdk1.6.* true])

(defn foo42 []
  (get {:a 1 :b 2 :ok 42}
       #x/condf [clj1.3.* :unsupported
                 (or jdk1.9+ clj2.0.*) :untested
                 (and clj1.4+ jdk1.5+) :ok]
       :fail))

(deftest running-on-clr
  (let [{:keys [major minor]} *clojure-version*]
    (is (= (clj14?) (or (> major 1) (and (== major 1) (>= minor 4)))))))

(deftest running-on-jdk16
  (let [jdk (System/getProperty "java.version")]
    (is (= (jdk16?) (.startsWith jdk "1.6.")))))

(deftest foo-test
  (is (= (foo42) 42)))

(deftest var-test
  (is (= #x/condf [#'miner.wilkins/*features* :ok] :ok))
  (is (= #x/condf [#'miner.wilkins/not-there :bad #'miner.wilkins/condf :ok] :ok))
  (is (nil? #x/condf [#'miner.wilkins/not-there :bad])))
