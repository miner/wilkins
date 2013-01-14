# wilkins

Experimental lib for Clojure conditional feature reader using tagged literals

## Usage

leiningen `[com.velisco/wilkins "0.1.4"]`

## Example

In your `data_readers.clj` you can assign a tag to enable the conditional reader like this:

	{feature/condf miner.wilkins/condf}
	
Then in your source:

	(require 'miner.wilkins)
	
    (println #feature/condf [(and jdk1.6+ clj1.5.*) "Ready for reducers" else "No reducers for you."])

User code can provide a feature like this:

    (provide 'foo.bar/baz1.3)
	
The `provide` function will use the current namespace `*ns*` if given an unqualified symbol.

## Conditional Feature Reader

`condf` is short for conditional feature reader.  The `condf` tag prefaces a sequence of clauses
similar to `cond`, but each test is a feature requirement.  The first feature requirement to be
satisfied causes the reader to return the following expression.  As a special case, the symbol
`else` always qualifies.  If no feature requirement is satisfied, the reader effectively returns
nil.  (Actually, it returns '(quote nil) in order to work around CLJ-1138, but that's a minor
technicalilty.)

The feature requirement specifies a feature name and an optional version.  Feature requirements are
evaluated against the available features to determine if the requirement is fulfilled.  The feature
requirements are evaluated by the `condf` data-reader at read-time so the compiler never sees
unsuccessful clauses.

The compact form of a feature requirement uses a single symbol.  For example, `clj1.4` means Clojure
1.4.  The alphabetic part is the feature name, and the dotted number part is the version number.  A
trailing `+` means "or greater".  A trailing `.*` means "any increment", but the previous parts must
match exactly.  Only one `+` or `*` is allowed in a feature requirement.  A qualifier string may
follow the version number, but in that case an exact match is required.  For example, `clj1.5-RC1`
matches exactly Clojure "1.5-RC1", and not any other version.

Unqualified feature names are reserved for Wilkins.  Users may create namespace-qualified names
following the usual convention for ownership of the namespace.  

A feature requirement can also be a literal vector of a name symbol and a version string.  This allows you to use
a feature name that contains a digit, such as `[foo.bar/i18n "1.2+"]`.

A var feature requirement simply requires that the named var has been declared.  It is specified
with #' macro, such as `#'clojure.core/*data-readers*`.  There is no versioning for vars.

Boolean combinations use list expressions beginning with `and`, `or` or `not`.  For example: `(and
jdk1.6+ clj1.5+)`.

Available features are kept in an atom `feature-map`.  It initially has features for the keys
`clojure` and `java`, plus any features declared by the property `wilkins.features`.  The property's
value should be a string of compact features separated by spaces.  For example, you could use
`-Dwilkins.features="x/foo1.2.3 y/bar4.5"` in your Java command line to declare feature `x/foo`1.2.3
and `y/bar`4.5.  For convenience, `clj` is a synonym for `clojure` and `jdk` is a synonym for `java`
in the default `@feature-map`.

The `provide` function provides a feature in user code.  If the symbol does not have a namespace,
the current `*ns*` will be used (usually, the namespace of the source file.)  A source file should
not normally provide a feature in another namespace, but it is currently allowed to do so.
Typically, you'll leave the namespace off and take the default `*ns*`.


## Details

The atom `feature-map` is a map of feature symbols to features.  Each feature is a map with keys: `:feature`
(value: symbol), `:version` (value: list of ints), and `:qualifier` (value: a String).  The :version
is a list of ints corresponding to the version string.  So "1.2.3" is :version (1 2 3).  Feature
requirements are encoded in the same sort of map, but with an additional key :plus (value: true or
false) to indicate if the plus (+) was included in the feature requirement.

A feature version requirement is one of:
* unqualified symbol, for example: `clj1.4`
* a vector of [name "ver"], for example: `[clj "1.4+"]`

A var feature requirement is one of:
* a #' symbol, for example: `#'foo.bar/baz`
* a list starting with `var`, for example: `(var foo.bar/baz)`

A boolean feature requirement is
* a list starting with `and`, `or`, or `not` combining other feature requirements


## Runtime

The `feature-cond` macro is like `cond` but the tests are feature specifications (unquoted as in the
`condf` data-reader).  This macro, of course, performs the checks at runtime.

    (feature-cond (and clj (not foo2.4+)) (do-something 1 2)
	              (or clj1.5 [clj "1.4"]) :clj 
				  else :something-else)

For conditional code based on the particular version of the JDK or Clojure, it is usually best to
use the `feature-cond` macro rather than the data-reader `condf`.  

On the other hand, `condf` is intended to be useful for conditional code across variants of Clojure
(such as ClojureScript) where the platform or host language may be so different that runtime checks
would not be feasible.  We have not yet implemented the ClojureScript version of `condf` so that
needs a bit more work to validate the approach.


## Bob Wilkins

Named in honor of [Bob Wilkins](http://en.wikipedia.org/wiki/Bob_Wilkins), host of KTVU's
["Creature Features"](http://www.bobwilkins.net/creaturefeatures.htm) (1971-79).

## License

Copyright © 2012 Stephen E. Miner

Distributed under the Eclipse Public License, the same as Clojure.
