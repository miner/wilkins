(ns miner.wilkins-test
  (:use clojure.test
        miner.wilkins))

;; defines some features used in these tests

(def ^{:feature (version "3.4.5")} Foo)

(def ^{:feature (version "20.34")} Bar)


(defn clj14+? []
  #x/condf [clr false [clj "1.4+"] true else false])

(defn jdk16? []
  #x/condf [jdk-1.6.* true else false])

(defn jdk17? []
  #x/condf [jdk-1.7.* true else false])

(defn foo42 []
  (get {:a 1 :b 2 :ok 42}
       #x/condf [clj-1.3.* :unsupported
                 (or jdk-1.9+ clj-2.0.*) :untested
                 (and clj-1.4+ jdk-1.5+) :ok]
       :fail))

(defrecord MyReco [x])

(deftest running-on-clr
  (let [{:keys [major minor]} *clojure-version*]
    (is (= (clj14+?) (or (> major 1) (and (== major 1) (>= minor 4)))))))

(deftest running-on-jdk
  (let [jdk (System/getProperty "java.version")]
    (is (= (jdk17?) (.startsWith jdk "1.7.")))
    (is (= (jdk16?) (.startsWith jdk "1.6.")))))

(deftest foo-test
  (is (= (foo42) 42)))

(defn clean-map [mp]
  ;; clean out nil values for more compact maps
  (reduce-kv (fn [m k v] (if-not v (dissoc m k) m)) mp mp))

(deftest parsing-features
  (are [x y] (= (clean-map (parse-feature x)) y)
       "foo-bar-1" {:feature 'miner.wilkins/foo-bar, :major 1}
       "foo-bar1" {:feature 'miner.wilkins/foo-bar1}
       "foo-bar-1.2+" {:plus true, :feature 'miner.wilkins/foo-bar, :major 1, :minor 2}
       "baz.quux/foo-bar-3.4.*"   {:feature 'baz.quux/foo-bar, :major 3, :minor 4,
                                   :incremental :*}
       "bar/foo.bar" {:feature 'bar/foo.bar}))      

(deftest parsing-requests
  ;; slightly different from parse-feature for the case where no version is specified
  (are [x y] (= (clean-map (parse-request x)) y)
       "foo-bar-1" {:feature 'miner.wilkins/foo-bar, :major 1}
       "foo-bar1" {:feature 'miner.wilkins/foo-bar1 :major :*}
       "foo-bar-1.2+" {:plus true, :feature 'miner.wilkins/foo-bar, :major 1, :minor 2}
       "baz.quux/foo-bar-3.4.*"   {:feature 'baz.quux/foo-bar, :major 3, :minor 4,
                                   :incremental :*}
       "bar/foo.bar" {:feature 'bar/foo.bar :major :*}))

(deftest defined-test
  (is (= #x/condf [miner.wilkins/clojure :ok] :ok))
  (is (= #x/condf [miner.wilkins/not-there :bad miner.wilkins/condf :ok] :ok))
  (is (= #x/condf [miner.wilkins/not-there :bad miner.wilkins/condf :ok] :ok))
  (is (= #x/condf [java.lang.Long :ok] :ok))
  ;; note the name-munging (- to _) on the record name as a Java class
  (is (= #x/condf [miner.wilkins_test.MyReco :ok] :ok))
  (is (= #x/condf [MyReco :ok] :ok))
  (is (= #x/condf [UndefindedRecord :bad else :ok] :ok))
  (is (nil? #x/condf [not.there :bad])))

(deftest provide-test
  (is (= 42 (read-string "#x/condf [foo-3.0+ :wrong-case miner.wilkins-test/foo :bad-var
miner.wilkins-test/Foo-50+ :wrong-version miner.wilkins-test/Foo-3.2+ 42 else :bad]")))
  (is (= 42 (read-string "#x/condf [Foo-4.2+ :missing-ns miner.wilkins-test/Bar-20.* 42 else :bad]"))))

;; ISSUE: can't handle ps/Foo because macros doesn't resolve local ns alias

(deftest runtime
  (let [n 10]
    (is (= (feature-cond (and clj (not foobar) miner.wilkins-test/Bar-1.2+) (inc n)
                         (or clj-1.5 [clj "1.4"]) :clj
                         else :bad)
           11))
    (is (= (feature-cond (and clj Foobar miner.wilkins-test/Bar-3.2+) (throw (IllegalStateException. "Bad"))
                         (or clj-1.5+ [clj "1.4"]) (dec (* n n))
                         else :bad) 
           99))
    (is (= (feature-cond (and clj (not Foobar3.2) miner.wilkins-test/Bar-31.2+) :bad 
                         (or jdk-2.0 [clj "1.9+"]) :clj-future
                         else (+ 3 4 n))
           17))))
