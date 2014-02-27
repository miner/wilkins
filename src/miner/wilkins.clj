(ns miner.wilkins
  "Wilkins creature feature lib"
  (:require [clojure.string :as str]
            [miner.wilkins.parse :as p]
            miner.wilkins.features))



(defn request? [x] (and (map? x) (:feature x)))

;; converts a string, symbol, or vector as necessary to a feature map
(defn as-feature [fspec]
  (cond (nil? fspec) nil
        (map? fspec) fspec
        (vector? fspec) (if (second fspec)
                          (assoc (p/parse-version (second fspec)) :feature (first fspec))
                          {:feature (first fspec)})
        (and (seq? fspec) (= (first fspec) 'quote)) {:feature (second fspec)}
        (string? fspec) (p/parse-feature fspec)
        (symbol? fspec) (p/parse-feature (str fspec))
        :else (throw (ex-info (str "Malformed feature specification: " (pr-str fspec))
                              {:fspec fspec}))))

(defn as-feature-request [rspec]
  (let [feat (as-feature rspec)]
    (if-not (:major feat)
      (assoc feat :major :*)
      feat)))

(defn parse-request [rstr]
  (let [feat (p/parse-feature rstr)]
    (as-feature-request feat)))

(defn as-version [vspec]
  (cond (nil? vspec) {:plus true :major :*}
        (map? vspec) vspec
        (integer? vspec) {:major vspec}
        (float? vspec) (p/parse-version (str vspec))
        :else (p/parse-version vspec)))

;; macro to make it easy to create a literal feature map

(defmacro version [vspec]
  `'~(as-version vspec))

(defmacro feature [rspec]
  `'~(as-feature-request rspec))


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
  {:pre [(map? actual) (map? request)]}
  ;; args are feature maps
  ;; assumes ids were already matched (might have been equivalent aliases, not identical)
  ;; if request has a qualifier everything must match exactly, otherwise qualifier doesn't matter
  (and (or (not (:qualifier request)) (= (:qualifier actual) (:qualifier request)))
       ((if (and (:plus request) (not (:qualifier request))) (complement neg?) zero?)
        (compare-versions actual request))))

(defn safe-resolve [x]
  (try
    (or (when (special-symbol? x) x) (resolve x) (ns-resolve (the-ns 'miner.wilkins.features) x))
    (catch Exception _ nil)))

(defn lookup-feature [id]
  (when-let [vsym (safe-resolve id)]
    (if-let [has (contains? (meta vsym) :feature)]
      (let [feat (:feature (meta vsym))]
        (cond (true? feat) {} ;; var marked ^:feature
              (map? feat) feat  ;; normal feature map
              ;; possibly marked ^{:feature false} to disable feature
              :else nil))
      ;; class reference or var with no :feature info
      {})))

(defn request-satisfied? [req]
  (when-let [id (:feature req)]
    (when-let [actual (lookup-feature id)]
      (version-satisfies? actual req))))



(defn class-symbol? [x]
  (and (symbol? x) (not (namespace x)) (.contains (name x) ".") (class? (safe-resolve x))))


(defn feature? [request]
  (cond (not request) false
        (#{'else :else true} request) true
        ;;(special-symbol? request) true
        ;;(class-symbol? request) true
        (request? request) (request-satisfied? request)
        (or (symbol? request) (vector? request) (string? request))
          (request-satisfied? (as-feature-request request))
        (seq? request) (let [op (first request)]
                         (case op
                           quote (request-satisfied? {:feature (second request) :major :*})
                           and (every? feature? (rest request))
                           or (some feature? (rest request))
                           not (not (feature? (second request)))
                           (throw (ex-info (str "Malformed feature request: " (pr-str request))
                              {:request request}))))
        :else (throw (ex-info (str "Malformed feature request: " (pr-str request))
                              {:request request}))))


;; data-reader
(defn condf [fspecs-and-forms]
  ;; fspecs-and-forms is sequence of alternating feature specifications and forms.
  ;; the first feature specification to succeed returns the next form as the result.
  (let [result (first (keep (fn [[fspec form]] (when (feature? fspec) form)) 
                            (partition 2 fspecs-and-forms)))]
    (if (nil? result) '(quote nil) result)))

;; (quote nil) works around CLJ-1138


(declare satisfaction-test)

(defmacro conjunctive-satisfaction
  ([con] `(~con))
  ([con fspec] `(satisfaction-test ~fspec))
  ([con fspec & more] `(~con (satisfaction-test ~fspec) (conjunctive-satisfaction ~con ~@more))))

(defmacro satisfaction-test [req]
  (cond (not req) false
        (#{'else :else '(quote else) true} req) true
        (special-symbol? req) true
        (class-symbol? req) true
        (or (symbol? req) (vector? req) (string? req))  `(request-satisfied? '~(as-feature-request req))
        (seq? req) (case (first req)
                     quote (if (symbol? (second req))
                             `(request-satisfied? '{:feature ~(second req) :major :*})
                             `(satisfaction-test ~(second req)))
                     and `(conjunctive-satisfaction and ~@(next req))
                     or `(conjunctive-satisfaction or ~@(next req))
                     not `(not (satisfaction-test ~(second req)))
                     (throw (ex-info (str "Malformed feature request: " (pr-str req))
                              {:request req})))
          :else (throw (ex-info (str "Malformed feature request: " (pr-str req))
                              {:request req}))))

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
