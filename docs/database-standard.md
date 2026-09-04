---
description: The schema every migration builds up, written once per dialect.
paths:
  - "server/src/main/resources/databases/**"
---

# Database standard

Everything durable this server owns lives in one relational schema, built up one
[Flyway](https://documentation.red-gate.com/flyway) migration at a time and written once per
dialect, with [every scenario run against each of them](testing-standard.md). The Kotlin that maps
to it is [the `data` layer standard](data-layer-code-standard.md).

## One schema, spelled per dialect

**A migration exists once per dialect folder, under the same file name.** Add every one of them in
the same change; the configured datasource decides which runs.

**The files describe the same schema and differ only in each dialect's spelling.** Write generating
a UUID, declaring an array, spelling an auto-incrementing integer and quoting a reserved identifier
the way each dialect wants them.

**A constraint one dialect cannot express is written for the dialect that can, and enforced in the
manager that writes the row.** A partial unique index is the case that arises; hold it as a backstop
and a query optimisation.

## Naming

**An identifier is lowercase `snake_case`**, quoted in the file of the dialect that reserves it.

**A table is named in the plural.** A table attached to another by its lifetime is
`<parent>_<concern>`, and carries the parent's key under the parent's name.

**An index is named `<table>__<columns>`.** Name every index and constraint rather than leaving one
to the database.

## Columns

**A primary key is a `uuid`, defaulted by the database.** These identifiers reach URLs and tokens.

**A foreign key is declared where a row belongs to another row, and the referencing column is
indexed.**

**A timestamp column is a plain timestamp holding UTC.** The application forces its own zone to UTC,
which is what makes a zoneless column unambiguous.

**A column is `NOT NULL` unless null means what no value could** — "never revoked", "never used".
Give a column a default where a value is merely inconvenient to supply at insert time.

**Absence is spelled `NULL`.** Every other value a column admits means itself.

## Migrations

**A migration is named `V{major}_{minor}_{patch}_{sequence}__{table}_{new|edit}.sql`.** The version
is the server version the change ships in, and the sequence orders the migrations within it.

**`_new` carries the whole table**, its columns, its constraints and its indexes; `_edit` carries a
change to a table that already exists.

**A migration whose version is unreleased is edited in place** and a development database is wiped
rather than migrated. One file goes on describing its table completely.

**A version is released when it is published as a GitHub release**, and a nightly build is not one.
The version a nightly carries is still open, and its migrations are still edited in place.

**A deployment that follows the nightly recreates its schema rather than migrating it.** An edited
migration no longer matches the checksum the previous nightly recorded, so the next start fails
validation until the schema is dropped.

**A migration that went out in a release stays as it was applied**, and a change to its table ships
as an `_edit` under the version that carries the change.

**One table per file.**

## What this standard does not cover

**Partitioning, sharding and read replicas.** One logical database, one schema.

**Soft-delete as a schema pattern.** A revoked-at column is a domain state with its own meaning, and
nothing filters rows on the way out.

**Data retention.** A scheduled job collects expired sessions; how long a revoked token or a used
validation code is kept is a policy nobody has set.

**Encryption at rest and column-level encryption.** Secrets are hashed where they are secrets, and
what the storage does underneath is the deployment's business.

---

← [Design documentation](index.md)
