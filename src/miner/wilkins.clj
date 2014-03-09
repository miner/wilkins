(ns miner.wilkins
  "Wilkins creature feature lib"
  (:require [clojure.string :as str]
            [miner.wilkins.parse :as p]
            miner.wilkins.features))


(defn simple-requirement? [x] (boolean (and (map? x) (:feature x))))

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
                              {:bad-fspec fspec}))))

(defn as-simple-requirement [rspec]
  (let [feat (as-feature rspec)]
    (if-not (:major feat)
      (assoc feat :major :*)
      feat)))

(defn parse-requirement [rstr]
  (let [feat (p/parse-feature rstr)]
    (as-simple-requirement feat)))

(defn as-version [vspec]
  (cond (nil? vspec) {:plus true :major :*}
        (map? vspec) vspec
        (integer? vspec) {:major vspec}
        (float? vspec) (p/parse-version (str vspec))
        :else (p/parse-version vspec)))

;; macro to make it easy to create a literal feature map from a version string or "feature
;; expression" (symbol or vector notation).  The map is the canonical form for a feature
;; requirement.

(defmacro version [vspec]
  `'~(as-version vspec))

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


(defn version-satisfies? [actual requirement]
  {:pre [(map? actual) (map? requirement)]}
  ;; args are feature maps
  ;; assumes ids were already matched (might have been equivalent aliases, not identical)
  ;; if requirement has a qualifier everything must match exactly, otherwise qualifier doesn't matter
  (and (or (not (:qualifier requirement)) (= (:qualifier actual) (:qualifier requirement)))
       ((if (and (:plus requirement) (not (:qualifier requirement))) (complement neg?) zero?)
        (compare-versions actual requirement))))

(defn safe-resolve [x]
  (try
    (or (when (special-symbol? x) x) (resolve x) (ns-resolve (the-ns 'miner.wilkins.features) x))
    (catch Exception _ nil)))

(defn feature-version [id]
  "Returns the version information map for the feature `id` (symbol)"
  (when-let [vsym (safe-resolve id)]
    (if-let [has (contains? (meta vsym) :feature)]
      (let [feat (:feature (meta vsym))]
        (cond (true? feat) {} ;; var marked ^:feature
              (map? feat) feat  ;; normal feature map
              ;; possibly marked ^{:feature false} to disable feature
              :else nil))
      ;; class reference or var with no :feature info
      {})))

(defn simple-requirement-satisfied? [req]
  (when-let [id (:feature req)]
    (when-let [actual (feature-version id)]
      (version-satisfies? actual req))))

(defn class-symbol? [x]
  (and (symbol? x) (not (namespace x)) (.contains (name x) ".") (class? (safe-resolve x))))

(defn feature? [requirement]
  (cond (simple-requirement? requirement) (simple-requirement-satisfied? requirement)
        (not requirement) false
        (#{'else :else true} requirement) true
        (or (symbol? requirement) (vector? requirement) (string? requirement))
          (simple-requirement-satisfied? (as-simple-requirement requirement))
        (seq? requirement) 
          (let [op (first requirement)]
            (case op
              quote (simple-requirement-satisfied? {:feature (second requirement) :major :*})
              and (every? feature? (rest requirement))
              or (some feature? (rest requirement))
              not (not (feature? (second requirement)))
              (throw (ex-info (str "Malformed feature requirement: " (pr-str requirement))
                              {:bad-requirement requirement}))))
        ;; the following tests are for not normally expected args, but logically they
        ;; should work
        (or (class? requirement) (fn? requirement) (var? requirement)) true
        :else (throw (ex-info (str "Malformed feature requirement: " (pr-str requirement))
                              {:bad-requirement requirement}))))


;; data-reader
(defn condf-reader [fspecs-and-forms]
  ;; fspecs-and-forms is sequence of alternating feature specifications and forms.
  ;; the first feature specification to succeed returns the next form as the result.
  (let [result (first (keep (fn [[fspec form]] (when (feature? fspec) form)) 
                            (partition 2 fspecs-and-forms)))]
    (if (nil? result) '(quote nil) result)))

;; (quote nil) works around CLJ-1138


(declare feature-test)

(defmacro conjunctive-test
  ([con] `(~con))
  ([con fspec] `(feature-test ~fspec))
  ([con fspec & more] `(~con (feature-test ~fspec) (conjunctive-test ~con ~@more))))

(defmacro feature-test [req]
  (cond (simple-requirement? req) `(simple-requirement-satisfied? req)
        (not req) false
        (#{'else :else '(quote else) true} req) true
        (special-symbol? req) true
        (class-symbol? req) true
        (or (symbol? req) (vector? req) (string? req))  
          `(simple-requirement-satisfied? '~(as-simple-requirement req))
        (seq? req) (case (first req)
                     quote (if (symbol? (second req))
                             `(simple-requirement-satisfied? '{:feature ~(second req) :major :*})
                             `(feature-test ~(second req)))
                     and `(conjunctive-test and ~@(next req))
                     or `(conjunctive-test or ~@(next req))
                     not `(not (feature-test ~(second req)))
                     (throw (ex-info (str "Malformed feature requirement: " (pr-str req))
                                     {:bad-requirement req})))
          :else (throw (ex-info (str "Malformed feature requirement: " (pr-str req))
                              {:bad-requirement req}))))

;; for use at runtime as opposed to readtime, but probably simpler to use `feature?`
(defmacro runtime-condf
  ([fspec form] `(if (feature-test ~fspec) ~form nil))
  ([fspec form & more] `(if (feature-test ~fspec) ~form (runtime-condf ~@more))))

(defmacro compile-if
 "Evals `compile-time-test` at compile time and chooses `then-expr` or `else-expr` as the
 expression.  Once it's compiled, the test will never be checked again. Note: Works at
 compile-time.  Be careful about AOT compilation."
 [compile-time-test then-expr else-expr]
 (let [result (try (eval compile-time-test) (catch Exception e nil))]
   (if result
     then-expr
     else-expr)))

(defmacro compile-condf
  "Like `feature-cond` but evals the feature requirements at compile time.  The compiler
ends up seeing just the result expression of the successful feature requirement. Note: Works
at compile-time.  Be careful about AOT compilation."
  ([fspec form] `(compile-if (feature-test ~fspec) ~form nil))
  ([fspec form & more] `(compile-if (feature-test ~fspec) ~form (compile-condf ~@more))))

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
