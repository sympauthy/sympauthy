# The interactive flow

Signing in is not the only thing this server needs a browser for. Enrolling a second factor,
confirming an action a client asked for, re-proving who you are before something sensitive, linking
a third-party identity — and whatever is added next — all need the same things: a page to send a
person to, a way to remember where they were, and a rule for what comes next.

**They are not separate flows. They are separate purposes, sequenced by one engine over one
session.** This document is that engine — the only subsystem here where reading the code in file
order does not explain it.

## The session

`InteractiveFlowSession` is the primitive. It is sealed, and its subtypes are the states a flow can
be in: `OnGoing`, `Completed`, `Failed`, `Cancelled`. It carries only **flow-generic** state — its
id, the ordered list of purposes it exists to satisfy, which of them initiated it, the user once one
is known, whether MFA has been passed, where to redirect on success and on cancel, and when it
expires.

**Concern-specific state is not on the session.** Each purpose that needs to remember something has
its **own record, keyed by the session id**, in its own table, fetched on demand through its own
manager — the OAuth2 request context is one such record, what a confirm was for is another.

The alternative — one session row that grows a column per purpose — fails on the first purpose that
is optional, because every other purpose then carries columns that are null for it and the row stops
saying anything about what a valid session is. It also makes every purpose's state readable by every
other, which is exactly what the split is protecting: a handler cannot accidentally depend on
another purpose's data if it has to ask another manager for it.

**A failed session never reads an attached record.** The failure path needs the session's id and its
flow, and nothing else, so it works even when the record that would explain the failure is the thing
that is missing.

**The session carries a version, and every lifecycle mutation checks it.** Two tabs, a double
submit, or a replayed callback all become one write that succeeds and one that finds the version
moved and routes to the error page, rather than two writes that both look fine.

## Purposes

**`InteractiveFlowPurpose` is the authoritative list, and its KDoc is the authoritative description
of each value.** Read it before adding one. What this document holds is the part the enum cannot
say: the three roles a purpose plays, and how the engine treats each.

| Role | A purpose has this role when | Owns the handoff at the end |
| --- | --- | --- |
| initiating | it is the reason the session was created at all | yes |
| gate | it must be satisfied before another purpose may run | no |
| follow-up | another purpose appends it once it knows it is needed | no |

Authorizing a client is initiating; confirming that the person meant to start this is a gate;
challenging for a second factor is a follow-up. Those are illustrations of the criterion, not the
set — the roles are what a new purpose has to be classified into, and the classification decides
where in the order it goes and whether it may complete the session.

A session's purposes are **ordered**, gates first and the initiating purpose last, and the list is
**appendable** while the session runs.

## The engine

`InteractiveFlowEngine.advance(session)` is the only thing that decides what happens next, and the
only thing that mutates the session. It walks the purposes in order and asks each one, through its
handler, whether it still needs a step:

1. The first purpose whose handler returns a step yields that step, and the walk stops. 2. A purpose
whose handler returns nothing is complete. The engine marks it so and inserts that purpose's
follow-ups **immediately after it** — which is why a gate's follow-up runs *before* the purpose the
gate was protecting. 3. When every purpose has resolved, the engine runs each terminal effect in
order and moves the session to completed or failed.

It returns an `InteractiveFlowStepResult`: the session as it now stands, and the step to send the
person to.

**A step is abstract and carries no URI.** `InteractiveFlowStep` is a sealed set of what has to
happen — sign in, confirm, enrol a factor, collect claims, validate them, authorize with a provider
— and turning one into a URL happens at the API boundary, from the page addresses a deployment
configured. That is what keeps the engine transport-free and testable without a controller.

## The account a sign-up has not finished creating

A flow is many requests, and signing up with a third-party provider leaves the server entirely and
comes back. No database transaction can span that, so the account, its password, its claims, its
provider links and its second factor are written as the person goes — before consent, before claim
validation, before MFA. Someone who walks away halfway leaves a real account behind that passed
none of it.

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

**Identifier uniqueness is settled when an account is promoted, not when it is written.** Nothing in
the schema enforces it — an end-user may sign in with any configured identifier claim, so a value
has to be unique across all of them rather than within one column — and the check at sign-up sees
committed rows only. Two sign-ups may therefore hold one email address at the same time, and neither
blocks the other, which is what stops an abandoned flow squatting an address until the cleaner runs.
The check runs again inside the promotion, and the first flow to complete wins; the second fails
non-recoverably, because at that point every purpose has resolved and no step is left to retry.

**Promotion belongs to the same transaction as the terminal effects and the completion.** That is
what makes "the account exists" and "the flow succeeded" one fact rather than two. It is also why an
invitation is consumed at completion rather than at sign-up: an invitation is spent on an account
that comes to exist, so an abandoned invited sign-up leaves it pending and the invitee's link still
works.

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

## A purpose handler is pure

`InteractiveFlowPurposeHandler` has one required member and two with defaults:

| Member | Answers |
| --- | --- |
| `purpose` | which value of the enum this handler is for |
| `nextStepOrNull` | the step this purpose still needs, or nothing if it is satisfied |
| `followUpPurposes` | purposes to insert after this one |
| `applyTerminalEffect` | the work this purpose does when the flow is about to succeed |

**A handler reads the session and describes what its purpose needs. It never mutates or persists
anything.** Appending purposes, marking one complete, completing or failing the session are the
engine's, exclusively. A handler that writes is a handler whose effects happen even when a purpose
earlier in the list turns out to still need a step.

**`nextStepOrNull` is a pure query, and the endpoint serving that step must mirror it.** A step's
`GET` decides for itself whether it applies, and if that predicate and the handler disagree, the
result is a redirect loop rather than an error — the engine sends the person to a page that
immediately decides it has nothing to do.

**A terminal effect may fail the flow.** It returns either "proceed" or a failure carrying the
exception, which is how a purpose that only discovers a problem at the end — no scope was granted, a
link turned out to conflict — stops the session instead of completing it.

**There is no registration list to edit.** The registry injects every handler bean and indexes them
by purpose, so a new handler is wired up by existing. A purpose with no handler fails at runtime,
and a test asserts that every enum value resolves — so the gap is a build failure, not a production
one.

## Adding a purpose

1. Add the value to the enum, with a KDoc saying what it is for and which role it plays. 2. Write
its handler as a bean: the purpose, `nextStepOrNull`, and whichever of the other two it needs. 3. If
it has state of its own, add a record keyed by the session id, its table in both dialects, and a
manager that reads and writes it. 4. Give it an entry point. There is no generic "start a flow"
endpoint: each purpose is initiated by whatever asks for it, which creates the session with the
ordered purpose list and persists any attached record **in the same transaction**. 5. Test the
handler's branches directly, and add an integration test that drives the flow.

## Adding a step

1. Add it to the sealed step type — an object, or a class when the step is parameterised. 2. Map it
to a redirect, from the page address the flow's configuration names. 3. Serve it as an endpoint
under the flow surface, gated on the session token, going through the controller helper rather than
decoding the state itself. 4. Make its applicability predicate mirror the handler's, or expect a
loop.

## Writing a step endpoint

**Every flow handler goes through the shared controller helper.** It verifies the signed state,
loads the session, runs the work, asks the engine what comes next, turns that into a redirect, and
translates a failure into either a retryable error or a failed session. A controller that decodes
state, loads a session or builds a redirect itself is doing four things the helper already does
identically everywhere else — and doing at least one of them slightly differently.

**A step exposes at most two operations.** A `GET` returns the step's configuration *or* a redirect,
never both; a `POST` applies the action and returns a redirect. When the redirect is present, the
configuration fields are absent, and the client tests for the redirect first.

**A step's response resource describes the UI and nothing else.** No sequencing, no prerequisites,
no purpose, no OAuth2 request data. What the pages are allowed to know is what they have to render,
because everything else is a decision the server has already made and the client must not re-make.

**Every URL in a flow response carries the state.** They are built through the step URI mapper,
never assembled by hand, because a link that loses the state is a page that cannot continue the flow
and whose failure looks like a session expiry.

## What this design does not do

**It does not let a client choose the steps.** A client asks for a purpose; which steps that implies
is the server's, derived from configuration and from what the user has already done.

**It does not throttle anything.** Nothing limits how many times a credential, a validation code or
a second factor may be attempted within a session. This is a known gap with its own work, not a
decision.

**It does not resume across sessions.** An expired session is gone, and its attached records — and
the account a sign-up had not finished creating — are collected by a scheduled job. A person starts
again from the client that sent them.

**It does not model steps that branch on client-supplied data.** Every predicate is a function of
the session and the configuration. A step that needed the client to say which of two paths to take
would be a purpose, not a step.

---

← [Design documentation](index.md)
