---
description: What each kind of subject is tested with, where the test lives, what a test is
  expected to prove, and how little of that a comment has left to say.
paths:
  - "server/src/test/**"
  - "integration-tests/src/**"
---

# Testing standard

What each kind of subject is tested with, where the test lives, how it is named, what a test is
expected to prove, and how little of that a comment has left to say. The components being tested are
[the code standards](general-code-standard.md).

| Subject | Tested with | Where |
| --- | --- | --- |
| a mapper, a parser, a validator | JUnit, no doubles | `server/src/test` |
| a manager | JUnit and MockK doubles | `server/src/test` |
| a flow purpose handler | JUnit and MockK doubles | `server/src/test` |
| a repository, a migration | a real database of each dialect, started by the test | `server/src/test` |
| a rule holding two files to each other | JUnit, reading both | `server/src/test` |
| an endpoint, a whole flow, a protocol rule | the server in a container | `integration-tests` |

## Unit tests

**A test class is named for its subject and lives in the mirrored package.**

**A test is named `` `method - case` ``** — the method verbatim, then ` - `, then the case:

```kotlin
@Test
fun `findByIdOrNull - Return null when id is null`() { … }
```

**A test with no method to name is named for the invariant it holds** — `` `Every code named in the
sources has a message in the bundle` ``. The subject of a rule holding two files to each other is
the pair, not a call, so there is nothing to put before the dash.

**A suspending subject is tested inside a coroutine test scope**, with suspending stubs and
verifications.

**Strict stubs are on for the module**, in the JUnit platform configuration, and a class using
doubles runs under the MockK extension.

**The extension clears the mocks between tests.** Leave the teardown to it so the unnecessary-stub
check still has something to report.

**A test verifies only what its assertions cannot prove.** Where a stub's return value or thrown
exception is what an assertion turns on, reaching the assertion is proof the call happened.

**A verification that a call did not happen is kept.** It is often the point of the test — that a
refused caller never reached the write, that a failure short-circuited before the side effect.

**Test the order as much as the result.** Prove that validation ran before the write, that a corrupt
row failed before anything acted on it, that a session was left untouched when the purpose was not
satisfied.

**A bundle message is not rendered in a test.** Assert the code and the values at the throw site; a
bundle is content, edited for editorial reasons, so a test reading one fails on a rewrite that broke
nothing.

**A bundle's keys are its contract rather than its content, and they are checked.**
[The exception standard](exception-code-standard.md#a-code-names-two-messages) holds the error
bundle's set of keys equal to the codes the sources name. Rewording a message leaves that set
untouched, which is what makes this rule and the one above compatible.

**A repository test proves what is not in the Kotlin** — that an array column binds as an array,
that a derived update method updates the row it was meant to. Prove its queries and its entity's
mapping; an index and a constraint are the database keeping its own promise.

**A repository test runs against every dialect**, as a parameterized test over `Database`. The
spellings that differ are the ones a repository touches, so a query is proved against the dialects
it ran on and no others.

**A repository test deletes the rows it created, and never calls `deleteAll()`.** Each dialect's
database is shared by every class in the run, so a wiped table breaks whichever class the runner
schedules next.

**A repository test seeds inside the test**, through `withFixture`, because `@BeforeEach` cannot see
which database the parameter named. Register a deletion as the row is created and the fixture
unwinds them in order.

**A test that wants a datasource without naming a dialect asks for the `h2` environment.** The
`test` environment carries no `r2dbc.datasources.default`, because PostgreSQL's url holds a mapped
port and arrives programmatically; a context started without either has no repository bean at all.

**A key a test queries by names the test class**, so no other class's rows fall inside the query
under test.

## Integration tests

They live in their own module, run only when asked for, and boot the server as a container to drive
it over real HTTP. Running them is [running locally](running-locally.md).

**Every scenario runs against every database**, as a parameterized test over them.

**One class per feature or per risk, with the happy path and the rejections together.** Add a new
class for a genuinely distinct feature or risk, and a new method for one more rejection of an
endpoint already covered.

**A class says what it is protecting, and cites it.** Name the RFC section or the issue in its own
documentation.

**Drive the server through the generated client.** Keep raw HTTP for what the typed client cannot
express — a malformed or forged request, an assertion about a redirect or a header.

**A rejected call is inspected inside the client block.** The response buffer is released when the
context closes.

**A green run against a JVM image is necessary and not sufficient.** The native run in continuous
integration is the source of truth for [the native-image rules](native-image-standard.md).

## Comments

**Silence is the standard.** [The comment standard](comment-standard.md) governs a test as it
governs everything else, and a test following the conventions above leaves it almost nothing to
carry: the name says the method and the case, the fixture says the setup, the assertion message says
the expectation. A test reading as though it needs explaining is usually one that needs splitting.

**A comment that repeats the test's own name is not written.** Restating the method, the case, or
the expectation the assertion message already states writes the sentence twice — and the copy
nothing runs is the one that drifts.

**A test file carries no section headers.** `// --- confirmEnrollment ---` over a run of tests each
named `` `confirmEnrollment - …` `` spells the method once more per group, and drifts on its own.
`@Test` is what marks a test, so a header separating the tests from the helpers around them says
nothing either. A file holding no test of its own — a shared base class — may still group its
helpers by family.

**An integration test narrates its scenario once, in the class documentation.** Repeating the steps
beside the calls writes the sequence three times — the documentation, the comment, the assertion
message — and only the failing one is checked.

**A rule these documents state is not restated in a test file.** That every repository test shares
one database per dialect, and that `deleteAll()` is forbidden because of it, is a rule of this
document; a file repeating it is one more place to edit the day the rule changes.

**A comment a test keeps carries what neither the name nor the code shows.** That is a stub left
out on purpose, where reaching the assertion is itself proof the call was never made; a fixture
value the assertion turns on that never appears; a departure a reader would take for a mistake.

## What this standard does not cover

**Coverage as a number.** Nothing measures it and nothing gates on it; the table above decides what
is worth testing.

**A dialect a test does not name.** `Database` holds the two the server supports, and adding a third
is a change to it before it is a change to any test.

**Load and performance testing.** No benchmark, no budget, no regression gate.

**Fuzzing and property-based testing.** The parsers here are the obvious candidates and none has
one.

**Contract testing against real clients.** The generated client proves this server matches its own
document.

---

← [Design documentation](index.md)
