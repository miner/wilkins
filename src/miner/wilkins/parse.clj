(ns miner.wilkins.parse
  (:require [clojure.string :as str]))


(defn parse-long [s]
  (cond (nil? s) nil
        (= s "*") :*
        :else (Long/parseLong s)))

;; this is here for convenience, but logically the function is part of 
(defn parse-version [vstr]
  (let [[valid major minor increm qual plus] 
          (re-matches #"(\d+|[*])?(?:\.(\d+|[*]))?(?:\.(\d+|[*]))?-?([^+/.]*)(\+)?" vstr)
         version (and valid
                       {:version vstr
                        :major (parse-long major)
                        :minor (parse-long minor)
                        :incremental (parse-long increm)
                        :qualifier (not-empty qual)})]
    (if (and version (or (not major) (= plus "+") (= major "*")))
      (assoc version :plus true)
      version)))


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
        id (not-empty id)
        feature (if (and valid id)
                     {:feature (symbol ns id)
                      :major (parse-long major)
                      :minor (parse-long minor)
                      :incremental (parse-long increm)
                      :qualifier (not-empty qual)}
                     {:feature (symbol fstr)} )]
    (if (and id feature (or (not major) (= plus "+") (= major "*")))
      (assoc feature :plus true)
      feature)))

(defn feature-java []
  (let [jdk-version (System/getProperty "java.version")
        java-feature (parse-version (first (str/split jdk-version #"_")))]
    (assoc java-feature :version jdk-version)))
