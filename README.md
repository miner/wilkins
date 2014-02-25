# wilkins

Experimental lib for Clojure conditional feature reader using tagged literals

## Usage

leiningen `[com.velisco/wilkins "0.1.8"]`

In case I forget to update the version number here in the README, the latest version is available on
Clojars.org:

[![Wilkins on clojars.org][latest]][clojar]

[latest]: https://clojars.org/com.velisco/wilkins/latest-version.svg "Wilkins on clojars.org"
[clojar]: https://clojars.org/com.velisco/wilkins


## Example

In your `data_readers.clj` you can assign a tag to enable the conditional reader like this:

	{feature/condf miner.wilkins/condf}
	
Then in your source:

	(require 'miner.wilkins)
	
    (println #feature/condf [(and jdk-1.6+ clj-1.5.*) "Ready for reducers" else "No reducers for you."])

Note that all the clause are contained in a vector literal so the data reader applies to the
whole vector as its single argument.

## Conditional Feature Reader

`condf` is short for conditional feature reader.  The `condf` tag prefaces a sequence of clauses
similar to `cond`, but each test is a feature requirement.  The first feature requirement to be
satisfied causes the reader to return the following expression.  As a special case, the symbol
`else` always qualifies.  If no feature requirement is satisfied, the reader effectively returns
nil.

The feature requirement specifies a feature name and an optional version.  The feature
requirements are evaluated by the `condf` data-reader at read-time so the compiler never sees
unsuccessful clauses.

The compact form of a feature requirement uses a single symbol.  For example, `clj-1.4`
means Clojure 1.4.  The alphabetic part is the feature name, and the dotted number part is
the version number, separated by a hyphen (-).  A trailing `+` means "or greater".  A
trailing `.*` means "any increment", but the previous parts must match exactly.  Only one
`+` or `*` is allowed in a feature requirement.  A qualifier string may follow the version
number (separated by a hyphen), but in that case an exact match is required.  For example,
`clj-1.5-RC1` matches exactly Clojure "1.5.0-RC1", and not any other version.  A hyphen is
required to  separate the feature name from version, but the version information is
optional.  Also, hyphens may be used in the feature name as well.  However, if the feature
name itself contains a hyphen followed by digit, the parser will be confused.  In that case, you
can either quote the symbol or use the vector form of the feature requirement.

A feature requirement can also be a literal vector of a name symbol and a version string.  This allows you to use
a feature name that contains a digit, such as `[lucky-7 "1.2+"]`.  The version string can be
omitted if you don't care about versioning.

A feature declaration is simply a Clojure var with metadata declared for the `:feature`
key.  

    (require '[miner.wilkins :as w])
    (def ^{:feature (w/version "1.2.3")} foo 42)

The `version` macro expands a version string into a map form, similar to the style of
`*clojure-version*`, with the keys `:major`, `:minor`, `:incremental` and `:qualifier`.  A
map-entry may be omitted if the value is nil.

A feature requirement without a namespace implicitly refers to the current namespace
(`*ns*`) or the `miner.wilkins.features` namespace which has a few predefined features.  For
example, `miner.wilkins/clj` is a var with metadata defining the current Clojure version
(taken from `*clojure-version*`).  `clojure` is a synonym for `clj`.  The vars `java` and
`jdk` have the version of the JDK.

A feature requirement without a version simply requires that var exist.  You can also
require that a Java class exists by using its name as the feature symbol (without any
version).  As in Clojure, a Java class is named by a simple symbol (no namespace) containing
internal periods -- for example, `java.util.concurrent.ForkJoinPool`.

Boolean combinations use list expressions beginning with `and`, `or` or `not`.  For example: `(and
jdk-1.6+ clj-1.5+)`.


## Summary

A feature version requirement is one of:
* `else` and `true` immediately succeed
* `nil` and `false` immediately fail
* a namespaced symbol, for example `my.ns/my-feature-2.3+`  (checks against :feature
  metadata of `#'my.ns/my-feature`)
* unqualified symbol, for example: `clj-1.4` (implicitly checks `*ns*` and `miner.wilkins.features`)
* a vector of `[name "ver"]`, for example: `[clj "1.4+"]` (the feature name is a literal symbol)
* a list of `(quote name)` to suppress the parsing of version information in the compact
  symbol form.

A boolean feature requirement is
* a list starting with `and`, `or`, or `not` combining other feature requirements.

Here are a few examples to show the how the parsing works for the compact symbol form:
*  `lucky-7` (no quote) expands into `{:feature lucky :major 7}`
*  `'lucky-7` (quoted) expands into `{:feature lucky-7 :major :*}`
*  `[lucky-7]` (vector without version) also expands into `{:feature lucky-7 :major :*}`


## Runtime

The `feature-cond` macro is like `cond` but the tests are feature specifications (unquoted as in the
`condf` data-reader).  This macro, of course, performs the checks at runtime.

    (feature-cond 
	   (and clj (not foo-2.4+))  (do-something 1 2)
	   (or clj-1.5 [clj "1.4"])  :clj 
	   else  :something-else)

For conditional code based on the particular version of the JDK or Clojure, it is usually
best to use the `feature-cond` macro rather than the data-reader `condf`.  Be careful about
ahead-of-time compilation (AOT) with data-readers -- the compiled code will be conditioned
by the compile-time environment.  It will not be "read" again when loaded from the jar into
the current runtime, which might have a different JDK or Clojure version.

On the other hand, `condf` is intended to be useful for conditional code across variants of Clojure
(such as ClojureScript) where the platform or host language may be so different that runtime checks
would not be feasible.  We have not yet implemented the ClojureScript version of `condf` so that
needs a bit more work to validate the approach.


## Bob Wilkins

Named in honor of [Bob Wilkins](http://en.wikipedia.org/wiki/Bob_Wilkins), host of KTVU's
["Creature Features"](http://www.bobwilkins.net/creaturefeatures.htm) (1971-79).

## License

Copyright © 2014  Stephen E. Miner

Distributed under the Eclipse Public License, the same as Clojure.
