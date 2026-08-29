# The `business` layer code standard

One of the [code standards](general-code-standard.md), which hold the components a feature is made
of and what each layer may import from another. This one covers the three the `business` layer
contributes: the model, the mapper, and the manager.

## The model

**Plain Kotlin, no framework annotations, and valid by construction.** If a model exists, it is a
real one: every closed set of values is an enum, every identifier is present, nothing is a
placeholder standing in for a value that failed to parse. Nothing downstream re-checks it.

That guarantee is only worth something if there is exactly one door into the type — see
[the mapper](#the-mapper), which is that door.

**A concept with more than one shape is a sealed hierarchy, not a flag.** A scope that came from
consent, a scope granted by a rule and a scope only a client may hold are three types with one
supertype, because the difference decides what each may be used for. A single type with a `kind`
field pushes that decision to every call site, and the call site that forgets is the one that issues
a token it should not have.

The same shape covers a thing that may be unusable: a configured item that failed validation is a
disabled variant carrying the reason, not a null and not an item with a flag. The caller then has to
say what it does about the disabled case in order to compile.

## The mapper

**The mapper is the only place a row becomes a model, and the only place a row is rejected.** A
column that no longer parses — a value outside its enum, a document that will not deserialize, a
reference whose target is gone — is a corrupt row, and it fails here by name rather than propagating
a null into the domain. The error code carries the mapper and the property, because "this row is
bad" is not one operational problem: a value outside an enum is data someone wrote, a missing
attached record is a write that half-succeeded, and the two need different people.

**Mapping toward the domain validates; mapping back does not.** The input in that direction is a
model, which is already valid by construction, so a check there would either be dead code or an
admission that the constructor's guarantee is a lie. The table's own constraints remain the backstop
on write.

**An unmapped target property fails the build.** The generator is configured to treat a target
property nothing maps to as an error rather than a warning, which is what makes adding a field to a
model safe: the build stops until every mapper has been told where the value comes from. The reverse
policy is deliberately lax — an entity column the domain does not care about is not an error.

**Mappers do not call repositories.** A mapper is a function from the rows it is handed. If a
mapping needs a second row, the manager fetches it and passes it in. A mapper that can query is one
that can issue a query per element from inside a loop nobody thought was doing I/O.

**A generated mapper is registered in a factory, and that is not optional.** The generator produces
a class with a no-argument constructor, so it is not a bean and its collaborators are not injected;
the factory is what publishes it and what sets those collaborators on the instance. A mapper that
exists but is not registered fails at startup, on the first bean that asks for it — and the
generated class must also be declared to
[the native image](native-image-standard.md), which fails much later and only in production.

## The manager

**The manager is the use case, and it is the transaction boundary.** It is a singleton, its
dependencies arrive through the constructor, and every method is `suspend`.

`@Transactional` belongs here and nowhere else. It is on the manager because the manager is the only
layer that knows which writes belong together; on a repository it would be too narrow to span two
tables, and on a controller it would hold a connection open across serialization. **It is imported
from Micronaut's own package**, not the Jakarta one — both are honoured, and one spelling means a
reader never has to check which semantics apply.

A class with a transactional method is `open`, and so is the method, for
[the reason every AOP annotation needs](general-code-standard.md#rules-that-compile-and-then-fail).

**A manager never lets an entity past its own boundary.** It may hold one — it has to, to hand it to
the mapper — but it never returns one, never accepts one as a parameter, and never puts one in a
model's field. The entity type stops inside the manager's own method bodies. This is the concrete
form of [a model per layer](architecture.md#a-model-per-layer), and when it breaks it breaks here
first, in a method that returns the entity because it was quicker.

### Naming

The verb says what kind of work the method does, and the reader should not have to open it to find
out:

| Verb | Does |
| --- | --- |
| `find…` | looks a row up by criteria; may match nothing |
| `fetch…` | reads a record attached to one the caller already holds |
| `list…` | reads many |
| `get…` | derives, computes or reads configuration rather than querying |
| `create…`, `save…` | writes a new row |
| `mark…` | moves an existing row to a new state |
| `revoke…`, `delete…` | ends something |
| `parse…` | turns untrusted input into a domain type |
| `validate…`, `check…` | throws when a rule is broken, and returns nothing |
| `is…`, `are…`, `can…` | answers a question as a `Boolean` |

The `…OrNull` rule from [the general standard](general-code-standard.md#naming) applies on top of
any of them, and the throwing twin delegates to the nullable one so that a single error code covers
every caller.

**`validate…` throws; it does not return a `Boolean`.** A boolean is ignorable, and the one call
site that forgets to check it is a rule silently not enforced. Where a caller genuinely needs the
answer as a value — offering an action rather than refusing it — that is a separate `can…` beside
the assertion, and the assertion calls it.

### Visibility

| Visibility | For |
| --- | --- |
| public | the manager's API, what other managers and controllers call |
| `internal` | a helper that exists to keep a public method readable |
| `open` | public, and transactional |
| `internal open` | a helper that is its own transaction |

`internal` rather than `private` for helpers, because a helper worth extracting is usually worth
testing directly, and the test lives in the same module.

### Composition

**Managers are split by domain and compose by injection.** One manager per concept — user, client,
consent, token, session — and a use case that spans two injects the other rather than reaching into
its repository. Splitting by caller instead would put an admin copy of the domain below the HTTP
boundary, which [architecture](architecture.md#the-split-stops-at-api) rules out.

**Where a manager needs a record the caller already holds, it takes it as a parameter.** A method
that re-fetches what its caller just fetched turns one read into three across a request, and the
copies can disagree. The fetch happens once, at the top of the flow, and the record is threaded
downward — a nullable parameter that the first method validates and returns non-null is the usual
shape.

**A cycle between two managers is broken with field injection, and it is a smell, not a pattern.**
Where it appears, the two managers usually share a concept that wants to be a third.

## What this standard does not cover

**Authorization as a component.** There is no policy class here: a manager checks what it must
inline, and the surface gate does the rest. Whether row-level checks deserve their own component is
a real question the moment a second caller needs the same rule.

**Domain events.** A manager's effects all happen inside its own call. See
[the general standard](general-code-standard.md#what-this-standard-does-not-cover).

---

← [Design documentation](index.md)
