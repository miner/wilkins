(ns miner.wilkins-test
  (:require miner.provide-sample)
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
  (is (= #x/condf [#'miner.wilkins/provide :ok] :ok))
  (is (= #x/condf [#'miner.wilkins/not-there :bad #'miner.wilkins/condf :ok] :ok))
  (is (nil? #x/condf [#'miner.wilkins/not-there :bad])))

(deftest provide-test
  (is (= 42 (read-string "#x/condf [foo3.0+ :no-ns miner.wilkins-test/foo3.2+ :wilkins
miner.provide-sample/foo3.2+ 42 user/foo3.4.5 :user else :bad]")))
  (is (= 42 (read-string "#x/condf [xyz/foo4.2+ :bad zzz/bar20.* 42 else :bad]"))))

(deftest runtime
  (let [n 10]
    (is (= (feature-cond (and clj (not foobar) zzz/bar1.2+) (inc n)
                         (or clj1.5 [clj "1.4"]) :clj
                         else :bad)
           11))
    (is (= (feature-cond (and clj foobar zzz/bar3.2+) (throw (IllegalStateException. "Bad"))
                         (or clj1.5 [clj "1.4"]) (dec (* n n))
                         else :bad) 
           99))
    (is (= (feature-cond (and clj (not foobar3.2) bar31.2+) :bad 
                         (or jdk2.0 [clj "1.9+"]) :clj-future
                         else (+ 3 4 n))
           17))))
