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

**A test class is named for its subject and lives in the mirrored package.** A reader looking for a
manager's tests should be able to guess the path.

**A test is named `` `method - case` ``** — the method verbatim, then ` - `, then what the case is:

```kotlin
@Test
fun `findByIdOrNull - Return null when id is null`() { … }
```

Two things fall out of putting the method first. A failure report becomes readable without opening
anything, because it names both what broke and when. And a class's coverage becomes something you
can *scan*: the prefixes gather one method's cases into a block, and a method whose name never
appears has no test at all — much harder to notice when every name starts with a verb.

**A suspending subject is tested inside a coroutine test scope**, with suspending stubs and
verifications. Nothing is unwrapped by blocking.

**Strict stubs are on.** Every test class that assembles its subject from doubles also asks the
framework to fail when a stub is never used. An unused stub is one of two things, and both are worth
a failure: a test that no longer exercises the path it was written for, or a test whose author was
not sure what the subject would call and stubbed everything in reach. The first is coverage that has
quietly gone; the second is a test that would pass against a subject doing something else entirely.

**Nothing resets the framework in its own teardown.** A class that clears the mocks after each test
clears them *before* the check runs, so the check finds no stub left to report and the class passes
whatever it stubbed. It still carries the annotation and still reports green, which makes it the one
failure this rule cannot catch: the enforcement is gone and nothing says so. The extension already
clears between tests, so the teardown buys nothing and costs the check.

**Do not verify a call the assertion already proves.** When a stub's return value or thrown
exception is what the assertion turns on, reaching the assertion is proof the call happened, and a
further verification of the same call asserts nothing. Strict stubs enforce the other half of this
mechanically: the redundant pair is a stub, a verification, and no coverage.

**A verification that a call did *not* happen is different, and it is kept.** No stub covers it, and
it is often the whole point of the test — that a refused caller never reached the write, that a
failure short-circuited before the side effect.

**Test the order as much as the result.** A manager's value is usually the sequence: that validation
ran before the write, that a corrupt row failed before anything acted on it, that the session was
not mutated when the purpose was not satisfied.

**A repository test earns its place on what is not in the Kotlin at all** — that an array column
binds as an array rather than as a string containing one, that a derived update method updates the
row it was meant to. Each of those compiles either way and fails at runtime when it is wrong, so
they are tested against a real database rather than a double.

## Integration tests

They live in their own module, run only when asked for, and boot the server as a container to drive
it over real HTTP. They are the only tests that exercise wiring, security filters, migrations
against PostgreSQL, and the protocol end to end. Running them is
[running locally](running-locally.md).

**Every scenario runs against both databases**, as a parameterized test over the two. A schema or a
query that works on one and not the other is the failure this catches, and it is
[the standing cost](database-standard.md#two-dialects-one-schema) of supporting two.

**One class per feature or per risk, with the happy path and the rejections together.** A feature's
success case and its refusals — bad input, missing scope, a disabled feature, another client's token
— exercise one endpoint, so its behaviour stays in one place as separate test methods. A new class
is for a genuinely distinct feature or a distinct risk, never for one more negative case of an
endpoint already covered.

**A class says what it is protecting, and cites it.** A security test names the RFC section or the
issue it exists for, in its own documentation. A test that asserts a rule without naming the rule is
one a later reader will weaken to make a change pass.

**Drive the server through the generated client, not through hand-written HTTP.** The client is
generated from the server's own published contract, so a test written against it fails to compile
when the contract changes — which is the point. Raw HTTP is for what the typed client cannot
express: a malformed or forged request, an assertion about a redirect or a header, a protocol
endpoint being called wrongly on purpose.

**A rejected call is inspected inside the client block.** The response buffer is released when the
context closes, so reading a status or a body afterwards reads something that is no longer there.

**A green run against a JVM image is necessary and not sufficient.** The image these tests point at
by default is not the one production runs, and none of
[the native-image rules](native-image-standard.md) are exercised by a JVM run. The native run in
continuous integration is the source of truth.

## What this standard does not cover

**Coverage as a number.** Nothing measures it and nothing gates on it. What is worth testing is
decided by the table at the top of this document, not by a percentage.

**Load and performance testing.** No benchmark, no budget, no regression gate. Startup time and
memory are the two things a native image is chosen for, and neither is measured.

**Fuzzing and property-based testing.** The parsers here — tokens, proofs, expressions,
configuration — are the obvious candidates, and none has one.

**Contract testing against real clients.** The generated client proves this server matches its own
document. Nothing proves the document matches what an integrator actually sends.

---

← [Design documentation](index.md)
