---
description: What a client sees — how a route is spelled, what the JSON looks like, how a collection
  is paged, and the shape of a failure.
paths:
  - "server/src/main/kotlin/com/sympauthy/api/**"
---

# API standard

[The code standards](general-code-standard.md) say what a feature looks like once it is written.
This document says what a client sees: how a route is spelled, what the JSON looks like, how a
collection is paged, and the shape of a failure. The Kotlin behind it is
[the `api` layer standard](api-layer-code-standard.md).

## Routes

Which prefix belongs to which surface, and why only some carry a version, is
[architecture](architecture.md#surfaces). What is left is everything after the prefix.

**A path segment is a lowercase plural noun.** The verb is the method, and a segment needing two
words is kebab-case.

**An action that is not a state change on a resource is a `POST` to a sub-path under it.** Ending a
session, skipping a step and revoking a grant are written that way.

**A path parameter is camelCase**, so the framework binds it by identity. A JSON property is a wire
name chosen explicitly, and it is snake_case.

**Nesting stops after one level.** A collection existing only inside a parent is nested under it,
and anything deeper is a filter on the top-level collection.

**The protocol endpoints follow their specifications.** Their paths, verbs, encodings and error
bodies are named by the RFCs, which win over this document.

## Verbs and status codes

| Verb | On | Means | Success |
| --- | --- | --- | --- |
| `GET` | collection or item | read | `200` |
| `POST` | collection | create | `201`, the created resource |
| `POST` | an action sub-path | do something | `200`, or a redirect |
| `PATCH` | item | update, partially | `200`, the updated resource |
| `DELETE` | item | remove | `204`, no body |

**Every update is a `PATCH` carrying the fields that change.** An administrator edits one claim and
a client changes one field, so two concurrent edits of different fields both survive.

**A `DELETE` answers `204` with no body.** The status is what tells a caller the row is gone.

| Situation | Status |
| --- | --- |
| malformed, failing validation, or refused by a business rule | `400` |
| no credential, or one that does not validate | `401` |
| a valid credential without the scope for this surface or this row | `403` |
| no such row, or one the caller may not know exists | `404` |
| the server could not complete the operation | `500` |

**Choose between `403` and `404` per endpoint.** Answer `404` where the caller may not know the row
exists, and `403` where its existence is already public and only the action is refused.

## Redirects are `303 See Other`

**Every redirect this server issues is a `303`.** OAuth 2.1 forbids `307`, which preserves the
method and the body and would resubmit posted credentials to the redirect target.

## JSON

**A property name is snake_case, and each one is set explicitly.** Write the annotation even where
the two spellings coincide, so renaming the Kotlin property cannot rename the wire field.

**A null is absent.** Serialization omits null properties, so a client tests for the presence of a
key — the flow resources return either a configuration or a redirect, never both.

**A date is ISO-8601 with no zone.** The server runs in UTC and every timestamp is UTC, and a
generated client has to be told which type to decode into.

**An enum reaches a client as a lowercase string**, carried by a property declared as a string that
a mapper fills in. The framework never chooses the spelling.

**A published name the Kotlin name cannot be lowercased into is declared on the enum value**, and
every mapper reads it from there.

**A value is spelled with dashes in the configuration file and lowercase on the wire.** Two readers,
two conventions, and one enum behind both: converted on the way from a response into a query.

**A UUID is canonical lowercase, and a boolean is a boolean.**

## Collections

**A collection response is an object.** The items sit under the plural name of what they are, with
the paging beside them:

```json
{ "users": [ … ], "page": 0, "size": 20, "total": 413 }
```

**`page` is 0-based, and an omitted `size` is the one the deployment configured.** Both arrive as
ordinary query parameters and both are optional.

**A page or a size outside its bounds is a `400` naming the parameter.** A negative page, a size
below one, and a page whose offset overflows the integer the layer below counts rows with are each
refused rather than clamped.

**`size` has a ceiling, and a deployment sets it.** The default size and the ceiling are
configuration, and the shipped values are in the default configuration file.

**The bounds are checked where the two numbers are resolved**, so every paged endpoint answers the
same way and a new collection inherits the answer.

**A paged collection is returned in a total order, and the order is part of the contract.** End the
sort on a key that is unique by construction.

**The order is ascending on a moment the row does not later rewrite**, so a new row appends at the
tail and a client walking pages 0..N is never shifted under.

**An endpoint sorting on a column the row rewrites says so where it is documented.** Two calls still
agree on a snapshot; a walk in progress can skip a row or see it twice.

**The order is named in the endpoint's own description**, which is what an integrator reads.

## Errors

Which exception becomes which status is [the exception standard's](exception-code-standard.md). This
section is the body:

```json
{ "status": 404, "error_code": "user.not_found",
  "description": "This account no longer exists.",
  "details": "No user with id … .", "properties": null }
```

**`error_code` is the contract.** A client branches on it, `status` repeats the HTTP status so a
logged body still says what it was, and `description` is written for a person and may be reworded in
any release.

**`details` is behind a flag, and the flag is off by default.** It is the technical message and may
name a row, a claim, a provider or a key; what the caller sees never depends on it.

**`properties` carries per-field validation**, one entry per violated property, each with the path
to it and what is wrong.

**An OAuth2 error is the body RFC 6749 defines**, snake_case by specification, with the codes the
specification names.

## CORS

**Each surface has its own allow-list**, and none of them is `*` by default: the flow surface's
origins are the pages a deployment configured, the admin surface's is the console. A wildcard is a
deployment's decision.

**A new flow step served from a new origin is a configuration change as well as a controller.**

## OpenAPI

**The document is generated from the annotations and the KDoc, and it is not committed.**

**The document names no address at build time.** It carries a placeholder, and the controller
serving it substitutes the configured public URL on the way out.

**A client is generated from the published document.** Two annotation processors produce a
specification here and they do not agree; the published one is what an integrator gets.

## Breaking a contract

**A contract is reshaped rather than versioned until a release ships it.** The server release is
what this turns on and not the surface prefix in the path;
[the database standard](database-standard.md#migrations) holds the same rule for the schema
underneath.

**A change to a resource the flow configuration is built from ships with a release of
`testcontainers-sympauthy`.** The library parses that response into a model of its own, and the
version the integration tests run against is pinned in `gradle/libs.versions.toml`.

## What this standard does not cover

**Caching.** No validator, no cache header and no conditional request, including on the discovery
document and the key set.

**Rate limiting.** Nothing throttles anything, on any surface.

**Bulk operations and long-running work.** Nothing creates many rows in one request, and nothing
takes long enough to need a job resource.

**Deprecating a version.** [Breaking a contract](#breaking-a-contract) covers one still unreleased,
and how long a released version lives once its successor ships is a policy invented with no second
version in sight.

---

← [Design documentation](index.md)
