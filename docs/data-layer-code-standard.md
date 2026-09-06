---
description: The entity and the repository, what each may hold, and the dialect twins every
  repository needs.
paths:
  - "server/src/main/kotlin/com/sympauthy/data/**"
---

# The `data` layer code standard

One of the [code standards](general-code-standard.md). This one covers the components the
`data` layer contributes: the entity and the repository. The shape of the table underneath them is
[the database standard](database-standard.md).

## The entity

**An entity is a typed row that carries `@Serdeable` and names its table with
`@MappedEntity`.**

**The identifier is nullable, `var`, declared in the body, and carries `@Id` and
`@GeneratedValue`.** The constructor then lists the columns a caller must decide:

```kotlin
class …Entity(val …: UUID) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}
```

**That nullability stops at the layer boundary.** A business model's identifier is non-null, and the
[mapper](business-layer-code-standard.md) is where a row arriving without one fails.

**An entity mirrors the column, not the domain.** Store a closed set of values as a `String`, a list
as an `Array<String>`, a duration as the integer its column holds, and narrow them in the mapper.

**A timestamp is a domain value set by the manager that knows what it means.** The framework's
automatic created- and updated-at annotations stay off.

**Every date is a `LocalDateTime`, and it is UTC.** The application forces the JVM's default zone to
UTC at startup.

## The repository

**A repository declares queries and nothing else:**

```kotlin
interface …Repository : CoroutineCrudRepository<…Entity, UUID> {

    suspend fun findBy…(…): …Entity?
}
```

**Validation, authorization, transactions, defaulting and derived fields live above this layer.** A
rule written here is one no reader of the `business` layer can see.

**A repository has an empty implementation in every dialect package.** Each extends the shared
interface and carries the dialect annotation and the condition that selects it from the configured
datasource.

**A derived update method is named for the column it sets.** Pass every further column as a
parameter and annotate the identifier, so the name never joins assignments with `And`.

**A delete returns the number of rows it removed**, as an `Int`.

## Queries the derived form cannot express

**A raw query is written in the intersection of every dialect.** One string is sent to all of
them, so keep every construct to what each of them understands.

**A parameter whose type the driver cannot infer carries an explicit type definition.** An array
bound into a raw query is the case that occurs here.

**A `json` column is written through a derived method, never through a raw query.** A map bound as a
raw-query parameter is stored as its `toString()` and fails on the next read; where a raw statement
is also needed, pair it with the derived write inside one transaction.

**Anything raw is covered by a repository test against a real database of every dialect.** A green
run against one of them proves half the query. See [the testing standard](testing-standard.md).

**A criteria query is an extension function on the repository interface**, in the same file.

## What this standard does not cover

**Soft deletes.** A revoked row is a domain state with its own column, and nothing here filters rows
on the way out. The one column a query does filter on is the session id a sign-up's rows carry until
it completes — and that is not a soft delete either: the rows are not a past state being hidden,
they are a future one that has not happened yet, and only the queries that could hand one out name
it. [The interactive flow](interactive-flow.md) is where that lives.

**Pagination in the repository.** Page and size arrive as ordinary parameters, and the defaults and
caps are [the API standard's](api-standard.md).

**Read replicas, sharding and any second datasource.** One logical database, one connection pool.

---

← [Design documentation](index.md)
