(ns miner.wilkins
  "Wilkins creature feature lib"
  {:version "0.2.0"}
  (:require [clojure.string :as str]))


(defn parse-long [s]
  (cond (nil? s) nil
        (= s "*") :*
        :else (Long/parseLong s)))

;; hairy regx, maybe should do some sanity checking
;; should verify that not both .* and + are used
;; only one version terminating .* is allowed
;; also if there's a qual, neither wildcard is allowed (currently is allowed)
;; hyphen is allowed before version number

(defn parse-feature  [fstr]
  (let [[head tail] (str/split fstr #"/" 2)
        ns (if tail head "miner.wilkins")
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
                     {:feature (symbol ns sym)} )]
    (if (and id feature (or (not major) (= plus "+") (= major "*")))
      (assoc feature :plus true)
      feature)))

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

;; converts a string, symbol, or vector as necessary to a feature map
(defn as-feature [fspec]
  (cond (nil? fspec) nil
        (map? fspec) fspec
        (vector? fspec) (if (second fspec)
                          (assoc (parse-version (second fspec)) :feature (first fspec))
                          {:feature (first fspec)})
        (and (seq? fspec) (= (first fspec) 'quote)) {:feature (second fspec)}
        (string? fspec) (parse-feature fspec)
        (symbol? fspec) (parse-feature (str fspec))
        :else (throw (ex-info (str "Malformed feature specification: " (pr-str fspec))
                              {:fspec fspec}))))

(defn as-request [rspec]
  (let [feat (as-feature rspec)]
    (if-not (:major feat)
      (assoc feat :major :*)
      feat))) 

(defn parse-request [rstr]
  (let [feat (parse-feature rstr)]
    (as-request feat)))

(defn as-version [vspec]
  (cond (nil? vspec) {:plus true :major :*}
        (map? vspec) vspec
        (integer? vspec) {:major vspec}
        (float? vspec) (parse-version (str vspec))
        :else (parse-version vspec)))

(defn feature-clojure []
  (assoc *clojure-version* :feature 'miner.wilkins/clojure))

(defn feature-java []
  (let [jdk-version (System/getProperty "java.version")
        java-feature (parse-version (first (str/split jdk-version #"_")))]
    (assoc java-feature :version jdk-version)))

(def ^{:feature (assoc *clojure-version* :version (clojure-version))} clojure)
(def ^{:feature (:feature (meta #'clojure))} clj)

(def ^{:feature (feature-java)} java)
(def ^{:feature (:feature (meta #'java))} jdk)

;; macro to make it easy to create a literal feature map
(defmacro feature [fspec]
  `'~(as-feature fspec))

(defmacro version [vspec]
  `'~(as-version vspec))

(defmacro request [rspec]
  `'~(as-request rspec))

;; for now, there aren't many requirements
(def feature? map?)

(defn compare-versions [a b]
  ;; allows last element to be :* as a wildcard
  ;; returns 0, -1 or 1 (like compare)
  (let [comp-vers (fn [a b ks]
                    (if-not (seq ks)
                      0
                      (let [k (first ks)
                            a1 (or (k a) 0)
                            b1 (or (k b) 0)]
                        (cond (or (= a1 :*) (= b1 :*)) 0
                              (== a1 b1) (recur a b (rest ks))
                              (> a1 b1) 1
                              (< a1 b1) -1))))]
    (comp-vers a b '(:major :minor :incremental))))


(defn version-satisfies? [actual request]
  {:pre [(feature? actual) (feature? request)]}
  ;; args are feature maps
  ;; assumes ids were already matched (might have been equivalent aliases, not identical)
  ;; if request has a qualifier everything must match exactly, otherwise qualifier doesn't matter
  (and (or (not (:qualifier request)) (= (:qualifier actual) (:qualifier request)))
       ((if (and (:plus request) (not (:qualifier request))) (complement neg?) zero?)
        (compare-versions actual request))))

(defn request-satisfied? [req]
  (when-let [id (:feature req)]
    (when-let [vsym (resolve id)]
      (let [feat (:feature (meta vsym))
            actual (if (or (nil? feat) (true? feat)) {} feat)]
        (when (feature? actual)
          (version-satisfies? actual req))))))

(defn feature-request-satisfied? [request]
  (request-satisfied? (as-request request)))


;; hacky stuff that doesn't exactly work.  Trying to handle alias ns resolution
;; (defn fully-qualified-namespace [sym]
;;   (->> (resolve sym) meta :ns))
;; 
;; 
;;         (when-let [nspace (fully-qualified-namespace x)]
;;           (when (not= (namespace x) (name (ns-name nspace)))
;;             (ns-resolve nspace (symbol (name x)))))

(defn soft-resolve [x]
  (try
     (resolve x)
    (catch Exception _ nil)))

(defn class-symbol? [x]
  (and (symbol? x) (not (namespace x)) (.contains (name x) ".") (class? (soft-resolve x))))

(defn satisfied? [request]
  (cond (not request) false
        (#{'else :else true} request) true
        (special-symbol? request) true
        (class-symbol? request) true
        (symbol? request)  (or (soft-resolve request) (feature-request-satisfied? request))
        (vector? request)  (feature-request-satisfied? request)
        (string? request)  (feature-request-satisfied? request)
        (seq? request) (let [op (first request)]
                         (case op
                           quote (feature-request-satisfied? request)
                           and (every? satisfied? (rest request))
                           or (some satisfied? (rest request))
                           not (not (satisfied? (second request)))))
        :else (throw (ex-info (str "Malformed feature request: " (pr-str request))
                              {:request request}))))


;; data-reader
(defn condf [fspecs-and-forms]
  ;; fspecs-and-forms is sequence of alternating feature specifications and forms.
  ;; the first feature specification to succeed returns the next form as the result.
  (let [result (first (keep (fn [[fspec form]] (when (satisfied? fspec) form)) 
                            (partition 2 fspecs-and-forms)))]
    (if (nil? result) '(quote nil) result)))

;; (quote nil) works around CLJ-1138


(declare satisfaction-test)

(defmacro conjunctive-satisfaction
  ([con] `(~con))
  ([con fspec] `(satisfaction-test ~fspec))
  ([con fspec & more] `(~con (satisfaction-test ~fspec) (conjunctive-satisfaction ~con ~@more))))

(defmacro satisfaction-test [request]
  (cond (not request) false
        (#{'else :else true} request) true
        (special-symbol? request) true
        (class-symbol? request) true
        (symbol? request)  `(or (soft-resolve '~request) 
                                (request-satisfied? '~(as-request request)))
        (vector? request)  `(request-satisfied? '~(as-request request))
        (string? request)  `(request-satisfied? '~(as-request request))
        (seq? request) (let [op (first request)]
                         (case op
                           quote `(request-satisfied? '~(as-request request))
                           and `(conjunctive-satisfaction and ~@(next request))
                           or `(conjunctive-satisfaction or ~@(next request))
                           not `(not (satisfaction-test ~(second request)))))
        :else (throw (ex-info (str "Malformed feature request: " (pr-str request))
                              {:request request}))))

;; for use at runtime as opposed to readtime

(defmacro feature-cond 
  ([] nil)
  ([fspec form] `(if (satisfaction-test ~fspec) ~form nil))
  ([fspec form & more] `(if (satisfaction-test ~fspec) ~form (feature-cond ~@more))))


(defn ns-features [namespace]
  "Returns a map of the features declare in the namespace.  The keys are fully qualified
  symbols (naming vars with metadata for the key :feature), and the values are maps of
  feature information, typically with keys such as :major, :minor, and :incremental.
  However, the feature information might be an empty map if the var was simply marked as a
  feature without any version information."
  (let [nsstr (name (ns-name namespace))]
    (reduce-kv (fn [fs sym vr]
                 (if-let [feat (:feature (meta vr))]
                   ;; true means no version info, just marked as a public feature for
                   ;; reference                   
                   (assoc fs (symbol nsstr (name sym)) (if (true? feat) {} feat))
                   fs))
               {}
               (ns-publics namespace))))
