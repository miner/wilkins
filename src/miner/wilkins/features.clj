(ns miner.wilkins.features
  (:require [miner.wilkins.parse :as p]))

(def ^{:feature (assoc *clojure-version* :version (clojure-version))} clojure)
(def ^{:feature (:feature (meta #'clojure))} clj)

(def ^{:feature (p/feature-java)} java)
(def ^{:feature (:feature (meta #'java))} jdk)

  
