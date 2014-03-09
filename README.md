# wilkins

Experimental lib for Clojure conditional features.

## Usage

leiningen dependency `[com.velisco/wilkins "a.b.c"]`

where "a.b.c" stands for the version number shown below (from Clojars.org)

[![Wilkins on clojars.org][latest]][clojar]

[latest]: https://clojars.org/com.velisco/wilkins/latest-version.svg "Wilkins on clojars.org"
[clojar]: https://clojars.org/com.velisco/wilkins

The main namespace is `miner.wilkins`.

There are two ways to use wilkins.  First, there is a data-reader that implements
conditional compilation at read-time.  Second, there is a macro that implements conditional
compilation at compile time.  In both cases, feature expressions determine which form is
actually evaluated.

## Examples

In your `data_readers.clj` you can assign a tag to enable the conditional reader like this:

	{feature/condf miner.wilkins/condf-reader}
	
Then in your source:

	(require '[miner.wilkins :as w])
	
    #feature/condf [
	    (and jdk-1.6+ clj-1.5.*) (call-my-fast-reducer-code)
		else (some-old-fashioned-code)	]

Note that all the clause are contained in a vector literal so the data reader applies to the
whole vector as its single argument.  The pairs of forms are like a `cond` but the test is a
feature requirement expression. The data-reader will return the single form following the first
matching feature requirement.  That is the only form that will be seen by the compiler.

The macro `compile-condf` is similar but doesn't have require the vector notation around the
clauses.  It reads more like a `cond` but the feature expressions are evaluated at
compile-time.

    (w/compile-conf
		(and jdk-1.6+ clj-1.5.*) (call-my-fast-reducer-code)
		else (some-old-fashioned-code))

If you want to evaluate feature expressions at runtime, use `feature?`.

    (w/feature? 'clj-1.5+)
	
## Conditional Feature Reader

`condf` is short for "conditional feature".  The `condf` tag marks a sequence of clauses
similar to `cond`, but each test is a feature requirement.  The first feature requirement to be
satisfied causes the reader to return the following expression.  As a special case, the symbol
`else` always qualifies.  If no feature requirement is satisfied, the reader effectively returns
nil.

The feature requirement specifies a feature name and an optional version.  The feature
requirements are evaluated by the `condf-reader` data-reader at read-time so the compiler
never sees unsuccessful clauses.  The `compile-condf` macro is similar but all its forms are
read by the reader so it's slightly less flexible with expressions that might not be legal
in certain Clojure variants.  Note: We intend to support Clojurescript, but that is not currently
implemented.

## Feature Notation

A canonical "feature requirement" is defined by an identifier (symbol) and version
information in a map following the same scheme as `*clojure-version*`.  We'll give the
details below, but typically we use a compact notation for versions and feature requirements.

The compact form of a feature requirement uses a single symbol.  For example, `clj-1.4`
means Clojure 1.4.  The alphabetic part is the feature identifier and the dotted number part
is the version number, separated by a hyphen (-).  A trailing `+` means "or greater".  A
trailing `.*` means "any increment", but the previous parts must match exactly.  Only one
`+` or `*` is allowed in a feature requirement.  A qualifier string may follow the version
number (separated by a hyphen), but in that case an exact match is required.  For example,
`clj-1.5-RC1` matches exactly Clojure "1.5.0-RC1", and not any other version.  A hyphen is
required to separate the feature name from version, but the version information is optional.
Also, hyphens may appear in the feature name.  In the rare case in which your feature name
itself actually contains a hyphen followed by digit, you can either quote the symbol or use
the vector form of the feature requirement to avoid parsing confusion.

The vector form of a feature requirement contains an identifier symbol (not subject to
version parsing) and a version string.  This allows you to use a feature name that contains
a digit, such as `[lucky-7 "1.2+"]`.  The version string can be omitted if you don't care
about versioning.

## Feature Declaration

A feature declaration is simply a Clojure var with metadata declared for the `:feature`
key.  

    (require '[miner.wilkins :as w])
    (def ^{:feature (w/version "1.2.3")} foo 42)

The `version` macro expands a version string into a map form, similar to the style of
`*clojure-version*`, with the keys `:major`, `:minor`, `:incremental` and `:qualifier`.  A
map-entry may be omitted if the value is nil.  No wildcards are allowed when declaring a version.

A feature requirement without a namespace implicitly refers to the current namespace
(`*ns*`) or the `miner.wilkins.features` namespace which has a few predefined features.  For
example, `miner.wilkins/clj` is a var with metadata defining the current Clojure version
(taken from `*clojure-version*`).  `clojure` is a synonym for `clj`.  The vars `java` and
`jdk` have the version of the JDK.

A feature requirement without a version simply requires that var exist.  You can also
require that a Java class exists by using its name as the feature symbol (without any
version).  As in Clojure, a Java class is named by a simple symbol (no namespace) containing
internal periods -- for example, `java.util.concurrent.ForkJoinPool`.

Boolean combinations are encoded as list expressions beginning with `and`, `or` or `not`.
For example: `(and jdk-1.6+ clj-1.5+)`.

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

## Canonical Feature Requirement

The canonical feature requirement is a map similar to `*clojure-version*`.

The "version" part has possible keys (with value types): `:major` (int), `:minor` (int),
`:incremental` (int), and `:qualifier` (string).  For informational purposes it might also
contain `:version` (string).

The feature requirement has the key `:feature` with a symbol as the value, and the version
keys described above.  It can also have the key `:plus` (boolean).  The least significant of
the `:major`, `:minor`, or `:incremental` values may be `:*` (a keyword) in addition to the
usual int value.  The `:*` matches any value.  For example, 1.4.* matches any version in the
1.4 series (1.4.0, 1.4.1, etc.) but not 1.5. If the version doesn't matter, then :major
should be given as `:*`.  (See `as-feature-request`.)  When the `:plus` value is true, then
the version matches exactly the given :major, :minor, and :incremental keys or anything
greater.  For example, 1.4+ matches 1.4.1, 1.5.0, etc.  If a `:qualifier` (string) is
specified as a requirement, then the match must be exact, and wildcards are not allowed.


## Runtime

If you're using AOT ("ahead of time") compilation, you have to be careful that your actual
runtime corresponds to the compilation environment.  If the conditional compilation depended
on JDK 1.7, but you later use that jar with JDK 1.6 you might have a problem.  If you want
to do your feature tests at runtime (or in a REPL), use the `feature?` predicate and pass it
feature expression.  (Note the you typically have to quote a symbolic requirement since this
is a normal function call, not a macro.)

    (cond
	   (w/feature? '(and clj (not foo-2.4+)))  (do-something 1 2)
	   (w/feature? '(or clj-1.5 [clj "1.4"]))  :clj 
	   :else  :something-else)


## Clojurescript not implemented yet

Wilkins is intended to be useful for conditional code across variants of Clojure (such as
ClojureScript) where the platform or host language may be so different that runtime checks
would not be feasible.  We have not yet implemented the ClojureScript version of
`condf-reader` so that needs a bit more work to validate the approach.


## Bob Wilkins

Named in honor of [Bob Wilkins](http://en.wikipedia.org/wiki/Bob_Wilkins), host of KTVU's
["Creature Features"](http://www.bobwilkins.net/creaturefeatures.htm) (1971-79).

## License

Copyright © 2014  Stephen E. Miner

Distributed under the Eclipse Public License, the same as Clojure.
