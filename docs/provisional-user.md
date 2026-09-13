# The provisional user

A flow is many requests, and signing up with a third-party provider leaves the server entirely and
comes back. No database transaction can span that, so the account, its password, its claims, its
provider links and its second factor are written as the person goes — before consent, before claim
validation, before MFA. Someone who walks away halfway leaves a real account behind that passed
none of it.

This document is what holds that back: how a row a sign-up writes is kept from counting, what
makes it count, and what collects it when nothing ever does. The session the work hangs off is
[the interactive flow](interactive-flow.md), and the engine completing one is what promotes
whatever it signed up.

## A row that does not count yet

**A row a sign-up writes carries the id of the session writing it, and does not count until the
session completes.** The five tables an account is made of carry a nullable `session_id`; the
interface `SessionScoped` and its KDoc are the authority on what the column means. On success the
column is cleared — one transaction, every table — and on abandonment the rows are collected with
the session. It is a long-running transaction emulated with a tombstone and compensating cleanup,
which is what one does when a real transaction cannot span the work.

**A row is provisional exactly when its user is.** Every row an account owns takes its session id
from the account rather than from the session the request happens to be serving, so a committed
account cannot grow a provisional row and a provisional account cannot grow a committed one. Nothing
at a call site decides it.

**Only a query that reaches an account without holding its id excludes the provisional ones.** That
invariant is what makes the rest of the reads safe as they are: a read keyed by a user id is already
exactly as visible as its user. The queries that do filter are the ones that could hand a caller an
account the server has not finished creating — the user listings, the identifier-claim lookups, the
provider-subject lookup, and the readers taking an id from outside. The flow reads its own account
through the session manager, the one reader entitled to a provisional one.

## Becoming an account

**Identifier uniqueness is settled when an account is promoted, not when it is written.** Nothing in
the schema enforces it — an end-user may sign in with any configured identifier claim, so a value
has to be unique across all of them rather than within one column — and the check at sign-up sees
committed rows only. Two sign-ups may therefore hold one email address at the same time, and neither
blocks the other, which is what stops an abandoned flow squatting an address until the cleaner runs.
The check runs again inside the promotion, and the first flow to complete wins; the second fails
non-recoverably, because at that point every purpose has resolved and no step is left to retry.

**What makes the first of them the only one is a lock over the values themselves.** The promotion
names every identifier value and every provider subject it is about to make committed as a
[named lock](locking-standard.md), holds them across its re-check and its writes, and so answers
against what the winner committed rather than against the rows it read before the winner existed.
Both halves are serialised on every dialect; the unique index PostgreSQL carries over a provider
subject is a backstop behind the lock rather than the rule, since H2 spells no partial index and a
partial index is what two provisional links sharing a subject requires.

**A provider subject has two more writers, and they take the same key.** Linking a provider to an
account and merging one into an existing account each commit a link after their own committed-only
check, and in that race neither holds a row the other could have waited on. The loser of either is
answered where it can still act on it: the link flow fails the way it already does for a subject
another account holds, and the merge is *recoverable*, because going through the provider again
finds the link that now exists and signs the person in.

**Promotion is the first thing the completion transaction does, and its locks outlive it.** They are
held until that transaction commits, terminal effects included, so a terminal effect doing I/O of
its own is everybody's problem: the OAuth2 effect calls the client's authorization webhook where one
is configured, and a flow whose identity hashes to the same stripe waits however long that client
takes to answer. That is the server breaking
[the rule against holding a lock across I/O](locking-standard.md), not an exception to it — and
moving that call out is not a reordering, because the effects write consents and a consumed
invitation against an account the promotion is what makes real.

**An invitation is consumed at completion rather than at sign-up**, for the same reason the
promotion is there. An invitation is spent on an account that comes to exist, so an abandoned
invited sign-up leaves it pending and the invitee's link still works.

## Collecting one that never will

**Collecting an abandoned account is a cleaner of its own, not a step of the one expiring the
sessions.** It keys on the session being *gone* rather than on the sessions any one run expired, so
it needs nothing from the run that removed them and has a cron of its own. Two things follow from
keeping them apart: it reads an absence every other transaction can see rather than one only its own
has written, and it never holds a session's lock while waiting for an account's — a completing flow
takes those two in the opposite order, and together they would deadlock.

**Its deletes carry the predicate its select selected by.** A flow may promote one of the accounts
between the read that listed it and the deletes that collect it, and an id names a row whatever
became of it since. Each of the five statements names the session id instead, so a database that
blocked on the promotion re-checks the account as the promotion left it and skips a promoted one.
The guarantee on the other side — that a flow whose session the cleaner expired cannot complete —
is the flow's, and the sweep does not lean on it.

**A table that references `users` is classified when it is added.** Collecting an abandoned account
means deleting it, and a foreign key that delete breaks would abort the whole sweep — again every
quarter of an hour, indefinitely. Each such table is either owned by the account and deleted with
it, or named in the guard that skips an account something still refers to. That guard is the query
the collection selects by, and it is where the rule is written.

---

← [Design documentation](index.md)
