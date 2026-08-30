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

They are separate files because they are read separately: a change touches one layer at a time, and
a standard nobody can open without loading the other six is one that gets skimmed.

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

Not every feature needs every row — a read-only endpoint has no input resource, a feature nothing
configures has none of the config ones — and none needs a row that is not here. A component that
does not fit one of these rows is either a component in the wrong layer or a new kind of thing, and
a new kind of thing is a change to this table before it is a class.

## Dependency rules

| From | May import | Never |
| --- | --- | --- |
| `api` | `business`, `config`, `security` | `data` — no entity, no repository |
| `business` | `data`, `config`, `client` | `api` |
| `data` | Micronaut Data and the JDK | `business`, `api` |
| `config` | `business.model`, the JDK | the rest of `business`, `data`, `api` |
| `expression` | `business.model`, the JDK | `business`, `config`, `data`, `api` |

**The two that get broken first, and what they look like when they are:** a resource mapper that
imports an entity because the manager it would otherwise go through returns almost the right shape,
and a manager that reaches into a controller for a route constant because the string is already
spelled there. Both are one import, both work, and both mean the layer boundary is now decorative.

**`business` may import `api` in exactly one case**, and it is not a matter of taste: a manager
implementing an OAuth2 endpoint throws the protocol's own error type, which lives in `api` because
an OAuth2 error code is a wire format rather than a domain concept. Flattening it to a business
exception would lose the code the specification requires. [The exception
standard](exception-code-standard.md) is where that carve-out is argued; nothing else may use it.

**`config` may import `business.model`, and that is an admission rather than a permission.** A
validated configuration *is* a domain value — the factory's output is the model the rest of the
application injects, and the alternative is a second set of types saying the same thing with a
translation between them. The coupling already runs both ways, because a manager reading the
configuration it was given is what the table has always allowed, so a rule forbidding one direction
would be describing half of a relationship. These two model packages are one layer with two names.

**What that does not extend to is the rest of `business`.** No manager, no mapper, no business
exception. The line is load-bearing rather than tidy: a manager reads configuration, so a validator
that injects one closes a loop back through the layer it is in the middle of building, and the
ordering such a loop depends on is not visible from either end of it. What a validator needs from
elsewhere is resolved by its factory and handed to it, which is [the config
standard's](config-layer-code-standard.md) rule for one configuration referring to another, applied
one layer further out.

**An embedded language belongs to neither the layer that checks it nor the layer that runs it.**
A rule a deployment writes as an expression is refused when the configuration is read and evaluated
when a request is served, which is two layers using one grammar. Putting the grammar in either of
them forces the other to import it, so it sits in `expression`, below both.

What that package may not do is grow the half that needs a request: it defines the functions and
turns a string into a boolean or a failure, and it is handed the values to bind. The moment it
reaches for a manager, the layer below has become the layer above.

**A manager is callable without an HTTP request.** It takes no request, no `Authentication` and no
header — a caller that has one extracts what the manager needs and passes it as an ordinary
parameter. This is what keeps a manager usable from a scheduled job, a data-repair script and a unit
test, none of which have a request behind them, and it is what makes authorization something you
pass in rather than an ambient context a test has to fake.

## Naming

**A protocol is spelled the way its specification spells it, in full.** `OAuth2`, never `Oauth2`;
`OpenIdConnect`, never `Oidc` or `OpenId`. These appear in class names, method names and packages,
and the abbreviation is what makes a symbol unfindable: half the codebase greps for one spelling and
half for the other. The YAML key `oidc` is the one exception, kept because it is a user-facing
setting a deployment types by hand and shortness is worth more there than symmetry.

**A method that can answer "there is none" ends in `…OrNull` and returns a nullable type.** Its
non-null twin has the same name without the suffix and delegates to it, throwing when the answer is
null. Two methods rather than one with a flag means the caller states which it wants at the call
site, where a reader can see it, and it means the throwing variant owns the error code so that every
caller reports the same failure.

**A name says what a thing is, not what it is not.** A collection of claims a user consented to is
`consentedClaims`; the same list unfiltered is `allClaims`. Where two lists of the same type coexist
and differ by which filter produced them, the filter is in the name — the alternative is a `claims`
that means something different in each of four managers.

## Concurrency

**Everything is `suspend`, including the operations that await nothing today.** A controller
handler, a manager method, a repository query: all of them. The signature is a contract with the
framework, not a description of the current body — the day a handler grows a manager call, the diff
is one line inside the method rather than a signature change buried in a feature commit, or a
`runBlocking` that parks the very thread this design exists to keep free.

"All of them" is also checkable at a glance, where "the ones that need it" asks every reviewer to
decide, per method, whether a blocking one is deliberate or an oversight.

**A blocking third-party call is wrapped rather than tolerated.** A library that does its own I/O —
fetching a key set, sending mail — is called inside an IO dispatcher, because a blocking call on an
event-loop thread stops serving every other request on it, and nothing about the call site says so.

## Rules that compile and then fail

Two modifiers here are load-bearing rather than stylistic, and both are invisible in review.

**A class carrying an AOP annotation is `open`, and so is the annotated method.** Kotlin classes are
final by default and the annotation is applied by a generated proxy, so a final one silently gets no
proxy — or, on this toolchain, fails the build with a message about advice on a final method. The
`all-open` compiler plugin is not applied, so the modifier is written by hand. It is not decoration,
and it is the modifier a reader is most likely to delete as noise.

**A generated class that is only ever constructed reflectively must be declared to the native
image.** It compiles, it passes every test on the JVM, and it fails at runtime in the artifact that
actually ships. [The native image standard](native-image-standard.md) is where that lives.

## Extension functions

**A helper that reads as a property of something else is an extension function on it**, declared at
the top level of the file that owns the concept — a downcast off an authentication, a config's
`orThrow()`, a repository's criteria query, a logger for a class. The call site then reads as the
thing doing the work rather than as a utility being handed it.

The limit is that an extension is not a hiding place. One that needs an injected collaborator is a
class, not a function with a service locator inside it.

## What this standard does not cover

**Events.** Nothing here publishes or subscribes to one; every effect happens inside the call that
caused it. The first thing that genuinely has to happen after a transaction commits is what should
decide the mechanism.

**Retries and circuit breaking on outbound calls.** A third-party provider being down surfaces as a
failed flow today. What should be retried, and what a half-configured provider should do to
readiness, are a real decision and not a detail.

**Caching.** Nothing memoises anything, including the discovery documents and key sets fetched from
third-party providers. When it does, the layer it belongs to is the interesting question.

---

← [Design documentation](index.md)
