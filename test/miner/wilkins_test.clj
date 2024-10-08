(ns miner.wilkins-test
  (:use clojure.test
        miner.wilkins))

;; see test/data_readers.clj for binding of #x/condf

;; defines some features used in these tests

(def ^{:feature (version "3.4.5")} Foo)

(def ^{:feature (version "20.34")} Bar)

(def ^:feature version-less 42)

;; potentially ambiguous def, no version
(def ^:feature lucky-7)

(defn clj14+? []
  #x/condf [clr false [clj "1.4+"] true else false])

(defn jdk16? []
  #x/condf [jdk-1.6.* true else false])

(defn jdk17? []
  #x/condf [jdk-1.7.* true else false])

(defn foo42 []
  (get {:a 1 :b 2 :ok 42}
       #x/condf [clj-1.3.* :unsupported
                 (or jdk-31.9+ clj-2.0.*) :untested
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

(defn shrink-map
  "Remove falsey values and associated keys from the map `m`"
  [m]
  (reduce-kv (fn [r k v] (if-not v (dissoc r k) r)) m m))

(deftest parsing-features
  (are [x y] (= (shrink-map (as-feature x)) y)
       "foo-bar-1" {:feature 'foo-bar, :major 1}
       "foo-bar1" {:feature 'foo-bar1}
       "foo-bar-1.2+" {:plus true, :feature 'foo-bar, :major 1, :minor 2}
       "baz.quux/foo-bar-3.4.*"   {:feature 'baz.quux/foo-bar, :major 3, :minor 4,
                                   :incremental :*}
       "bar/foo.bar" {:feature 'bar/foo.bar}))      

(deftest parsing-requirements
  ;; slightly different from as-feature for the case where no version is specified
  (are [x y] (= (shrink-map (as-simple-requirement x)) y)
       "foo-bar-1" {:feature 'foo-bar :major 1}
       "foo-bar1" {:feature 'foo-bar1 :major :*}
       "foo-bar-1.2+" {:plus true, :feature 'foo-bar, :major 1, :minor 2}
       "baz.quux/foo-bar-3.4.*"   {:feature 'baz.quux/foo-bar, :major 3, :minor 4,
                                   :incremental :*}
       "bar/foo.bar" {:feature 'bar/foo.bar :major :*}))

(deftest defined-test
  (is (= #x/condf [clojure :ok] :ok))
  (is (= #x/condf [miner.wilkins/not-there :bad miner.wilkins/condf-reader :ok] :ok))
  (is (= #x/condf [miner.wilkins/not-there :bad condf-reader :ok] :ok))
  (is (= #x/condf [(and (not cond) conf) :bad cond :ok] :ok))
  (is (= #x/condf [java.lang.Long :ok] :ok))
  (is (= #x/condf [Long :ok] :ok))
  ;; note the name-munging (- to _) on the record name as a Java class
  (is (= #x/condf [miner.wilkins_test.MyReco :ok] :ok))
  (is (= #x/condf [MyReco :ok] :ok))
  (is (= #x/condf [UndefindedRecord :bad else :ok] :ok))
  (is (nil? #x/condf [not.there :bad])))

(deftest local-def-test
  (is (= 42 (read-string "#x/condf [foo-3.0+ :wrong-case miner.wilkins-test/foo :bad-var
miner.wilkins-test/Foo-50+ :wrong-version miner.wilkins-test/Foo-3.2+ 42 else :bad]")))
  (is (= 42 (read-string "#x/condf [Foo-4.2+ :missing-ns miner.wilkins-test/Bar-20.* 42 else :bad]"))))


(deftest without-version
  (is (= 11 #x/condf (miner.wilkins-test/version-less 11 else :bad)))
  (is (= 12 #x/condf (miner.wilkins-test/version-less-* 12 else :bad)))
  (is (= 13 #x/condf (miner.wilkins-test/unknown-var :bad else (inc 12))))
  (is (= 11 #x/condf (version-less 11 else :bad)))
  (is (= 12 #x/condf (version-less-* 12 else :bad)))
  (is (= 13 #x/condf (unknown-var :bad else (inc 12)))))

;; ISSUE: can't handle ps/Foo because macros doesn't resolve local ns alias

(deftest runtime
  (let [n 10]
    (is (= 11 (runtime-condf (and clj (not foobar) miner.wilkins-test/Bar-1.2+) (inc n)
                            (or clj-1.5 [clj "1.4"]) :clj
                            else :bad)))
    (is (= 99 (runtime-condf (and clj Foobar miner.wilkins-test/Bar-3.2+) 
                              (throw (IllegalStateException. "Bad"))
                            (or clj-1.5+ [clj "1.4"]) (dec (* n n))
                            else :bad)))
    (is (= 17 (runtime-condf (and clj (not Foobar3.2) miner.wilkins-test/Bar-31.2+) :bad 
                            (or jdk-2.0 [clj "1.13+"]) :clj-future
                            else (+ 3 4 n))))))

(deftest short-names
  (is (= 101 (runtime-condf (and clj Object) 101 else :bad)))
  (is (= 22 (runtime-condf (not miner.wilkins-test/Foo) :foo 
                          (and miner.wilkins-test/Foo miner.wilkins-test/Bar
                               miner.wilkins-test/version-less) 22 
                          Object :too-late))))


(deftest check-ns-features
  (is (= #{'miner.wilkins-test/Foo 'miner.wilkins-test/Bar 'miner.wilkins-test/version-less
           'miner.wilkins-test/lucky-7}
         (set (keys (ns-features (the-ns 'miner.wilkins-test))))))
  (is (= #{'miner.wilkins.features/clj 'miner.wilkins.features/clojure
           'miner.wilkins.features/jdk 'miner.wilkins.features/java}
         (set (keys (ns-features (the-ns 'miner.wilkins.features)))))))

(deftest lucky-parsing
  ;; normal parsing takes the 7 as a version
  (is (= (shrink-map (as-simple-requirement 'lucky-7)) '{:feature lucky, :major 7}))
  ;; quote protects the -7 as part of the symbol
  (is (= (shrink-map (as-simple-requirement '(quote lucky-7))) '{:major :*, :feature lucky-7}))
  ;; double single-quote looks weird but works
  (is (= (shrink-map (as-simple-requirement ''lucky-7)) '{:major :*, :feature lucky-7}))
  ;; vector with no version also works
  (is (= (shrink-map (as-simple-requirement '[lucky-7])) '{:major :*, :feature lucky-7})))

(deftest check-lucky-reader
  (is (= 7 #x/condf [lucky-7 :bad 'miner.wilkins-test/lucky-7 7]))
  (is (= 7 #x/condf [lucky-7 :bad 'lucky-7 7]))
  (is (= 7 #x/condf [(not lucky) 7 else :bad]))
  (is (= 7 #x/condf [[lucky "7"] :bad [miner.wilkins-test/lucky-7] 7 else :bad2]))
  (is (= 7 #x/condf [[lucky "7"] :bad [lucky-7] 7 else :bad2])))

(deftest check-lucky-runtime
  ;; note lucky-7 is the var name (not version 7 of lucky), to disambiguate that from
  ;; [lucky "7"], you need the extra quote 'lucky-7 or [lucky-7] vector format.
  (is (= 7 (runtime-condf lucky-7 :bad 'miner.wilkins-test/lucky-7 7)))
  (is (= 7 (runtime-condf miner.wilkins-test/lucky-7 :bad 'miner.wilkins-test/lucky-7 7)))
  (is (= 7 (runtime-condf (not lucky) 7 else :bad)))
  (is (= 7 (runtime-condf [lucky "7"] :bad [miner.wilkins-test/lucky-7] 7 else :bad2)))
  (binding [*ns* (the-ns 'miner.wilkins-test)]
    ;; Honestly, not really sure why binding the *ns* is required, but otherwise the tests
    ;; run with the 'user namespace as the *ns*.
    (is (= 7 (runtime-condf lucky-7 :bad 'lucky-7 7)))
    (is (= 7 (runtime-condf [lucky "7"] :bad [lucky-7] 7 else :bad2)))) )

(deftest check-lucky-compile
  ;; note lucky-7 is the var name (not version 7 of lucky), to disambiguate that from
  ;; [lucky "7"], you need the extra quote 'lucky-7 or [lucky-7] vector format.
  (is (= 7 (compile-condf lucky-7 :bad 'miner.wilkins-test/lucky-7 7)))
  (is (= 7 (compile-condf miner.wilkins-test/lucky-7 :bad 'miner.wilkins-test/lucky-7 7)))
  (is (= 7 (compile-condf (not lucky) 7 else :bad)))
  (is (= 7 (compile-condf [lucky "7"] :bad [miner.wilkins-test/lucky-7] 7 else :bad2)))
  (binding [*ns* (the-ns 'miner.wilkins-test)]
    ;; Honestly, not really sure why binding the *ns* is required, but otherwise the tests
    ;; run with the 'user namespace as the *ns*.
    (is (= 7 (compile-condf lucky-7 :bad 'lucky-7 7)))
    (is (= 7 (compile-condf [lucky "7"] :bad [lucky-7] 7 else :bad2)))) )


(def ^{:feature false} disabled-feature)

(deftest test-disabled-feature
  ;; note to self: don't name a test the same as the var you're testing!
  (is (= 13  (runtime-condf (not miner.wilkins-test/disabled-feature) 13
                            miner.wilkins-test/disabled-feature 7))))

(deftest compile-condf-test
  (is (= :ok (compile-condf (and clj jdk missing) :wrong (and clj jdk) :ok clj :too-late))))


(deftest weird-core-asyn-version
  ;; the qualifier looks weird, but core.async has a weird versioning system during alpha
  (is (= (as-simple-requirement "core.async-0.1.301.0-deb34a-alpha")
         (as-simple-requirement 'core.async-0.1.301.0-deb34a-alpha)
         '{:feature core.async, :major 0, :minor 1, :incremental 301,
           :qualifier ".0-deb34a-alpha"})))

(deftest unusual-feature-names
  (is (= (as-simple-requirement 'foo-bar1) '{:major :*, :feature foo-bar1}))
  (is (= (as-simple-requirement 'foo-bar-1.*) '{:feature foo-bar, :major 1, :minor :*}))
  (is (= (as-simple-requirement '[foo-bar-1.*]) '{:major :*, :feature foo-bar-1.*})))


  
