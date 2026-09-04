---
description: What each kind of subject is tested with, where the test lives, and what a test is
  expected to prove.
paths:
  - "server/src/test/**"
  - "integration-tests/src/**"
---

# Testing standard

What each kind of subject is tested with, where the test lives, how it is named, and what a test is
expected to prove. The components being tested are [the code standards](general-code-standard.md).

| Subject | Tested with | Where |
| --- | --- | --- |
| a mapper, a parser, a validator | JUnit, no doubles | `server/src/test` |
| a manager | JUnit and MockK doubles | `server/src/test` |
| a flow purpose handler | JUnit and MockK doubles | `server/src/test` |
| a repository, a migration | a real H2, started by the test | `server/src/test` |
| an endpoint, a whole flow, a protocol rule | the server in a container | `integration-tests` |

## Unit tests

**A test class is named for its subject and lives in the mirrored package.**

**A test is named `` `method - case` ``** — the method verbatim, then ` - `, then the case:

```kotlin
@Test
fun `findByIdOrNull - Return null when id is null`() { … }
```

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

**A repository test proves what is not in the Kotlin** — that an array column binds as an array,
that a derived update method updates the row it was meant to. Run it against a real database.

**A repository test deletes the rows it created, and never calls `deleteAll()`.** Every one of them
runs against the same in-memory database, so a wiped table breaks whichever class the runner
schedules next.

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

## What this standard does not cover

**Coverage as a number.** Nothing measures it and nothing gates on it; the table above decides what
is worth testing.

**Load and performance testing.** No benchmark, no budget, no regression gate.

**Fuzzing and property-based testing.** The parsers here are the obvious candidates and none has
one.

**Contract testing against real clients.** The generated client proves this server matches its own
document.

---

← [Design documentation](index.md)
