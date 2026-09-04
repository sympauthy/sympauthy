---
description: Which exception each layer throws, what a failure carries instead of a sentence, and
  how an error code names its two messages.
paths:
  - "server/src/main/kotlin/com/sympauthy/exception/**"
  - "server/src/main/resources/error_messages*.properties"
---

# Exception code standard

How a failure travels from the layer that notices it to what a caller finally sees: which exception
each layer may throw, what a failure carries instead of a rendered sentence, and how a code names
both the message an operator reads and the one an end-user does. The components doing the throwing
are [the code standards](general-code-standard.md); the **body** the caller receives is
[the API standard](api-standard.md#errors).

## One root, three layers

**Every failure in this server extends one localized exception type**, which lives above all three
layers because two of them throw. It carries no message. It carries a code, an optional second code
for the end-user's version, a map of values to interpolate, and whether the situation is one the
caller could recover from.

| Layer | Throws | Meaning |
| --- | --- | --- |
| `business` | `BusinessException` | a rule refused, or the operation could not complete |
| `api` | `LocalizedHttpException` | the request itself is wrong, or the resource is not there |
| `api` | `OAuth2Exception` | a protocol error, whose code the specification names |
| `config` | `ConfigurationException` | a value in the deployment's YAML is wrong |

The root type is never thrown directly. It exists to be extended and to be rendered.

## An exception carries keys, never a sentence

**A failure holds a code and values; rendering happens once, at the edge, against the caller's
locale.** That is what keeps `business` free of both the request and the message source — a manager
has to stay callable from a scheduled job that has no `Accept-Language` behind it — and it is what
makes the same failure serve an English operator log and a French error page without either being
translated twice.

The consequence worth stating: **a value that belongs in the message is a value, not string
concatenation.** A code with the offending scope interpolated is one code an alert can group on; the
same message built by hand is as many distinct strings as there are scopes.

## Three business failures, and the question that tells them apart

| Factory | Recoverable | Becomes | For |
| --- | --- | --- | --- |
| `recoverableBusinessExceptionOf` | yes | `400` | the caller can change something and retry |
| `businessExceptionOf` | no | `400` | the request was wrong, and retrying it will not help |
| `internalBusinessExceptionOf` | no | `500` | the server failed, and the caller is not at fault |

**The question is asked from the caller's side: can they send something else?** A wrong password, a
claim that fails validation, a scope the client may not ask for — the caller is entitled to try
again with different input, and the message they get should tell them how. That is the recoverable
one, and it is the only one that takes a second code for the end-user's message, because it is the
only one where an end-user has anything to do.

**The third exists because "not the caller's fault" is a different operational problem.** A key that
will not load, a row that cannot become a model, a provider that answered something unparseable:
nothing the caller sent would have helped. Returning `400` for those blames the caller for a bug
they cannot fix and — worse — keeps a monitored error rate flat while the server quietly rots.

Three factories rather than one with two flags, because a flag has a default and the default would
be whichever case was written first. Three names make the question unskippable at the throw site,
which is the only place it can be answered.

## The OAuth2 carve-out

**A manager implementing an OAuth2 endpoint throws the protocol's exception directly.** This is the
one place `business` is allowed to import `api`, and it is not a convenience.

The OAuth2 and token-exchange specifications name the error a client receives — `invalid_grant`,
`invalid_request`, `invalid_target`, `access_denied` — and a client library branches on exactly
those strings. Routing them through a business exception would flatten every one of them into a
single code at the boundary, and the endpoint would stop being conformant: the client would have to
read a human-readable description to find out what happened, which is precisely what the
specification exists to avoid.

It applies to the OAuth2 managers and nothing else. Anywhere else, a protocol code is not the
contract and the three factories above are.

## A code names two messages

**An error code is a message-bundle key, and the key is the contract.** The code names the technical
message an operator reads; the same code prefixed with `description.` names the one shown to an
end-user. There is one string, not two files kept in step, which means a code cannot exist without a
message — and it means renaming a key is a **breaking change**, because the key is also what a
client branches on.

**The technical message is for troubleshooting; the description is for a person who cannot fix it.**
The first may name a scope, a claim, a provider or an algorithm. The second says what the reader
should do next, in their own words, and never names anything internal.

**A code reads `<domain>.<thing>.<condition>`**, most general first, so that a sorted bundle groups
by what failed rather than by how. A code with no domain is one nothing specific was thrown for —
the per-status generics — and that absence is itself a signal: a scoped code means a rule of ours
refused something, a bare one means the framework did.

**The condition never names a status.** The status is already in the response and in the handler
table; a code that repeats it says the same thing twice and makes the status impossible to change
without a rename that breaks clients.

**A `5xx` says nothing to the caller.** The technical half of a failure — the row, the key, the
provider, the step — belongs in the log and behind the flag that gates it. A caller learns that the
server failed, because there is nothing else they could act on.

## The apostrophe rule

**Never write `'` immediately before `{` in a message.** The message source treats a quoted brace
expression as a literal, so `'{scope}'` is emitted verbatim, interpolation silently does not happen,
and the reader is shown the placeholder instead of the value. Write the placeholder bare.

This is the rule in this document most likely to be broken by someone trying to make a message read
better, and the failure is invisible in review: the code compiles, the test that asserts the code
passes, and only the rendered message is wrong.

## What this standard does not cover

**Retryability as a wire signal.** Whether a caller should retry is expressed today only as the
choice of status. No header says how long to wait, and nothing distinguishes "retry this exact
request" from "change something first" beyond the description.

**Error aggregation.** One request reports one failure, except for the per-property validation
errors the API standard describes. A partial success that needs to report several is a shape nothing
here has needed.

---

← [Design documentation](index.md)
