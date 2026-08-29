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

**It does not resume across sessions.** An expired session is gone, and its attached records are
collected by a scheduled job. A person starts again from the client that sent them.

**It does not model steps that branch on client-supplied data.** Every predicate is a function of
the session and the configuration. A step that needed the client to say which of two paths to take
would be a purpose, not a step.

---

← [Design documentation](index.md)
