---
description: What a feature is made of, and what holds across every layer — dependency rules,
  naming, concurrency.
paths:
  - "server/src/main/kotlin/**"
---

# General code standard

[Architecture](../design/architecture.md) says what the layers are and why each owns its own model.
This document says what a feature is made of, and what holds across every layer. What each layer
does with its own components is a standard per layer:

| Standard | Covers |
| --- | --- |
| [`api` layer](api-layer-code-standard.md) | resources, mappers, controllers, filters |
| [`business` layer](business-layer-code-standard.md) | models, mappers, managers |
| [`data` layer](data-layer-code-standard.md) | entities and repositories |
| [`config` layer](config-layer-code-standard.md) | properties, parsers, validators, factories |
| [Exceptions](exception-code-standard.md) | which exception a layer throws, and what it becomes |
| [Comments](comment-standard.md) | what a KDoc carries, and where the rest of the rationale goes |
| [Testing](testing-standard.md) | what is tested where, and how a test is named |

## What a feature is made of

| Layer | Component | Suffix | Owns |
| --- | --- | --- | --- |
| `data` | entity | `…Entity` | one table's columns, as Kotlin types |
| `data` | repository | `…Repository` | the queries against that table |
| `business` | model | *none* | the domain concept |
| `business` | mapper | `…Mapper` | entity ↔ model |
| `business` | manager | `…Manager` | the use case, and the transaction around it |
| `business` | collaborator | its role — `…Generator`, `…Cleaner`, `…Resolver` | one step of a use case a manager delegates to |
| `api` | resource | `…Resource`, `…InputResource` | one surface's JSON contract |
| `api` | mapper | `…ResourceMapper` | model → resource |
| `api` | controller | `…Controller` | routing and status codes, and nothing else |
| `api` | utility | `…Util` | a rule the controllers of a surface apply identically |
| `api` | filter | `…Filter` | what every request under a path is answered with before it is routed |
| `api` | error handler | `…ExceptionHandler` | a failure turned into a status and a body |
| `config` | properties | `…ConfigurationProperties` | the raw YAML, all of it nullable |
| `config` | parser | `…ConfigParser` | text into types |
| `config` | validator | `…ConfigValidator` | whether those values are allowed |
| `config` | factory | `…ConfigFactory` | assembling the validated model |
| `config` | readiness | *none* | whether the configuration as a whole is usable, and the keys it left unbound |
| `cron` | job | `…Cron` | when a leased job runs, handing the work to a manager |

**A feature is built from the rows above that it needs.** A component fitting none of them is a
change to this table before it is a class.

## Where a package goes

**`business` holds authentication**: the accounts and their claims, the clients and what they were
granted, the tokens, and the flows a person signs in through.

**A subject the server needs that is not authentication is a top-level package of its own**, named
for the subject — `mail`, `queue`. It sits beside `business` rather than inside it, so that
`business` stays the one place a reader looks for what the server is for.

**Such a package is laid out as `business` is**, with a `manager`, a `model`, a `mapper` and an
`exception` package, each where it has one, and contributes the `business` rows of the table above.
[The `business` layer standard](business-layer-code-standard.md) holds its components as it holds
`business`'s.

**`api` and `data` keep their organization.** A subject's routes are controllers in `api` and its
tables are entities and repositories in `data`, so that every surface is gated in one tree and every
schema is one set of migrations.

## Dependency rules

| From | May import | Never |
| --- | --- | --- |
| `api` | `business`, `config`, `security`, `monitoring` | `data` — no entity, no repository |
| `business` | `data`, `config`, `client`, `monitoring` | `api` |
| `data` | Micronaut Data, `monitoring` and the JDK | `business`, `api` |
| `config` | `business.model`, the JDK | the rest of `business`, `data`, `api` |
| `expression` | `business.model`, the Micronaut framework, the JDK | `business`, `config`, `data`, `api` |
| `mail` | `business`, `config`, `client`, `monitoring`, `queue`, `server`, the Micronaut framework, the JDK | `api`, `view` |
| `queue` | `business`, `config`, `data`, `monitoring`, `server`, the Micronaut framework, the JDK | `api`, `view`, `mail` |
| `monitoring` | logback, SLF4J, the R2DBC SPI and r2dbc-proxy, OpenTelemetry, Netty, the Micronaut framework, the JDK | every layer |

**`business` may import `api` in exactly one case**: a manager in `business.manager.auth` or
`business.manager.token` throws the protocol's own error type, which lives in `api` because an
OAuth2 error code is a wire format. [The exception standard](exception-code-standard.md) argues that
carve-out, and nothing else uses it.

**`config` may import `business.model`.** A validated configuration is a domain value, and these two
model packages are one layer with two names.

**An interface a setting selects an implementation from lives in `business.model`.** A setting of
that shape names the interface it selects from, and `business.model` is the only part of `business`
a parser may name; the implementations stay in `business.manager`, where the work they do belongs.
[The `config` standard](config-layer-code-standard.md#a-setting-that-selects-an-implementation) owns
the mechanism.

**A validator takes what it needs from elsewhere as a parameter**, resolved by its factory. No
manager, no mapper and no business exception reaches `config`.

**`business` never imports `mail`.** A manager reaches a mail through an interface `business`
declares and `mail` implements, as `ValidationCodeMediaSender` is, so a use case says that a message
goes out without depending on how it is composed, drawn or delivered.

**`queue` imports `server` for the scope its delivery runs in.** What it attempts in the background
has to stop when the server does, and `BackgroundScope` is the one scope the server cancels then.

**`queue` never imports the package of a destination it delivers to.** That package implements
`QueueItemDeliverer`, which the queue is handed by injection, so an item's payload is read only by
the package that wrote it.

**`mail` may import `view` in exactly one case**: the path of a route a mail links to, so that the
address a mail carries and the route answering it are one constant.

**An embedded language lives in `expression`, below the layer that checks it and the layer that runs
it.** It defines the functions, turns a string into a boolean or a failure, and is handed the values
to bind.

**A manager is callable without an HTTP request.** A caller holding a request extracts what the
manager needs and passes it as an ordinary parameter, which keeps the manager usable from a
scheduled job, a repair script and a unit test.

## Naming

**A protocol is spelled the way its specification spells it, in full.** `OAuth2`, `OpenIdConnect` —
in class names, method names and packages. The YAML key `oidc` is the one exception, kept because a
deployment types it by hand.

**A method that can answer "there is none" ends in `…OrNull` and returns a nullable type.** Its
non-null twin has the same name without the suffix, delegates to it, and owns the error code.

**A missing value is passed as `null`, never as a substituted empty one.** Push the nullability into
the signature so the callee decides what absence means; `?: ""` turns a missing secret into an empty
one that may match.

**The values interpolated into a message are the exception.** That map may not admit a null — a
nullable one binds a different overload and drops every named value — so an absent value goes in as
an empty string where the exception is built.

**A name says what a thing is.** A collection of claims a person consented to is `consentedClaims`
and the same list unfiltered is `allClaims`; where two lists of one type coexist, the filter is in
the name.

## Concurrency

**Everything is `suspend`**, including the operations that await nothing today — a controller
handler, a manager method, a repository query. A query streaming its rows answers a `Flow` instead,
which is the same promise for a stream.

**A coroutine a bridge to a reactive type starts for a request is handed
`traceCoroutineContext()`.** `publish`, `rxSingle`, `asPublisher` and their kin start one with
nothing in it, even inside a `suspend` function, so what it queries opens no span and what it logs
carries no trace. Hand it the trace alone, never the whole propagated context, which also holds the
caller's connection and transaction for the coroutine to join and then outlive.

**A blocking third-party call is made inside an IO dispatcher.** A library that fetches a key set or
sends mail does its own I/O.

**Coordination between instances is a row, not a monitor.** A `Mutex` or an `AtomicInteger` holds
inside one process and settles nothing against the next one; [the locking
standard](locking-standard.md) holds the mechanisms that do.

## Rules that compile and then fail

**A class carrying an AOP annotation is `open`, and so is the annotated method.** Kotlin classes are
final by default, and the modifier is written by hand.

**A generated class that is only ever constructed reflectively is declared to the native image.**
[The native image standard](native-image-standard.md) is where that lives.

## Extension functions

**A helper that reads as a property of something else is an extension function on it**, declared at
the top level of the file that owns the concept — a downcast off an authentication, a config's
`orThrow()`, a repository's criteria query.

**A helper that needs an injected collaborator is a class.**

## What this standard does not cover

**Events.** Nothing publishes or subscribes to one, and every effect happens inside the call that
caused it.

**Retries and circuit breaking on outbound calls.** A third-party provider being down surfaces as a
failed flow, which the person in it can retry. What is fetched once at startup and nothing else
would ever resolve — a provider's discovery document, a brand image — is tried again until it
answers, as [logging](../design/logging.md#what-implements-it) describes. A mail is retried, because
it is a promise to somebody who is not waiting on the call and cannot retry it themselves — how is
[delivery](../design/delivery.md)'s. Circuit breaking stays open for every call, a delivery's
included.

**Caching.** What the configuration determines, or the server fetched once at startup, is held until
a restart. Nothing a request fetches from a third party is memoised, a provider's key set included.

**Size thresholds.** No length is set for a function or a class, and no rule says which literal
deserves a name; the detekt rules that would hold either stay off until one is argued for here.

---

← [How the code is written](index.md)
