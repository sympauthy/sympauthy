---
description: What a feature is made of, and what holds across every layer — dependency rules,
  naming, concurrency.
paths:
  - "server/src/main/kotlin/**"
---

# General code standard

[Architecture](architecture.md) says what the layers are and why each owns its own model. This
document says what a feature is made of, and what holds across every layer. What each layer does
with its own components is a standard per layer:

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
| `api` | resource | `…Resource`, `…InputResource` | one surface's JSON contract |
| `api` | mapper | `…ResourceMapper` | model → resource |
| `api` | controller | `…Controller` | routing and status codes, and nothing else |
| `api` | utility | `…Util` | a rule the controllers of a surface apply identically |
| `config` | properties | `…ConfigurationProperties` | the raw YAML, all of it nullable |
| `config` | parser | `…ConfigParser` | text into types |
| `config` | validator | `…ConfigValidator` | whether those values are allowed |
| `config` | factory | `…ConfigFactory` | assembling the validated model |
| `config` | readiness | *none* | whether the configuration as a whole is usable |

**A feature is built from the rows above that it needs.** A component fitting none of them is a
change to this table before it is a class.

## Dependency rules

| From | May import | Never |
| --- | --- | --- |
| `api` | `business`, `config`, `security` | `data` — no entity, no repository |
| `business` | `data`, `config`, `client` | `api` |
| `data` | Micronaut Data and the JDK | `business`, `api` |
| `config` | `business.model`, the JDK | the rest of `business`, `data`, `api` |
| `expression` | `business.model`, the JDK | `business`, `config`, `data`, `api` |

**`business` may import `api` in exactly one case**: a manager implementing an OAuth2 endpoint
throws the protocol's own error type, which lives in `api` because an OAuth2 error code is a wire
format. [The exception standard](exception-code-standard.md) argues that carve-out, and nothing else
uses it.

**`config` may import `business.model`.** A validated configuration is a domain value, and these two
model packages are one layer with two names.

**A validator takes what it needs from elsewhere as a parameter**, resolved by its factory. No
manager, no mapper and no business exception reaches `config`.

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

**A name says what a thing is.** A collection of claims a user consented to is `consentedClaims` and
the same list unfiltered is `allClaims`; where two lists of one type coexist, the filter is in the
name.

## Concurrency

**Everything is `suspend`**, including the operations that await nothing today — a controller
handler, a manager method, a repository query.

**A blocking third-party call is made inside an IO dispatcher.** A library that fetches a key set or
sends mail does its own I/O.

## Rules that compile and then fail

**A class carrying an AOP annotation is `open`, and so is the annotated method.** Kotlin classes are
final by default, the `all-open` plugin is not applied, and the modifier is written by hand.

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
failed flow.

**Caching.** Nothing memoises anything, including the discovery documents and key sets fetched from
third-party providers.

---

← [Design documentation](index.md)
