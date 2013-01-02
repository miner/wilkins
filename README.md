# wilkins

Experimental lib for Clojure conditional reader using tagged literals

## Usage

leiningen `[com.velisco/wilkins "0.1.2"]`

## Example

In your `data_readers.clj` you can assign a tag to enable the conditional reader like this:

	{feature/condf miner.wilkins/condf}
	
Then in your source:

	(require 'miner.wilkins)
	
    #feature/condf [(and jdk1.6+ clj1.5.*) (require 'clojure.core.reducers)]

User code can provide a feature like this:

    (provide foo.bar/baz1.3)
	
Notice: no quotes on the arg.  The `provide` macro expects an unquoted literal feature specification.

## Conditional Feature Reader

`condf` is short for conditional feature reader.  The `condf` tag prefaces a sequence of clauses
similar to `cond`, but each test is a feature requirement.  The first feature requirement to be
satified causes the reader to return the following expression.  As a special case, the symbol `else`
always qualifies.  If no feature requirement is satisfied, the reader effectively returns nil.
(Actually, it returns '(quote nil) in order to work around CLJ-1138, but that's a minor
technicalilty.)

The feature requirements are evaluated by the `condf` data-reader.  Basically, the feature
requirement specifies both a feature id and a version.  Feature requirements are evaluated against
the available features to determine if the requirement is fulfilled.

The compact form of a feature requirement uses a single symbol.  For example, `clj1.4` means Clojure
1.4.  The alphabetic part is the feature id, and the dotted number part is the version number.  A
trailing `+` means "or greater".  A trailing `.*` means "any increment", but the previous parts must
match exactly.  Only one `+` or `*` is allowed in a feature requirement.  A qualifier string may
follow the version number, but in that case an exact match is required.  For example, `clj1.5-RC1`
matches exactly Clojure "1.5-RC1", and not any other version.

Unqualified feature IDs are reserved for Wilkins.  Users may create namespace-qualified IDs
following the usual convention for ownership of the namespace.  

A feature requirement can also be a literal vector of ID and version string.  This allows you to use
a feature ID that contains a digit, such as `[foo.bar/i18n "1.2+"]`.

A var feature requirement simply requires that the named var has been declared.  It is specified
with #' macro, such as `#'clojure.core/*data-readers*`.  There is no versioning for vars.

Boolean combinations use list expressions beginning with `and`, `or` or `not`.  For example: `(and
jdk1.6+ clj1.5+)`.

Available features are kept in an atom `feature-map`.  It initially has features for
the keys: `clj`, `clojure`, `jdk` and `java`, plus any features declared by the property
`wilkins.features`.  The value should be a string of compact features separated by spaces.  For
example, you could use `-Dwilkins.features="x/foo1.2.3 y/bar4.5"` in your Java command line to
declare feature `x/foo`1.2.3 and `y/bar`4.5.

The `provide` macro provides a
feature in user code.  The argument is an unquoted feature specification.  If the symbol does not
have a namespace, the current `*ns*` will be used (essentially, the namespace of the source file.)




## Details

The atom `feature-map` is a map of feature IDs to features.  Each feature is a map with keys: `:id`
(value: symbol), `:version` (value: list of ints), and `:qualifier` (value: a String).  The :version
is a list of ints corresponding to the version string.  So "1.2.3" is :version (1 2 3).

A feature version requirement is one of:
* unqualified symbol, for example: `clj1.4`
* a vector of [id "ver"], for example: `[clj "1.4+"]`

A var feature requirement is one of:
* a #' symbol, for example: `#'foo.bar/baz`
* a list starting with `var`, for example: `(var foo.bar/baz)`

A boolean feature requirement is
* a list starting with `and`, `or`, or `not` combining other feature requirements


## Bob Wilkins

Named in honor of [Bob Wilkins](http://en.wikipedia.org/wiki/Bob_Wilkins), host of KTVU's
["Creature Features"](http://www.bobwilkins.net/creaturefeatures.htm) (1971-79).

## License

Copyright © 2012 Stephen E. Miner

Distributed under the Eclipse Public License, the same as Clojure.
