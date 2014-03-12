(ns miner.wilkins.parse
  (:require [clojure.string :as str]))

(defn assoc-when 
  ([m k v] (if v (assoc m k v) m))
  ([m k v & kvs] (reduce (fn [m1 [k1 v1]] (assoc-when m1 k1 v1)) 
                         (assoc-when m k v)
                         (partition 2 kvs))))

(defn parse-long [s]
  (cond (nil? s) nil
        (= s "*") :*
        :else (Long/parseLong s)))

(defn parse-version [vstr]
  (cond (nil? vstr) {}
        (= vstr "") {}
        :else 
          (let [[valid major minor increm qual plus] 
                (re-matches #"(\d+|[*])?(?:\.(\d+|[*]))?(?:\.(\d+|[*]))?-?([^+/.]*)(\+)?" vstr)]
            (when valid
              (assoc-when {:version vstr}
                          :major (parse-long major)
                          :minor (parse-long minor)
                          :incremental (parse-long increm)
                          :qualifier (not-empty qual)
                          :plus (= plus "+"))))))


;; hairy regx, maybe should do some sanity checking
;; should verify that not both .* and + are used
;; only one version terminating .* is allowed
;; also if there's a qual, neither wildcard is allowed (currently is allowed)
;; hyphen is allowed before version number

(defn parse-feature  [fstr]
  (let [[head tail] (str/split fstr #"/" 2)
        ns (when tail head)
        sym (or tail head)
        [valid id major minor increm qual plus] 
          (re-matches #"(?:(\D+)-)(\d+|[*])(?:\.(\d+|[*]))?(?:\.(\d+|[*]))?-?([^+/.]*)(\+)?" sym)
        id (not-empty id)]
    (if (and valid id)
      (assoc-when {:feature (symbol ns id)}
                  :major (parse-long major)
                  :minor (parse-long minor)
                  :incremental (parse-long increm)
                  :qualifier (not-empty qual)
                  :plus (= plus "+"))
      {:feature (symbol fstr)})))


(defn feature-java []
  (let [jdk-version (System/getProperty "java.version")
        java-feature (parse-version (first (str/split jdk-version #"_")))]
    (assoc java-feature :version jdk-version)))
