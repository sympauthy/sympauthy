---
description: Where a string a person reads comes from — which bundle, how its keys are named, and
  how a locale is resolved.
paths:
  - "server/src/main/resources/*_messages*.properties"
  - "server/src/main/resources/views/**"
---

# Internationalization standard

Every string a person reads comes from a bundle, resolved against their own locale at the moment it
is rendered. What a failure carries before it is rendered is
[the exception standard](exception-code-standard.md).

## A bundle per audience

**A bundle serves one audience, and that audience decides what may be said in it.** A message naming
a claim, a provider or an algorithm belongs to an operator's bundle. The bundles under the server's
resources are the set that exists.

**A class asking for messages names its bundle by qualifier.** Say which audience is reading before
asking for a string.

## Keys

**An error code is its key**, and the same code prefixed with `description.` names the end-user's
message. [The exception standard](exception-code-standard.md#a-code-names-two-messages) owns how a
code is built.

**A display key is the identifier of the thing displayed, with the facet appended.** A deployment
derives the keys it may override from the claims it configured.

**A mail key names the template and the usage.** The template segment is the FreeMarker file's name
without its extension.

**A placeholder is written bare.** [The apostrophe
rule](exception-code-standard.md#the-apostrophe-rule) holds in every bundle.

**A message interpolates a value as its `toString()`.** The message source is a hand-rolled `{name}`
substitution rather than a format, so a bundle sentence needing a formatted number, a duration or a
plural is reworded rather than parameterised.

**A name with no value is written out as the bare word.** Nothing fails and no brace survives, so a
message naming a placeholder nobody supplies reads as a sentence with a stray identifier in it.

## Locale

**The locale comes from the request.** A request stating no preference, and a preference with no
translation, both resolve to the hard-coded default.

**A missing translation falls back to the default locale's string.** A reader gets a sentence in
another language rather than a dotted identifier.

**Rendering happens at the edge, once.** A manager throws a code and its values, and the layer
holding the request renders them.

## Mail

**A mail is a template plus its keys.** The template holds the structure and the shared partials;
every string in it comes from the bundle.

**A mail is queued and sent asynchronously, so its locale is captured when the message is created.**

## What this standard does not cover

**Which languages ship.** A language is a further copy of each bundle, and adding one changes
nothing here.

**Translation tooling and workflow.** The bundles are edited by hand in this repository.

**Locale-sensitive formatting.** The message source cannot format a number, a duration or a plural,
and nothing preformats one before handing it over; the pages and the mail templates make their own
choices.

**Right-to-left and per-locale layout.** A concern of the pages, not of the server that supplies
their strings.

---

← [Design documentation](index.md)
