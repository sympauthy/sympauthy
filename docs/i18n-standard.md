# Internationalization standard

Every string a person reads comes from a bundle, resolved against their own locale at the moment it
is rendered. This document says which bundle, how its keys are named, and where the locale comes
from. What a failure carries *before* it is rendered is
[the exception standard](exception-code-standard.md).

## A bundle per audience

**A bundle exists for one audience, and the audience decides what may be said in it.** That is the
test for whether a string belongs in an existing bundle or a new one: not what the message is about,
but who reads it and what they are allowed to be told. A message an operator reads may name a claim,
a provider or an algorithm; a message an end-user reads may not.

Merging two audiences into one bundle would put a single editorial rule over two kinds of writing,
and the one that would lose is the one nobody in this repository ever reads. Today that separates
the failure messages, the labels the sign-in pages render, and the text of the mails — the resource
bundles under the server's resources are the source of truth for which exist.

**Each bundle is injected by its own qualifier**, so a class asking for messages says which audience
it is writing for. There is no default and no unqualified message source: a class that wants strings
has to name the bundle, which is the point at which it also has to decide who is reading.

## Keys

**An error code *is* its key**, and the same code prefixed with `description.` names the end-user's
version of the same failure. This is [the exception
standard's](exception-code-standard.md#a-code-names-two-messages) rule, and the consequence for this
document is that the error bundle has no naming scheme of its own — it inherits the codes'.

**A display key names the thing being displayed and the facet of it.** A claim's label is the
claim's own identifier with the facet appended, so the set of keys a deployment may override is
derivable from the set of claims it configured, rather than being a list somebody has to maintain in
parallel.

**A mail key names the template and the usage.** The template segment is the FreeMarker file's name
without its extension, which is what keeps a template and its strings findable from each other: a
template with no keys and keys with no template are both visible as an absence rather than as
nothing.

**A placeholder is written bare, never quoted.** `'{value}'` is emitted literally and interpolation
silently does not happen. The rule and the reason live in
[the exception standard](exception-code-standard.md#the-apostrophe-rule); it applies to every
bundle.

## Locale

**The locale comes from the request, and falls back to one hard-coded default.** A request that
states no preference, and a preference for which no translation exists, both resolve to the same
default rather than to an empty string or a key.

**A missing translation falls back to the default locale's string, never to the key.** A reader
should get a sentence in the wrong language rather than a dotted identifier, because the first is
recoverable by a human and the second is not.

**Rendering happens at the edge, once.** Nothing below the HTTP boundary holds a rendered message —
a manager throws a code and values, and the layer that has the request renders them. This is what
keeps a manager callable from a scheduled job with no `Accept-Language` behind it, and it is the
same boundary [the exception
standard](exception-code-standard.md#an-exception-carries-keys-never-a-sentence) draws.

## Mail

**A mail is a template plus its keys, and neither is complete alone.** The template holds the
structure and the shared partials; every string in it comes from the bundle. A template with a
literal sentence in it is a sentence that cannot be translated and that nobody will find when they
go looking for the text a user reported.

**Mail is queued and sent asynchronously**, so the locale has to be captured at the moment the
message is created rather than read when it is sent. A recipient's language is not a property of the
sending thread.

## What this standard does not cover

**Which languages ship.** A language is a further copy of each bundle, and adding one is adding
files — nothing here has to change to accept it.

**Translation tooling and workflow.** The bundles are edited by hand in this repository. Where
translations would come from, and how a new key reaches a translator, is a process nobody has needed
yet.

**Locale-sensitive formatting.** Dates and numbers that reach a person are formatted by whatever
renders them. Nothing here sets a format per locale, and the sign-in pages — which are a separate
application — make their own choices.

**Right-to-left and per-locale layout.** A concern of the pages, not of the server that supplies
their strings.

---

← [Design documentation](index.md)
