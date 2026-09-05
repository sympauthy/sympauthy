---
description: What the `business` layer contributes — the model, the mapper, and the manager that
  owns the transaction.
paths:
  - "server/src/main/kotlin/com/sympauthy/business/**"
---

# The `business` layer code standard

One of the [code standards](general-code-standard.md). This one covers the components the
`business` layer contributes: the model, the mapper, and the manager.

## The model

**A model is plain Kotlin, valid by construction, and carries no framework annotation.** Make every
closed set of values an enum and every identifier present, so that nothing downstream re-checks it.

**A concept with more than one shape is a sealed hierarchy.** A scope that came from consent, one
granted by a rule and one only a client may hold each become a type under one supertype.

**A configured item that failed validation is a disabled variant carrying the reason.** The caller
then has to say what it does about the disabled case in order to compile.

**A model one manager alone builds is declared inside it.** `ScopeSearchManager.ScopeWithClaims` is
a row of that manager's answer and nothing else assembles one, so the model package keeps what more
than one caller shares.

## The mapper

**The mapper is the only door into a model, and the only place a row is rejected.** A column outside
its enum, a document that will not deserialize and a reference whose target is gone fail here, under
an error code naming the mapper and the property.

**Mapping toward the domain validates; mapping back is a translation.** The input in that direction
is already valid, and the table's constraints remain the backstop on write.

**An unmapped target property fails the build.** The generator is configured to treat it as an error
rather than a warning; an entity column the domain ignores stays lax.

**A mapper is a function of the rows it is handed.** Where a mapping needs a second row, the manager
fetches it and passes it in.

**A generated mapper is registered in `BusinessMapperFactory`**, which publishes it and sets its
collaborators. Declare its generated implementation to
[the native image](native-image-standard.md) in the same change.

## The manager

**The manager is the use case, and it is the transaction boundary.** It is a singleton, its
dependencies arrive through the constructor, and every method is `suspend`.

**`@Transactional` belongs on the manager**, imported from Micronaut's own package.

**A class carrying a transactional method is `open`, and so is the method.** See
[the general standard](general-code-standard.md#rules-that-compile-and-then-fail).

**A manager keeps the entity type inside its own method bodies.** It never returns one, accepts one
as a parameter, or puts one in a model's field.

### Naming

The verb says what kind of work the method does:

| Verb | Does |
| --- | --- |
| `find…` | looks a row up by criteria; may match nothing |
| `fetch…` | reads a record attached to one the caller already holds |
| `list…` | reads many |
| `count…` | reads how many, without reading them |
| `get…` | derives, computes or reads configuration rather than querying |
| `create…`, `save…` | writes a new row |
| `mark…` | moves an existing row to a new state |
| `revoke…`, `delete…` | ends something |
| `parse…` | turns untrusted input into a domain type |
| `validate…`, `check…` | throws when a rule is broken, and returns nothing |
| `is…`, `are…`, `can…` | answers a question as a `Boolean` |

**The `…OrNull` rule from [the general standard](general-code-standard.md#naming) applies on top of
any of them.** The throwing twin delegates to the nullable one, so one error code covers every
caller.

**`validate…` throws.** Where a caller needs the answer as a value, write a `can…` beside it and
have the assertion call it.

### Visibility

| Visibility | For |
| --- | --- |
| public | the manager's API, what other managers and controllers call |
| `internal` | a helper that exists to keep a public method readable |
| `open` | public, and transactional |
| `internal open` | a helper that is its own transaction |

**A helper is `internal` rather than `private`**, so the test in the same module can reach it.

### Composition

**Managers are split by domain and compose by injection.** One manager per concept, and a use case
spanning two injects the other rather than reaching into its repository.

**Reading a collection a surface pages is one method.** It takes one parameter per criterion plus
the `PageParams` the caller asked for, and answers a `Page` it ordered itself, so that the criteria,
the order and the slice can become one query.

**A listing that is a concept of its own is a `…SearchManager`**, composing the manager that owns
the concept or reading the repositories where it is that manager. A manager whose whole subject is
that one collection keeps its listing, the way `ClientUserManager` does.

**A row a `…SearchManager` assembles is read one at a time by that same manager.** A surface
publishing one of those rows on its own calls its `find…OrNull`, rather than fetching the concept
and what the row carries beside it apart and pairing them itself.

**A manager takes a record the caller already holds as a parameter.** Fetch once at the top of the
flow and thread the record downward, usually as a nullable parameter the first method validates and
returns non-null.

**A cycle between two managers is broken with field injection, and it is a smell.** The two usually
share a concept that wants to be a third manager.

## What this standard does not cover

**Authorization as a component.** A manager checks what it must inline and the surface gate does the
rest; a second caller needing the same row-level rule is what would force the question.

**Domain events.** A manager's effects all happen inside its own call. See
[the general standard](general-code-standard.md#what-this-standard-does-not-cover).

---

← [Design documentation](index.md)
