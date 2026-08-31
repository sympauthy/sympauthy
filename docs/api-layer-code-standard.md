# The `api` layer code standard

One of the [code standards](general-code-standard.md), which hold the components a feature is made
of and what each layer may import from another. This one covers the Kotlin: the resource, the
mapper, the controller, and the two kinds of class that sit around them. What those classes turn
into on the wire — routes, JSON, status codes, the body of a failure — is
[the API standard](api-standard.md).

## The resource

**A resource is one surface's request or response, and its suffix says which.**

| Suffix | Is |
| --- | --- |
| `…Resource` | a response, or a fragment reused inside several |
| `…InputResource` | a request body |
| `…ListResource` | a page of a collection, with its own count |
| `…FlowResource` | a flow step's response, which may carry a redirect instead of a body |

There is no `…Request`: an input is a resource like any other, and the suffix that matters is the
direction, which `Input` already says.

**A resource is a serializable data class, documented on every property.** The class carries a
description and so does each property, because that text is the OpenAPI document, which is what the
integration tests generate their client from and what an integrator reads. A property with no
description is a field somebody has to guess at.

**A wire name that differs from the Kotlin name is written twice, deliberately.** The serialization
annotation is what the server actually emits; the schema annotation's name is what the generated
document says. They are read by different tools, and setting only one produces a server and a
contract that disagree — which nothing detects, because each is internally consistent.

**Each surface owns its resources.** Surfaces describe overlapping concepts — more than one of them
has a notion of a user — but an administrator's view carries fields the others must never see. One
shared type means either leaking those fields or forking it later under pressure; separate types
mean the leak has to be written on purpose.

**A resource is validated for shape, and only shape.** Required, length, range, pattern — anything
answerable from the request alone. Whether a value refers to something that exists is not: that
needs a query, so it belongs to a manager. The dividing line is whether answering the question
requires touching another row.

## The mapper

**A model becomes a resource in a mapper, not in a controller.** They are generated the same way
[the business mappers](business-layer-code-standard.md#the-mapper) are, registered in the surface's
own `…ApiMapperFactory`, and configured in opposite directions: mapping *out* treats an unmapped
response property as an error, mapping *in* treats an unmapped model property as one. Each policy
points at the side that would silently lose data.

A mapper that needs an injected collaborator to resolve part of its output is written by hand as an
ordinary bean instead of generated. That is a normal outcome, not a failure of the pattern.

## The controller

**Controllers route and translate. They hold no logic.** Bind the request, call one manager method,
map the result, return it. No branching on the result, no second manager call to assemble a
response, no test for which kind of caller this is.

**Every handler is `suspend`**, for [the reason the general standard
gives](general-code-standard.md#concurrency).

**The route is a constant when anything else needs to name it, and a literal otherwise.** An
endpoint another part of the system builds a URL for — the protocol endpoints, the flow steps a
redirect points at — declares its path as a constant in the controller's companion. A route nothing
else references is written inline, where it is read.

**A route something below the `api` layer has to build a URL for declares its constant with the
concept instead.** A configuration assembling the pages of a flow cannot import a controller, so a
constant that stayed in the companion would have to be spelled a second time to be reachable — and
the second spelling is the one that drifts. The controller then names its route from that constant,
so the path is still written once and still findable from the endpoint that serves it.

**Security is declared at class level, matching the surface.** A per-method annotation that *widens*
access is how a route ends up unprotected, so anything narrower than the surface's default belongs
in a check inside the manager instead. The roles and scopes themselves come from the constants in
`security/`, never as string literals — a scope spelled by hand in an annotation is a scope no
compiler will ever compare against the one that grants it.

**A parameter is documented on the parameter.** The description goes in an annotation on the method
argument itself, and the type, whether it is required, and its format are all inferred from the
Kotlin. Documenting it instead in the operation's own parameter list is not a style choice: when the
name there matches a bound argument, the generator keeps the description and **drops the schema**,
emitting a typeless parameter that then fails client generation in the integration tests. The
failure is two build steps away from the annotation that caused it.

## The utility

**A rule the controllers of a surface all apply identically, and that needs an injected
collaborator, is a bean they share rather than a line each of them repeats.** A controller holds no
logic, so the rule cannot live in one; a top-level function cannot hold the collaborator, and one
reaching for it anyway is [a service locator](general-code-standard.md#extension-functions). What is
left is an ordinary singleton, named for what it resolves or refuses.

Stating the rule once is what makes it hold for the endpoints nobody was thinking about: the
arithmetic that overflows, the value one caller in a thousand sends, the endpoint written a year
later. Copied into each controller instead, it is that many places for one of the copies to be the
one that was never updated.

**It is not somewhere to put what a manager should own.** The test is whether stating the rule needs
the request or the response — resolving a query parameter, choosing a status, reading a credential
off a header. Anything answerable without either is a use case, and a use case is
[a manager](business-layer-code-standard.md#the-manager) however convenient this neighbourhood is.

## Filters and error handlers

**CORS is ours, not the framework's.** The framework's own handling is switched off and each surface
has its own filter, ordered ahead of everything else, because the surfaces have genuinely different
answers: the flow pages are a configured origin, the admin console another, and the protocol
endpoints are called from anywhere. One global policy would have to be the loosest of the three.

**A failure becomes a status in one place.** Every exception is normalised into a single localized
HTTP exception first, and rendered from there — so the mapping from a business failure to a status
lives entirely on the `api` side of the boundary, and a manager stays callable from something with
no response to write. What the body looks like is [the API standard](api-standard.md#errors); which
exception means what is [the exception standard](exception-code-standard.md).

**An unrecognised exception is a 500 with a generic body, and that is a decision.** The handler of
last resort does not guess. A business failure reported as an internal one still looks like a
working endpoint from the outside, which is why every exception type that should mean something else
is [given its own answer](exception-code-standard.md) rather than left to fall through.

## What this standard does not cover

**Content negotiation.** Everything is JSON, except the protocol endpoints the specifications
require to accept form encoding.

**Response caching and conditional requests.** No validator, no cache header. The discovery document
and the key set are the first things that would benefit, and both are cheap enough today that the
question has not been forced.

**Rate limiting.** Nothing here throttles anything, including the endpoints where a wrong answer is
retryable. That is a real gap rather than a deliberate one, and it is tracked as its own work.

---

← [Design documentation](index.md)
