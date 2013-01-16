(ns miner.wilkins
  (:require [clojure.string :as str]))


(defn current-ns-str []
  (name (ns-name *ns*)))

(defn namespace? [ns]
  (instance? clojure.lang.Namespace ns))

;; ns can be a real namespace, a string or a symbol
;; sym can be a symbol or a string
;; when the namespace is not specified, the current *ns* is used
;; result is always a qualified symbol
(defn qualified-symbol 
  ([sym] (cond (nil? sym) nil
               (string? sym) (symbol (current-ns-str) sym)
               (namespace sym) sym
               :else (symbol (current-ns-str) (name sym))))
  ([ns sym] (cond (nil? sym) nil
                  (nil? ns) (qualified-symbol sym)
                  (namespace? ns) (symbol (name (ns-name ns)) (name sym))
                  :else (symbol (name ns) (name sym)))))

(defn version-list [major minor increm]
  (cond (not major) ()
        (= major "*") ()
        (not minor) (list (read-string major))
        (not increm) (list (read-string major) (read-string minor))
        :else (list (read-string major) (read-string minor) (read-string increm))))

;; hairy regx, maybe should do some sanity checking
;; should verify that not both .* and + are used
;; only one version terminating .* is allowed
;; also if there's a qual, neither wildcard is allowed

(defn parse-feature  [fstr]
  (let [[valid ns id major minor increm qual plus]
        (re-matches #"(?:((?:[a-zA-Z](?:\w|-)*)(?:[.][a-zA-Z](?:\w|-)*)*)/)?([a-zA-Z]*)-?(\d+|[*])?(?:\.(\d+|[*]))?(?:\.(\d+|[*]))?-?([^+/.]*)(\+)?"
                    (str fstr))
        ns (not-empty ns)
        feature (and valid
                     {:feature (and (not-empty id) (if ns (symbol ns id) (symbol id)))
                      :version (version-list major minor increm)
                      :qualifier (not-empty qual)})]
    (if (and feature (or (not major) (= plus "+") (= major "*")))
      (assoc feature :plus true)
      feature)))

;; converts a string, symbol, or vector as necessary to a feature map
(defn as-feature [fspec]
  (cond (nil? fspec) nil
        (map? fspec) fspec
        (vector? fspec) (assoc (parse-feature (second fspec)) :feature (first fspec))
        :else (parse-feature (str fspec))))

(defn clj-version []
  {:feature 'clojure
   :version (list (:major *clojure-version*)
                  (:minor *clojure-version*)
                  (:incremental *clojure-version*))
   :qualifier (:qualifier *clojure-version*)})

(defn java-version []
  (let [jdk (parse-feature (System/getProperty "java.version"))
        qual (:qualifier jdk)]
    (assoc jdk
      :feature 'java
      :qualifier (when (and qual (not (.startsWith qual "_"))) qual))))

(defn init-features []
  (let [clj (clj-version)
        jdk (java-version)
        features {'clj clj 'clojure clj 'jdk jdk 'java jdk}
        features-prop (System/getProperty "wilkins.features")]
    (if features-prop
      (reduce #(assoc % (:feature %2) %2) features (map parse-feature (str/split features-prop #"\s+")))
      features)))

(defonce feature-map (atom (init-features)))

;; someday provide might be incorporated into the ns macro
(defn provide [feature]
  ;; always requires a namespaced id, uses *ns* if none specified
  (let [feature (as-feature feature)
        id (qualified-symbol (:feature feature))
        feature (assoc feature :feature id)]
    (swap! feature-map assoc (:feature feature) feature)
    feature))

(defn feature? [x]
  (and (map? x)
       (contains? x :feature)
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
  {:pre [(feature? actual) (feature? request)]}
  ;; args are feature maps
  ;; assumes ids were already matched (might have been equivalent aliases, not identical)
  ;; if request has a qualifier everything must match exactly, otherwise qualifier doesn't matter
  (and (or (not (:qualifier request)) (= (:qualifier actual) (:qualifier request)))
       ((if (and (:plus request) (not (:qualifier request))) (complement neg?) zero?)
        (compare-versions (:version actual) (:version request)))))


(defn vsym-provided? [vsym]
  (let [req (parse-feature (str vsym))
        id (:feature req)
        actual (get @feature-map id)]
    (and actual
         (version-satisfies? actual req))))

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
(defn defined? [sym]
  ;; sym should have a namespace
  (when-let [nsname (namespace sym)]
    (when-let [ns (find-ns (symbol nsname))]
      (try
        (ns-resolve ns sym)
        (catch java.io.FileNotFoundException _ nil)))))

(defn vector-provided? [vfspec]
  (let [[id ver & junk] vfspec]
    (assert (nil? junk))
    (let [req (assoc (parse-feature ver) :feature id)
          actual (get @feature-map id)]
        (and actual
             (version-satisfies? actual req)))))

(defn feature-provided? [request]
  {:pre [(feature? request)]}
  (let [actual (get @feature-map (:feature request))]
    (and actual
         (version-satisfies? actual request))))
  

(defn provided? [fspec]
  (cond (#{'else :else} fspec) true
        (symbol? fspec)  (vsym-provided? fspec)
        (vector? fspec)  (vector-provided? fspec)
        :else (let [op (first fspec)]
                (case op
                  (quote deref clojure.core/deref var var?) (defined? (qualified-symbol (second fspec)))
                  prop= (apply prop= (rest fspec))
                  env= (apply env= (rest fspec))
                  class? (apply java-class? (rest fspec))
                  and (every? provided? (rest fspec))
                  or (some provided? (rest fspec))
                  not (not (provided? (second fspec)))))))


;; data-reader
(defn condf [fspecs-and-forms]
  ;; fspecs-and-forms is sequence of alternating feature specifications and forms.
  ;; the first feature specification to succeed returns the next form as the result.
  (let [result (first (keep (fn [[fspec form]] (when (provided? fspec) form)) (partition 2 fspecs-and-forms)))]
    (if (nil? result) '(quote nil) result)))

;; (quote nil) works around CLJ-1138


(declare provided-test)

(defmacro conjunctive-provided
  ([con] `(~con))
  ([con fspec] `(provided-test ~fspec))
  ([con fspec & more] `(~con (provided-test ~fspec) (provided-test (~con ~@more)))))

(defmacro provided-test [fspec]
  (cond (nil? fspec) false
        (#{'else :else} fspec) true
        (map? fspec) `(feature-provided? '~fspec)
        (symbol? fspec)  `(feature-provided? '~(as-feature fspec))
        (vector? fspec)  `(feature-provided? '~(as-feature fspec))
        :else (let [op (first fspec)]
                (case op
                  (quote deref clojure.core/deref var var?) `(defined? '~(qualified-symbol (second fspec)))
                  prop= `(prop= ~@(rest fspec))
                  env= `(env= ~@(rest fspec))
                  class? `(java-class? ~@(rest fspec))
                  (and clojure.core/and) `(conjunctive-provided and ~@(next fspec))
                  (or clojure.core/or) `(conjunctive-provided or ~@(next fspec))
                  (not clojure.core/not) `(not (provided-test ~(second fspec)))))))
  
;; for use at runtime as opposed to readtime

(defmacro feature-cond 
  ([] nil)
  ([fspec form] `(if (provided-test ~fspec) ~form nil))
  ([fspec form & more] `(if (provided-test ~fspec) ~form (feature-cond ~@more))))

