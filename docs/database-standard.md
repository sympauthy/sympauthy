---
description: The schema every migration builds up, written twice — once per dialect.
paths:
  - "server/src/main/resources/databases/**"
---

# Database standard

Everything durable this server owns lives in one relational schema, built up one
[Flyway](https://documentation.red-gate.com/flyway) migration at a time — and built up **twice**,
once for PostgreSQL and once for H2. This document is the standard those migrations hold to. The
Kotlin that maps to them is [the `data` layer standard](data-layer-code-standard.md).

## Two dialects, one schema

**Every migration exists twice, under the same file name, one file per dialect folder.** Both are on
the Flyway path; the one that runs is decided by the configured datasource. Adding a table means
adding two files, and a migration that exists in only one folder is a schema that silently differs
between what a developer runs and what a deployment runs.

**The two files describe the same schema. Only the dialect's spelling differs.** Generating a UUID,
declaring an array, spelling an auto-incrementing integer, quoting an identifier that one dialect
reserves: these are written the way each dialect wants them, and nothing else is allowed to diverge.
A column that exists in one file and not the other is a bug, not a dialect difference.

**Where one dialect cannot express a constraint, the constraint is written for the dialect that can
— and the application may not rely on it.** A partial unique index is the case that arises here:
PostgreSQL enforces it, H2 has no way to say it, so the rule holds in production and does not hold
in development. Anything the schema is the *only* guard for would therefore be unguarded on H2, so
the rule has to exist above the schema as well, in the manager that writes the row. The index is a
backstop and a query optimisation; it is never the whole argument.

This asymmetry is the standing cost of [supporting two
databases](architecture.md#two-databases-one-schema), and it is the one that bites quietly: a test
suite that only runs on H2 will happily accept rows PostgreSQL would refuse, and the failure
surfaces in production. It is why
[the integration tests](testing-standard.md) run every scenario against both.

## Naming

**Identifiers are lowercase `snake_case`.** An identifier a dialect reserves is quoted the way that
dialect quotes it, in that dialect's file only, and that is the one place the two files differ in
something other than a type.

**Table names are plural.** A table holds many rows, and the plural reads correctly where the name
is actually used.

**A table attached to another one by its lifetime is named for the parent and the concern**, and
carries the parent's key under the parent's name. The records hanging off an interactive flow
session are the family this rule exists for: each is `<parent>_<concern>`, keyed by the session, and
fetched only by the manager that owns that concern. Consistency here is what lets a reader see, from
a table list alone, which rows disappear when a session does.

**Indexes are named `<table>__<columns>`.** A name the database invents is exactly what surfaces in
the error a violated constraint raises and what a later migration has to spell to drop it, and an
invented name tells whoever reads the log to go and look it up.

## Columns

**A primary key is a `uuid`, defaulted by the database.** Not a sequential integer, because these
identifiers are public: they end up in URLs and in tokens, and a sequential key published that way
advertises how many of a thing exist and reduces enumerating all of them to a loop.

**A foreign key is declared where a row belongs to another row.** Referential integrity is the
database's to enforce here rather than the application's, because the alternative — orphan detection
as a periodic job — is work nobody has written and would not notice needing.

**A referencing column is also indexed.** The constraint does not remove the need to join on the
column, and an unindexed join column is a scan per lookup.

**A timestamp column is a plain timestamp, and every value in it is UTC.** The application forces
its own zone to UTC so that a zoneless column is unambiguous, which is a fact about the runtime
rather than about the type — worth knowing precisely because nothing in the schema says it.

**`NOT NULL` is the default, and nullable is the exception that argues for itself.** A column may be
nullable when null means something no value could — "never revoked", "never used" — as distinct from
zero or empty. It may not be nullable merely because a value is inconvenient to supply at insert
time; that is what a default is for.

**Absence is spelled `NULL` and nothing else.** No sentinel dates, no `-1`, no empty string standing
in for unset. A sentinel is a null that every reader has to know about individually.

## Migrations

**A migration is named `V{major}_{minor}_{patch}_{sequence}__{table}_{new|edit}.sql`.** The version
is the server version the change ships in; the sequence orders the migrations within it. `_new`
carries the whole table — its columns, its constraints and its indexes — and `_edit` carries a
change to one that already exists.

**Before 1.0, a `_new` file is edited in place rather than followed by an `_edit`.** No deployment
carries data worth migrating yet, so a table's file continues to describe that table completely, and
a reader gets the current schema from one file instead of reconstructing it from a chain. Flyway
validates by checksum, so this means wiping a development database rather than migrating it — an
acceptable price now, and exactly the wrong one later.

**After 1.0 that reverses**, and it reverses for good: an applied migration is never edited, because
re-running a changed file fails validation on every environment that applied the original. The
switch is a real event and should be recorded as a decision when it happens, not discovered when a
checksum fails.

**One table per file.** A migration that creates two unrelated tables cannot be reviewed, explained
or reverted separately from itself.

## What this standard does not cover

**Partitioning, sharding and read replicas.** One logical database, one schema.

**Soft-delete as a schema pattern.** Some rows carry a revoked-at column, but that is a domain state
with its own meaning, not a deletion the schema hides. Nothing filters rows on the way out.

**Data retention.** Expired sessions are collected by a scheduled job; nothing else is ever removed,
and how long a revoked token or a used validation code should be kept is a policy nobody has needed
to set.

**Encryption at rest and column-level encryption.** Secrets are hashed where they are secrets. What
the storage layer does underneath is the deployment's business.

---

← [Design documentation](index.md)
