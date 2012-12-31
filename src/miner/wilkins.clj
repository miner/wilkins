(ns miner.wilkins
  (:require [clojure.string :as str]))

;; a feature spec can be any of the following:
;; 1. var feature --fully qualified symbol -- succeeds if symbol is resolved
;; 2. version feature -- non-namespace symbol -- indicates a spec --
;;    key maps into features, version, etc must match
;; 3. vector -- lein style [name "ver"] where ver is a double-quoted string like "1.2.3"
;; 4. list starting with AND, OR or NOT (symbols) -- boolean combinations of feature specs

;; java1.5 clj1.4+ clj1.5 java1.4.*
;; any qualifier implies an exact match only


;; should verify that not both .* and + are used
;; only one version terminating .* is allowed
;; also if there's a qual, neither wildcard is allowed

(defn parse-version [major minor increm]
  (cond (not major) ()
        (= major "*") ()
        (not minor) (list (read-string major))
        (not increm) (list (read-string major) (read-string minor))
        :else (list (read-string major) (read-string minor) (read-string increm))))


(defn parse-feature  [fstr]
  (let [[valid ns id major minor increm qual plus]
        (re-matches #"(?:((?:[a-zA-Z]\w*)(?:[.][a-zA-Z]\w*)*)/)?([a-zA-Z]*)-?(\d+|[*])?(?:\.(\d+|[*]))?(?:\.(\d+|[*]))?-?([^+/.]*)(\+)?"
                    (str fstr))
        ns (not-empty ns)
        feature (and valid
                     {:id (and (not-empty id) (if ns (symbol ns id) (symbol id)))
                      :version (parse-version major minor increm)
                      :qualifier (not-empty qual)})]
    (if (and feature (or (not major) (= plus "+") (= major "*")))
      (assoc feature :plus true)
      feature)))

;; converts a string or symbol as necessary to a feature map
(defn as-feature [featurish]
  (and featurish
       (if (map? featurish) featurish (parse-feature (str featurish)))))

(defn clj-version []
  {:id 'clojure
   :version (list (:major *clojure-version*)
                  (:minor *clojure-version*)
                  (:incremental *clojure-version*))
   :qualifier (:qualifier *clojure-version*)})

(defn java-version []
  (let [jdk (parse-feature (System/getProperty "java.version"))
        qual (:qualifier jdk)]
    (assoc jdk
      :id 'java
      :qualifier (when (and qual (not (.startsWith qual "_"))) qual))))

(defn init-features []
  (let [clj (clj-version)
        jdk (java-version)
        features {'clj clj 'clojure clj 'jdk jdk 'java jdk}
        features-prop (System/getProperty "wilkins.features")]
    (if features-prop
      (reduce #(assoc % (:id %2) %2) features (map parse-feature (str/split features-prop #"\s+")))
      features)))

(def ^:dynamic *features* (init-features))

;; SEM not sure about this
(defn update-in-features! [keys val]
  (alter-var-root #'*features* update-in (if (symbol? keys) (vector keys) keys) (fn [_ new] new) val))

(defn add-feature! [feature]
  (let [feature (as-feature feature)]
    (update-in-features! (:id feature) feature)))

(defn feature? [x]
  (and (map? x)
       (contains? x :id)
       (contains? x :version)))

(defn compare-versions [as bs]
  ;; allows last element to be '* as a wildcard
  ;; returns 0, -1 or 1
  (if-not (or (seq as) (seq bs))
    ;; both empty
    0
    (let [a1 (or (first as) 0)
          b1 (or (first bs) 0)]
      (cond (or (= a1 '*) (= b1 '*)) 0
            (== a1 b1) (recur (rest as) (rest bs))
            (> a1 b1) 1
            (< a1 b1) -1))))

(defn version-satisfies? [actual request]
  ;; args are feature maps
  ;; assumes ids were already matched (might have been equivalent aliases, not identical)
  ;; if request has a qualifier everything must match exactly, otherwise qualifier doesn't matter
  (and (or (not (:qualifier request)) (= (:qualifier actual) (:qualifier request)))
       ((if (and (:plus request) (not (:qualifier request))) (complement neg?) zero?)
        (compare-versions (:version actual) (:version request)))))


;; Add support AND OR and NOT for boolean logic

(defn vsym-sat? [vsym]
  (or (= vsym 'else) (= vsym :else)
      (let [req (parse-feature (str vsym))
            id (:id req)
            actual (get *features* id)]
        (and actual
             (version-satisfies? actual req)))))


;; not used
(defn maybe-qualified-class? [sym]
  ;; Check for java class, must be fully qualified
  (and (re-matches #"([\p{L}_$][\p{L}\p{N}_$]*\.)+[\p{L}_$][\p{L}\p{N}_$]*" (str sym))
       true))

(defn java-class?
  ([sym]
     (when (maybe-qualified-class? sym)
       (try
         (resolve sym)
         (catch ClassNotFoundException _ nil))))
  ([sym & more]
     (and (java-class? sym)
          (every? java-class? more))))

(defn prop= 
 ([prop val] (= (System/getProperty (str prop)) (str val)))
 ([prop val & more]
    (and (prop= prop val)
         (every? prop= (partition 2 more)))))

(defn env= 
 ([evar val] (= (System/getenv (str evar)) (str val)))
 ([evar val & more]
    (and (env= evar val)
         (every? env= (partition 2 more)))))

;; maybe could force require, but seems wrong
(defn clojure-var? [sym]
  ;; sym should have a namespace
  (when-let [nsname (namespace sym)]
    (when-let [ns (find-ns (symbol nsname))]
      (try
        (ns-resolve ns sym)
        (catch java.io.FileNotFoundException _ nil)))))

(defn var-feature [feature]
  (when feature
    (if-let [ns (namespace feature)] feature (symbol (str *ns*) (name feature)))))

(defn vector-sat? [vfeature]
  (let [[id ver & junk] vfeature]
    (assert (nil? junk))
    (let [req (assoc (parse-feature ver) :id id)
          actual (get *features* id)]
        (and actual
             (version-satisfies? actual req)))))
    

(defn feature-sat? [feature]
  (cond (symbol? feature)  (or (clojure-var? feature) (vsym-sat? feature))
        (vector? feature)  (vector-sat? feature)
        :else (let [op (first feature)]
                (case op
                  (var var?) (clojure-var? (var-feature (second feature)))
                  prop= (apply prop= (rest feature))
                  env= (apply env= (rest feature))
                  class? (apply java-class? (rest feature))
                  and (every? feature-sat? (rest feature))
                  or (some feature-sat? (rest feature))
                  not (not (feature-sat? (second feature)))))))



(defn condf [tests-and-forms]
  ;; tests-and-forms is sequence of alternating tests and forms.
  ;; the first testform to succeed returns the next form as the result.
  ;; Each test is a featuture expression.  Needs more explanation.
  (let [result (first (keep (fn [[v form]] (when (feature-sat? v) form)) (partition 2 tests-and-forms)))]
    (if (nil? result) '(quote nil) result)))

;; (quote nil) works around CLJ-1138


;; for use at runtime as opposed to readtime
(defmacro feature-cond [&clauses]
  ;; TBD
  nil)
