---
description: What a client sees — how a route is spelled, what the JSON looks like, and the shape of
  a failure.
paths:
  - "server/src/main/kotlin/com/sympauthy/api/**"
---

# API standard

[The code standards](general-code-standard.md) say what a feature looks like once it is written.
This document says what a client sees: how a route is spelled, what the JSON looks like, and the
shape of a failure. The Kotlin behind it is
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
every mapper reads it from there. Only that value declares one; the rest of the set is spelled by
the rule.

**A value is spelled with dashes in the configuration file and lowercase on the wire.** Two readers,
two conventions, and one enum behind both: converted on the way from a response into a query.

**A UUID is canonical lowercase, and a boolean is a boolean.**

## Collections

**A collection is [the collection standard](collection-standard.md)'s, whole.** The object it
answers with, the page a caller asks for and its bounds, the criteria that narrow it, and the
document a collection publishes about itself are written there.

## Errors

Which exception becomes which status is [the exception standard's](exception-code-standard.md). This
section is the body:

```json
{ "status": 404, "error_code": "user.not_found",
  "description": "This account no longer exists.",
  "details": "No user with id … ." }
```

**`error_code` is the contract.** A client branches on it, `status` repeats the HTTP status so a
logged body still says what it was, and `description` is written for a person and may be reworded in
any release.

**`details` is behind a flag, and the flag is off by default.** It is the technical message and may
name a row, a claim, a provider or a key; what the caller sees never depends on it.

**One surface publishes that message whatever the flag says: the page of a failed interactive flow
session.** The flag keeps the server's internals away from a caller nobody vouched for, and the
reader there is not one — the surface is gated by `admin:interactive-flow-sessions:read`, and a
message that may name a row, a claim, a provider or a key is written for exactly the person holding
it. What earns the exemption is that audience, so a surface an end-user or an unvouched-for client
can reach never gets one.

**`properties` carries per-field validation**, one entry per violated property, each with the path
to it and what is wrong. It is the only place a single response reports more than one failure, and
it is absent rather than empty where the failure refuses no property in particular:

```json
{ "status": 400, "error_code": "flow.claims.invalid",
  "description": "One or more of the values you submitted were refused. Please correct them.",
  "properties": [
    { "path": "birthdate", "error_code": "user.claim_value_validator.invalid_date",
      "description": "Please provide a date formatted as YYYY-MM-DD." },
    { "path": "email", "error_code": "user.claim_value_validator.invalid_type",
      "description": "The value provided is not of the expected type (email)." } ] }
```

**An entry is the error's own triple minus the status** — a code to branch on, a description to
show, and the technical message behind the same flag. The status belongs to the request; a caller
correcting a form needs to know which field carries which failure, not only that one of them did.

**An entry carries no description where the failure names none.** The generic sentence answers for
the response as a whole, and printed against a single field it says less than the path and the code
already do.

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

**A rule a caller needs while writing a request is on the parameter it governs.** An operation's own
description says what the operation is for, and a tag's sits above every operation of the surface at
once, so neither is where a reader looks for what one parameter accepts.

**A client is generated from the published document.** Two annotation processors produce a
specification here and they do not agree; the published one is what an integrator gets.

## Breaking a contract

**A contract is reshaped rather than versioned until a release ships it.** The surface prefix in the
path is not what this turns on — [the database standard](database-standard.md#migrations) names what
a release is, and holds the same rule for the schema underneath.

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
