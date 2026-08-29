# The `data` layer code standard

One of the [code standards](general-code-standard.md), which hold the components a feature is made
of and what each layer may import from another. This one covers the two components the `data` layer
contributes: the entity and the repository. The shape of the table underneath them is
[the database standard](database-standard.md).

## The entity

**An entity is a typed row, and it names its table explicitly.**

```kotlin
@Serdeable
@MappedEntity("…")
class …Entity(
    val …: UUID,
    val …: LocalDateTime,
    val …: LocalDateTime? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}
```

**The identifier is nullable, `var`, and declared in the body rather than the constructor.** It is
the one property the application does not supply: the database mints it on insert, so an entity on
its way to `save` genuinely has none, and the null means "not persisted yet" rather than "has no
value". Putting it in the body keeps the constructor a list of the columns a caller must decide,
which is what makes a missing one a compile error instead of a null.

That nullability stops at the layer boundary. A row read back always has an identifier, so a
business model's identifier is non-null and the [mapper](business-layer-code-standard.md) is where
the two meet — a row that somehow arrives without one fails there, loudly, rather than propagating a
null into the domain.

**An entity mirrors the column, not the domain.** A closed set of values is stored as a `String`
because the column is text; a list is an `Array<String>` because the column is an array; a duration
is the integer its column holds. Narrowing those into enums, lists and value types is the mapper's
job one layer up. An entity that is already the domain type has quietly moved the failure — a value
outside the enum then fails inside the driver, with a message about a column rather than about a
row.

**A timestamp is a domain value, not an audit column.** The framework's automatic created- and
updated-at annotations are not used: every date here means something specific — when a consent was
given, when a token expires, when a session was abandoned — and is set by the manager that knows
which. A pair of framework timestamps beside them would be a second, weaker answer to a question the
domain already answers precisely.

**Every date is a `LocalDateTime`, and it is UTC.** The application forces the JVM's default zone to
UTC at startup, which is what makes a zoneless type unambiguous here. That is a decision the type
does not carry on its own, so it is worth knowing rather than inferring: nothing stops a
`LocalDateTime` from meaning a wall clock somewhere, and here it never does.

## The repository

**A repository declares queries and nothing else.**

```kotlin
interface …Repository : CoroutineCrudRepository<…Entity, UUID> {

    suspend fun findBy…(…): …Entity?
}
```

No validation, no authorization, no transaction annotation, no defaulting, no derived field. That is
stricter than any single case needs, deliberately: a method that quietly filters out revoked rows,
or an entity initialiser that normalises a value, is a rule no reader of the `business` layer can
see, and invisible rules are how two layers end up disagreeing about what the data means.

**Every repository has two empty implementations, one per dialect.** One in the PostgreSQL package
and one in the H2 package, each an interface extending the shared one, each carrying the dialect
annotation and the condition that selects it from the configured datasource. They hold no methods.

This is the price of [supporting two databases](architecture.md#two-databases-one-schema) and it is
paid per repository, so it is the step most likely to be forgotten. A missing twin is not a compile
error — it is a bean that does not exist on one of the two databases, which surfaces as a startup
failure on whichever one the author was not running.

**A derived update method is named for what it sets, and never uses `And`.** The name after `update`
names the column being set; every further column is an extra parameter, and the identifier is
annotated so it is read as the row to match rather than a value to write. `And` is how the derived
query parser joins *criteria*, so a name that uses it to join assignments is parsed as something
else entirely — a method that either fails at startup or updates by the wrong predicate.

**A delete returns the number of rows it removed.** It is an `Int`, not `Unit`, which matters most
where it is easy to assume otherwise: a test that stubs one has to say what it returns, and a caller
that ignores it is choosing to.

## Queries the derived form cannot express

**A raw query is a last resort, and it must be written in the intersection of both dialects.** There
is no dialect-specific override mechanism here — one string is sent to PostgreSQL and to H2 — so a
construct only one of them understands is a failure on the other, found by whichever test runs
second.

**A parameter whose type the driver cannot infer carries an explicit type definition.** An array
bound into a raw query is the case that occurs here. This is invisible in review and produces no
compile error; it produces a binding failure at runtime, which is why the
[testing standard](testing-standard.md) asks for a repository test against a real database rather
than a mock for anything raw.

**A criteria query is an extension function on the repository interface**, in the same file. It
keeps the composed query beside the declared ones instead of in a manager, and it reads at the call
site as another method on the repository — which is what it is, minus the framework's ability to
derive it.

## What this standard does not cover

**Soft deletes.** Some rows are revoked rather than removed, and that is a domain state with its own
column, not a deletion the layer hides. Nothing here filters rows on the way out, and the moment
something does, it stops being a repository and becomes a rule.

**Pagination in the repository.** Page and size arrive as ordinary parameters from above; the layer
has no opinion about defaults or caps. Those are [the API standard's](api-standard.md).

**Read replicas, sharding and any second datasource.** One logical database, one connection pool.

---

← [Design documentation](index.md)
