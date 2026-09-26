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

**A predicate names the state of a column and never a value a deployment chooses.** These files
cannot read the configuration, so a value enumerated in one is a guess — right for the deployments
that happen to match it and silently wrong for the rest. Narrow a partial index by
`session_id IS NULL` or by `revoked_at IS NULL`, and take an index broader than the rows a read
looks at over one that depends on a YAML file.

## Naming

**An identifier is lowercase `snake_case`**, quoted in the file of the dialect that reserves it.

**A table is named in the plural.** A table attached to another by its lifetime is
`<parent>_<concern>`, and carries the parent's key under the parent's name.

**An index is named `<table>__<columns>`**, the columns joined with a single `_` so that `__` marks
the table off from them. Name every index and constraint rather than leaving one to the database.

**A conditional index is suffixed `__where_<condition>`**, summarising the predicate in a few words
rather than spelling it. Only the dialect that carries the condition carries the suffix, so the twin
an unconditional dialect writes stays `<table>__<columns>`.

```sql
CREATE UNIQUE INDEX consents__user_id_audience_id__where_not_revoked
    ON consents (user_id, audience_id) WHERE revoked_at IS NULL;
```

**An identifier is at most 63 bytes**, which is PostgreSQL's ceiling: a longer one is truncated
there under a notice nothing fails on and kept whole by H2, so one file would build two
differently-named indexes. **Where a name does not fit, abbreviate the table to the first letter of
each of its words** and keep every column spelled in full.

```sql
CREATE UNIQUE INDEX ifssc__session_id_fingerprint
    ON interactive_flow_session_security_context (session_id, fingerprint);
```

## Columns

**A business object's primary key is a `uuid`, defaulted by the database.** These identifiers reach
URLs and tokens.

**A foreign key is declared where a row belongs to another row, and the referencing column is
indexed.**

**A timestamp column is a plain timestamp holding UTC.** The application forces its own zone to UTC,
which is what makes a zoneless column unambiguous.

**A column is `NOT NULL` unless null means what no value could** — "never revoked", "never used".
Give a column a default where a value is merely inconvenient to supply at insert time.

**Absence is spelled `NULL`.** Every other value a column admits means itself.

**A `bytea` column is `NOT NULL`.** A null one cannot be written — the R2DBC binding types it
`smallint[]`, which PostgreSQL refuses against a `bytea` — so give the absent value a spelling of
its own and translate it in the mapper. [The design FAQ](design-faq.md) holds the case that settled
it.

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
nothing filters rows on the way out. The nullable session id the tables a sign-up writes carry is
the one exception, and it is not a soft delete: it says the row is not real yet rather than no
longer. [The provisional user](provisional-user.md) owns it.

**Data retention, except where a table holds personal data.** A scheduled job collects expired
sessions and the accounts an abandoned sign-up left half-written, and the places a person signs in
from carry a retention of their own because an address is personal data — [the security
context](security-context.md) holds it. How long a revoked token or a used validation code is kept
is still a policy nobody has set.

**Encryption at rest and column-level encryption.** Secrets are hashed where they are secrets, and
what the storage does underneath is the deployment's business.

---

← [Design documentation](index.md)
