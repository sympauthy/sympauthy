---
description: The Kotlin an API surface is made of — resources, mappers, controllers, filters and
  error handlers.
paths:
  - "server/src/main/kotlin/com/sympauthy/api/**"
---

# The `api` layer code standard

One of the [code standards](general-code-standard.md). This one covers the Kotlin an API surface is
made of: the resource, the mapper, the controller, and the classes that sit around them. What those
classes turn into on the wire is [the API standard](api-standard.md).

## The resource

**A resource is one surface's request or response, and its suffix says which:**

| Suffix | Is |
| --- | --- |
| `…Resource` | a response, or a fragment reused inside several |
| `…InputResource` | a request body |
| `…ListResource` | a page of a collection, with its own count |
| `…FlowResource` | a flow step's response, which may carry a redirect instead of a body |

**A resource is a serializable data class, documented on the class and on every property.** That
text is the OpenAPI document an integrator reads and the integration tests generate their client
from.

**A wire name that differs from the Kotlin name is written twice, deliberately.** Set the
serialization annotation, which is what the server emits, and the schema annotation's name, which is
what the generated document says.

**Each surface owns its resources.** An administrator's view of a user carries fields the other
surfaces must never see.

**A resource is validated for shape** — required, length, range, pattern. A question that needs
another row belongs to a manager.

## The mapper

**A model becomes a resource in a mapper**, generated the way
[the business mappers](business-layer-code-standard.md#the-mapper) are and registered in the
surface's own `…ApiMapperFactory`. Mapping out treats an unmapped response property as an error,
mapping in an unmapped model property.

**A mapper needing an injected collaborator is written by hand as an ordinary bean.**

**An enum is published through the shared `wireName`, never through a spelling the mapper or the
enum repeats.** [The API standard](api-standard.md#json) fixes the published form at the lowercased
Kotlin name, so it is derivable, and one extension deriving it is the whole implementation of that
rule. A property carrying the same string on each enum is one copy per enum of an answer there is
only one of; `name.lowercase()` written at the mapper is the same copy with no name on it, and
neither is findable from the other when the rule changes.

The exception is where the rule cannot reach: a published name the Kotlin name does not lowercase
into is declared on the enum itself, and that declaration then reads as what it is — this value is
spelled differently on purpose. A declaration on every value says nothing, and the one that matters
is invisible among them.

## The controller

**Controllers route and translate.** Bind the request, call one manager method, map the result,
return it.

**A listing's filters are resolved into domain criteria, and its page is read by a manager.** The
controller binds the parameters, resolves the paging bounds and maps the page it gets back; the
criteria, the order and the slice are the manager's.

**A filter naming one value out of a closed set is resolved by `filterOf`, and a sort direction by
`orderOf`.** A wire word naming no member of that set is refused there, so what reaches the manager
is the domain value or nothing. The two sit over one resolution and name a code apiece: the
description a caller reads says which parameter they got wrong, and an ordering is not a filter.

**Every handler is `suspend`**, for [the reason the general standard
gives](general-code-standard.md#concurrency).

**A route another part of the system names is a constant in the controller's companion.** Write a
route nothing else references inline.

**A route something below the `api` layer builds a URL for declares its constant with the concept**,
and the controller names its route from that constant.

**Security is declared at class level, matching the surface.** Take the roles and scopes from the
constants in `security/`, and put anything narrower than the surface's default in a manager check.

**A parameter is documented on the parameter**, in an annotation on the method argument itself. The
type, whether it is required and its format are inferred from the Kotlin.

## The utility

**A rule the controllers of a surface all apply identically, and that needs an injected
collaborator, is a bean they share.** Name it for what it resolves or refuses.

**A rule stated without the request or the response is a use case**, and belongs to
[a manager](business-layer-code-standard.md#the-manager). Resolving a query parameter, choosing a
status or reading a credential off a header is what earns a utility.

## Filters and error handlers

**Each surface has its own CORS filter**, ordered ahead of everything else, and the framework's own
handling stays off. The flow pages are a configured origin, the admin console another, and the
protocol endpoints are called from anywhere.

**A failure becomes a status in one place.** Normalise every exception into the single localized
HTTP exception first, and render it from there.

**An unrecognised exception is a `500` with a generic body.** Give every exception type that should
mean something else [its own answer](exception-code-standard.md).

## What this standard does not cover

**Content negotiation.** Everything is JSON, except the protocol endpoints the specifications
require to accept form encoding.

**Response caching and conditional requests.** No validator and no cache header, including on the
discovery document and the key set.

**Rate limiting.** Nothing here throttles anything; it is tracked as its own work.

---

← [Design documentation](index.md)
