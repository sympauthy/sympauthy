---
description: How every document in `docs/` is written — what it names, how it is kept true, and the
  mechanics both halves share.
paths:
  - "docs/**"
---

# Documentation standard

What holds of every document in `docs/`, whichever half it sits in. Each half then has a standard of
its own, read on top of this one: [the standards standard](docs-standards-standard.md) for a rule in
`docs/standards/`, and [the design documentation standard](docs-design-standard.md) for a document
describing a part of the server in `docs/design/`. Neither of them restates what is here.

## What a document names

**A place the reader is sent to is named in full.** Spell out the file, the class or the directory
the change is unfinished without — the factory a generated mapper is registered in, the metadata the
native image reads.

**A set that will grow is described by its criterion**, with the source of truth named as
authoritative: the sealed type, the enum and its KDoc, the configuration. Name a member as an
illustration of the criterion.

```markdown
**A purpose that confirms an action before it proceeds is a gate.** Which purposes exist is the
sealed type's KDoc.
```

**A set that will grow is never counted.** A rule saying *twins*, *twice* or *the second copy* is
false the day a third dialect, provider or surface arrives, so write it at any size — *once per
dialect*, *a copy per dialect* — and let the criterion say what the members are.

**The human is a person.** Write *a person* for whoever signs in or reads a message, never *the
user* or *the end-user*. Keep *user* for what the tree spells that way — the `User` model, the
`users` table, `readable-by-user-when-consented`, the `/api/v1/user` surface.

**What this server holds for a person is an account.** A credential, the claims, the provider links
and the second factor are an account's, so a sentence about them says *account* where it would have
said *the user's*.

## A document says what is true now

**A document describes the design as it stands.** Write what is built today.

**A document is edited in place.** Rewrite the sentence that stopped being true, so that one
question has one answer.

**The history stays in git and in the tracker.** Both hold the previous version beside the change
that caused it and the discussion that settled it.

**A new document joins its half's index in the same commit** — [how the system
works](../design/index.md) or [how the code is written](index.md) — with a one-line summary of what
it covers.

## Shape of a document

**What a document deliberately leaves open is written down in a closing section.** Name the subject
and say that it is open, so that an absence reads as a decision rather than as an oversight.

**A document ends with a horizontal rule and a link back to its half's index.**

## Mechanics

**Wrap at 100 columns and write headings in sentence case.**

**A heading's depth is how general it is.** A `##` section asks one question about the subject and a
`###` or `####` under it answers one case of that question, so a reader going down the headings
meets the general answer before any particular one.

**Write plain Markdown.** Keep to what GitHub and an IDE both render.

**Link between documents relatively, keeping the `.md`.** Link to the [public
documentation](https://sympauthy.github.io) with an absolute URL.

## What this standard does not cover

**What a rule looks like.** [The standards standard](docs-standards-standard.md) holds the shape of
one, the frontmatter that loads it, and what a standard states in place of code.

**What a design document answers.** [The design documentation
standard](docs-design-standard.md) holds it, along with what one argues and what it leaves to the
code and to the FAQ.

**The indexes.** [The one at the top of `docs/`](../index.md) names the two halves and states the
project's goals, and each half's own lists what is in it; nothing here shapes them beyond the
mechanics above.

**The public documentation.** It is written in another repository, and nothing here governs it.

**Diagrams.** No document draws one, and no format is set for the first that does.

**Checking.** Nothing verifies a link, a glob or an anchor; each is kept true by whoever moves what
it names.

---

← [How the code is written](index.md)
