---
description: How two instances take turns — the lock a transaction holds over an object, the batch
  a run claims, and the lease a scheduled job takes.
paths:
  - "server/src/main/kotlin/com/sympauthy/business/manager/lock/**"
  - "server/src/main/kotlin/com/sympauthy/cron/**"
---

# Locking standard

More than one instance of this server answers for one database, and they share nothing else — no
cluster, no cache, no message bus. Every mechanism here is therefore a row, written in what every
supported dialect spells the same way. The tables are [the database standard](database-standard.md),
the managers holding them are [the `business` layer standard](business-layer-code-standard.md), and
the queries are [the `data` layer standard](data-layer-code-standard.md).

## Choosing the mechanism

| What is being serialised | Take |
| --- | --- |
| a value a single column holds uniquely | a unique index, and no lock |
| a check-then-act over more than one table, or over a value no row holds yet | a named lock |
| a bounded batch of rows a run is about to write | a claim |
| a scheduled job that should run once per tick across the deployment | a lease |

**A uniqueness one column can express is a constraint, not a lock.** Add the unique index and
translate the violation in the manager that writes the row.

**A rule no constraint can express is serialised by a named lock**, taken before the check that
enforces it. A value spanning several columns, and one whose competitor may not have a row yet, are
the cases that arise.

**A lock is taken against another instance, never against another coroutine.** A `Mutex` in this
process is invisible to the process next to it, so it settles nothing this standard is about.

## The named lock

**A lockable object is a subtype of `LockKey`**, and the sealed type's KDoc is the list of what may
be locked. A caller naming a key that is not one of them does not compile.

**A key carries the value the check compares on, normalised the same way.** A lock over a spelling
the check would not have matched excludes nothing.

**A lock is taken through `LockManager.withLock`, and it is held until the enclosing transaction
ends.** The manager joins the caller's transaction, so the caller decides how much of its work the
lock covers.

```kotlin
lockManager.withLock(LockKey.IdentifierValue(email)) {
    checkIdentifierClaimsStillFree(userId)
    collectedClaimRepository.clearSessionId(userId, sessionId)
}
```

**One `withLock` per transaction, naming every object that transaction will touch.** A second call
that is not covered by the first is refused rather than deadlocked.

**A locked block does no I/O beyond the database.** Sending a mail, calling a provider or reading a
key set inside one holds a row for the length of somebody else's outage.

**A lock names a bounded set of keys.** Locking many objects at once serialises against every other
batch, which is what a claim exists to avoid.

**Two writers of one object agree on its key rather than on an order.** One lock taken first by both
replaces the rule that every table is written in the same order, and is what makes a new writer safe
to add.

**The number of stripes the keys hash into is fixed for the life of the schema.** Two instances
running different counts map one key to two rows and exclude nothing, so changing it means stopping
the deployment.

**A lock wait ends in a failure rather than in a stalled connection.** H2 gives up after two seconds
by default and PostgreSQL waits forever, so a deployment sets `lock_timeout` and the standard
assumes a critical section measured in milliseconds.

## Claiming a batch

**A run over a table takes a bounded batch**, ordered by the key and limited to what one run may
write:

```kotlin
@Query(
    """
    SELECT * FROM mail_queue
    WHERE expiration_date IS NULL OR expiration_date > :now
    ORDER BY id
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
    """
)
suspend fun claimUnsent(now: LocalDateTime, limit: Int): List<MailQueueEntity>
```

**A claim and the writes that follow are one transaction.** Outside one, the row lock is released by
the autocommit that took it and the claim means nothing.

**A claim is the one mechanism here that needs more than a row lock.** `SKIP LOCKED` is no part of
the SQL standard, and the floor it sets on a dialect this server supports is PostgreSQL 9.5, H2 2.4,
MySQL 8.0.1 and MariaDB 10.6.

**A run that took fewer rows than it asked for has not failed.** What it skipped is what another
instance is already holding, and the next run collects the remainder.

**`FOR UPDATE SKIP LOCKED` is for a table this run is the only writer of.** Where another writer
touches the same rows, either both sides take the same named lock, or the run re-asserts the
predicate its select selected by in every statement and writes its tables in the other writer's
order.

**A statement writing a claimed row re-asserts the predicate the claim selected by.** An identifier
names a row whatever became of it since it was read.

## The lease

**A scheduled job is a value of `ScheduledJob`, and its lease row ships in the same migration.** A
test holds the enum and the seeded rows equal, on every dialect.

**A leased job is one that stays correct when it runs twice.** A lease expires on a wall clock, so a
run overtaking its own lease runs beside its successor — the lease saves the work, it does not own
it.

**A lease is taken and released around the block, never as a transaction the block runs inside.**
Both statements stand alone and commit on their own; a job's own writes are its own transactions.

**A lease is released by its holder alone.** The release names the holder, so an instance whose
lease already expired and was taken elsewhere ends its run without freeing somebody else's.

**A job whose run may outlast its lease says so in the lease duration.** Give the duration the room
a slow run needs; it is only ever read after the holder has died.

## Tests

**A locking query is proved by a repository test holding a second connection open**, against a real
database of every dialect. A test where both transactions are sequential asserts a property that
held before the lock existed.

**The mapping from a key to its row is pinned by a unit test.** A refactor that changes it silently
splits every deployment mid-upgrade.

## What this standard does not cover

**A lock held across requests.** The interactive flow session serialises its own steps with a
version-guarded update, and [the interactive flow](interactive-flow.md) owns that.

**A work row leased across transactions.** Nothing yet claims a row, commits, and takes minutes to
finish it; a queue whose delivery outlives its transaction would need a lease column of its own.

**Renewing a lease.** A run keeps the lease it took for as long as its duration says, and a
heartbeat that extends one is not written.

**Fairness.** Nothing queues, and an instance that loses a lock or a lease has no claim on the next
one.

**A dialect with no `SKIP LOCKED`.** SQL Server spells it as a table hint and Db2 as a clause of its
own, and supporting either means the claim becomes a query per dialect — which
[the `data` layer standard](data-layer-code-standard.md) does not admit today.

**Retrying after a lock failure.** A timed-out wait surfaces as the failure it is, and the caller
decides nothing about trying again.

**Leader election.** No instance holds a role, and every one of them is free to take any lease.

---

← [Design documentation](index.md)
