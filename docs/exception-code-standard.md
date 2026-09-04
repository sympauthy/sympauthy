---
description: Which exception each layer throws, what a failure carries instead of a sentence, and
  how an error code names its two messages.
paths:
  - "server/src/main/kotlin/com/sympauthy/exception/**"
  - "server/src/main/resources/error_messages*.properties"
---

# Exception code standard

How a failure travels from the layer that notices it to what a caller finally sees. The components
doing the throwing are [the code standards](general-code-standard.md); the body the caller receives
is [the API standard](api-standard.md#errors).

## One root, a type per layer

**Every failure extends one localized exception type**, which carries a code, an optional second
code for the end-user's version, a map of values to interpolate, and whether the caller could
recover. The root type is extended and rendered, never thrown.

| Layer | Throws | Meaning |
| --- | --- | --- |
| `business` | `BusinessException` | a rule refused, or the operation could not complete |
| `api` | `LocalizedHttpException` | the request itself is wrong, or the resource is not there |
| `api` | `OAuth2Exception` | a protocol error, whose code the specification names |
| `config` | `ConfigurationException` | a value in the deployment's YAML is wrong |

**A layer's type is subclassed where a caller has to catch one of its failures and not the others.**
`InvalidJwtException` is the three ways a token fails to decode, which a token endpoint answers with
`invalid_grant`, split from the key that will not load and travels out of the same call as a `500`.
The alternative is a list of codes at every catch: the contract restated once per caller, and wide
by default the day one caller stops restating it.

## An exception carries keys, never a sentence

**A failure holds a code and its values, and rendering happens once, at the edge**, against the
caller's locale. A manager stays callable from a scheduled job with no `Accept-Language` behind it.

**A value that belongs in the message is passed as a value.** One code an alert can group on carries
the offending scope as data.

## The question that tells the business failures apart

| Factory | Recoverable | Becomes | For |
| --- | --- | --- | --- |
| `recoverableBusinessExceptionOf` | yes | `400` | the caller can change something and retry |
| `businessExceptionOf` | no | `400` | the request was wrong, and retrying it will not help |
| `internalBusinessExceptionOf` | no | `500` | the server failed, and the caller is not at fault |

**Ask the question from the caller's side: can they send something else?** A wrong password, a claim
that fails validation and a scope the client may not ask for are recoverable, and recoverable is the
only one that takes a second code for the end-user's message.

**A failure nothing the caller sent would have prevented is internal.** A key that will not load, a
row that cannot become a model and a provider that answered something unparseable are `500`.

## The OAuth2 carve-out

**A manager implementing an OAuth2 endpoint throws the protocol's exception directly**, so that the
code the specification names — `invalid_grant`, `invalid_request`, `invalid_target` — reaches the
client. This is the one place `business` imports `api`.

**It applies to the OAuth2 managers and nothing else.** Everywhere else, the factories above are
the contract.

## A code names two messages

**An error code is a message-bundle key.** The code names the technical message an operator reads,
and the same code prefixed with `description.` names the one an end-user is shown.

**A code that is thrown has its technical message in the bundle, and a recoverable one has its
`description.` message too.** A description the bundle does not hold renders as null and is
[dropped from the body](api-standard.md#json), so the caller is told nothing at all.

**The bundle holds a message for every code and no message besides**, and
`ErrorMessageBundleTest` [holds the two sets equal](testing-standard.md#unit-tests). Neither half
degrades visibly on its own: a missing message is dropped from the body, which reads exactly like
the flag that gates it being off, and a message nothing throws is invisible by construction.

**A code is written where it is named, as a literal**, so that the set of codes can be read off the
sources. A site forwarding a code another site named is the exception, and forwards one already
checked.

**A parameter carrying a code is named for what it carries** — `…DetailsId`, `…DescriptionId` or
`…MessageId`. The name is what the check reads, so a parameter named for anything else takes its
code out of the set.

**A placeholder a message names is supplied at every throw site of that code.** An unsupplied name
[reaches the reader as the bare word](i18n-standard.md#keys) rather than as a failure.

**A code is renamed as a breaking change.** A client branches on the key.

**The technical message is for troubleshooting; the description is for a person who cannot fix it.**
The first may name a scope, a claim, a provider or an algorithm; the second says what the reader
does next, in their own words.

**A code reads `<domain>.<thing>.<condition>`**, most general first. A code with no domain is one
the framework refused rather than a rule of ours.

**The condition names what failed.** The status is already in the response and in the handler table.

**A `5xx` tells the caller that the server failed.** Keep the row, the key, the provider and the
step in the log, behind the flag that gates them.

## The apostrophe rule

**A placeholder is written bare**, as `{scope}` and never as `'{scope}'`. The message source reads a
quoted brace expression as a literal and emits it verbatim, so interpolation silently does not
happen.

## What this standard does not cover

**Retryability as a wire signal.** The status is the only signal; no header says how long to wait.

**Error aggregation.** One request reports one failure, except for the per-property validation
errors [the API standard](api-standard.md#errors) describes.

**Whether a placeholder is supplied.** The rule above is stated and nothing enforces it: the set of
codes is readable from the sources, the set of values each throw site passes is not, and a name with
no value fails silently exactly as a missing message did.

---

← [Design documentation](index.md)
