# API standard

[The code standards](general-code-standard.md) say what a feature looks like once it is written.
This document says what a **client** sees: how a route is spelled, what the JSON looks like, how a
collection is paged, and the shape of a failure.

It divides cleanly from [the `api` layer standard](api-layer-code-standard.md), which owns the
Kotlin — that a response is a serializable data class, that a controller does four things and no
more. This document owns the bytes those classes turn into. Where the two touch — which exception
becomes which status — it is [settled in the exception standard](exception-code-standard.md) and not
re-argued here.

**An API with no standard is not empty; it already has one.** A naming convention, a null policy, a
date format and an error body arrive from the serialization framework, chosen by nobody. Several of
those are wrong for this server, and at least one is invisible from the Kotlin and only surfaces in
a client.

## Routes

Which prefix belongs to which surface, and why only three carry a version, is
[architecture](architecture.md#surfaces). What is left is everything after the
prefix.

**A path segment is a lowercase plural noun, never a verb.** The verb is the method, and every
cache, proxy and retry policy between a client and here reads the method rather than the path. A
segment needing two words is kebab-case — a path is compared case-sensitively by some intermediaries
and folded by others, and a name with no case in it has none to lose.

**An action that is not a state change on a resource is a sub-path under it**, and a `POST`. Ending
a session, skipping a step, revoking a grant: none of them is the creation or deletion of the thing
in the path, and inventing a resource to make them look like one would be a noun nobody uses.

**A path parameter is camelCase, and this is not an inconsistency with the JSON.** The two are
different kinds of name. A JSON property is a wire name chosen explicitly, and it is snake_case; a
path parameter is a Kotlin argument name the framework binds by identity, so respelling it in the
route means annotating every handler to say the two are the same. The cost of that annotation is
paid on every parameter of every endpoint, forever, and it buys a cosmetic match between a URL and a
body that no client ever compares.

**Nesting stops after one level.** A collection that exists only inside a parent is nested under it;
anything deeper is a filter on the top-level collection instead. Deeper paths are a query wearing a
path, and the same rows wanted grouped another way have to be spelled a second time.

**The protocol endpoints are outside all of this.** Their paths, their verbs, their encodings and
their error bodies are named by the specifications they implement. Where this standard and an RFC
disagree, the RFC wins, and the disagreement is not a bug to be reported.

## Verbs and status codes

| Verb | On | Means | Success |
| --- | --- | --- | --- |
| `GET` | collection or item | read | `200` |
| `POST` | collection | create | `201`, the created resource |
| `POST` | an action sub-path | do something | `200`, or a redirect |
| `PATCH` | item | update, partially | `200`, the updated resource |
| `DELETE` | item | remove | `204`, no body |

**There is no `PUT`.** Every update here is partial: an administrator edits one claim, a client
changes one field. A `PUT` would require a caller to send back every property it did not want to
change, which turns a read into a prerequisite of every write and makes two concurrent edits of
different fields overwrite each other.

**A `DELETE` returns `204` and no body.** A caller that asked for a thing to be gone learns that it
is gone from the status. Returning a confirmation object instead invites a client to read state out
of a response that describes the past, and it makes the endpoint's contract grow a shape that has to
be versioned.

Failures:

| Situation | Status |
| --- | --- |
| malformed, failing validation, or refused by a business rule | `400` |
| no credential, or one that does not validate | `401` |
| a valid credential without the scope for this surface or this row | `403` |
| no such row, or one the caller may not know exists | `404` |
| the server could not complete the operation | `500` |

**`403` and `404` both hide, but they hide different things.** Prefer `404` when the caller may not
know the row exists at all; use `403` when its existence is already public and only the action is
refused. Deciding this per endpoint is the point — a blanket rule leaks either identifiers or
capability.

## Redirects are `303`, never `307`

**Every redirect this server issues is a `303 See Other`.** OAuth 2.1 forbids `307` for the reason
that makes it dangerous here specifically: a `307` preserves the method and the body, so a browser
resubmits the credentials it just posted to whatever the redirect target is. A `303` forces a `GET`,
and the body stops at the endpoint that received it.

This is the rule most likely to be broken by reaching for the framework's default redirect helper,
and nothing about the resulting response looks wrong.

## JSON

**Property names are snake_case, and each one is set explicitly.** The serialization framework's
default is the Kotlin name, so a property that is not annotated ships as camelCase and the body
becomes a mix. The annotation is written even where the two spellings happen to coincide, so that
renaming the Kotlin property cannot silently rename the wire field.

**A null is absent.** Serialization is configured to omit null properties rather than emit them, so
a client tests for the presence of a key, not for its value. This matters most on the flow
resources, where a step returns either its configuration or a redirect and never both: the redirect
being present *is* the signal, and the configuration keys simply are not there.

**A date is ISO-8601 with no zone**, because the type carrying it has none. The server runs in UTC
and every timestamp is UTC, but nothing in the payload says so, so a client that does not know that
will read a local time. This is a real sharp edge rather than a design: it is why the generated
client used by the integration tests has to be told what type to decode into.

Scalars otherwise: a UUID is canonical lowercase, an enum is its name in upper case — the same
string the column stores and the Kotlin declares, so a value can be followed through the whole stack
by grep — and a boolean is a boolean, never a string and never a number.

## Collections

**A collection response is an object, never a bare array.** A top-level array has nowhere to put a
page number and cannot grow one later without breaking every client that indexed into it.

**The items sit under the plural name of what they are**, with the paging beside them rather than
nested in an envelope:

```json
{ "users": [ … ], "page": 0, "size": 20, "total": 413 }
```

Naming the array after the resource rather than `items` means a response reads correctly on its own
and two collections never look identical in a log.

**`page` is 0-based and `size` defaults to 20.** Both arrive as ordinary query parameters and both
are optional. The base matches the data layer underneath rather than being friendlier to read: the
two numbers meet inside a manager, and an off-by-one there is a page of rows silently skipped, where
an unfamiliar base is noticed once per client at integration time.

## Errors

Which exception becomes which status is [the exception standard's](exception-code-standard.md). This
section is the body:

```json
{
  "status": 404,
  "error_code": "user.not_found",
  "description": "This account no longer exists.",
  "details": "No user with id … .",
  "properties": null
}
```

**`error_code` is the contract; `description` is not.** A client branches on the code. The
description is written for a person, is localized, and may be reworded in any release — a client
matching on its text breaks on a typo fix. `status` repeats the HTTP status so that a body which has
been logged or forwarded still says what it was.

**`details` is behind a flag, and the flag is off by default.** It is the technical message, and it
can name a row, a claim, a provider or a key — exactly what is wanted in development and exactly
what should not be served to the internet. What the caller sees never depends on the flag.

**`properties` carries per-field validation**, one entry per violated property, each with the path
to it and what is wrong. It is the only place a single response reports more than one failure.

**An OAuth2 error is a different body, and deliberately so.** The protocol endpoints return the
error shape RFC 6749 defines, snake_case by specification, with the codes the specification names.
Wrapping those in the envelope above would make a conforming client unable to read them.

## CORS

**Each surface has its own allow-list, and none of them is `*` by default.** The flow surface's
origins are the pages a deployment configured; the admin surface's is the console. A wildcard is
available and is a deployment's decision to make, not a default to inherit.

The consequence for a contributor: **a new flow step served from a new origin is a configuration
change as well as a controller.** The endpoint will work in every test and fail in a browser.

## OpenAPI

**The document is generated from the annotations and the KDoc, and it is not committed.** A
checked-in copy is a second source of truth, wrong for the interval between a change and its
regeneration.

**It names no address at build time.** The server's own public URL is deployment configuration, so
the generated document carries a placeholder and the controller serving it substitutes the
configured value on the way out.

**The document a client is generated from is the published one, not the one packaged in the
artifact.** Two annotation processors both produce a specification here, and they do not agree.
Anyone changing an annotation and checking the result has to know which of the two they are looking
at, and the one that matters for integrators is the published one.

## What this standard does not cover

**Caching.** No validator, no cache header, no conditional request — including on the discovery
document and the key set, which are the two things a client fetches most and which change least.

**Rate limiting.** Nothing throttles anything, on any surface, including the endpoints where a wrong
answer can simply be tried again. This is a gap, not a decision.

**Bulk operations and long-running work.** Nothing creates many rows in one request and nothing
takes long enough to need a job resource, so neither the partial-batch question nor the polling
question has been forced.

**Deprecating a version.** How long a version lives once its successor ships, and what announces a
sunset, is a policy — and one invented with no second version in sight is one the first real
migration would have to work around.

---

← [Design documentation](index.md)
