# Documentation standard

How a document in `docs/` is written. What each one covers is [the index](index.md).

## Two kinds of document

**[Architecture](architecture.md) describes the system**, so it names the system's parts — the
packages, the layers, the engine that sequences an interactive flow. A reader arrives there to find
out what exists and where, and a description that named nothing would answer neither.
[The interactive flow](interactive-flow.md) and [security](security.md) are descriptions too: each
names the types its subject is actually built from, because the subject *is* those types.

**A standard describes a rule**, so it does not name the code that happens to follow it. The rule is
stated in the abstract and illustrated with a shape — `…Manager`, `find…OrNull()`,
`description.{detailsId}`, `V{major}_{minor}_{patch}_{sequence}__{table}_new.sql` — never with a
class, function, migration or message key lifted from `server/`.

## Why a standard names no code

An example taken from the codebase rots quietly. The rule stays true while the example stops being
one: the class is renamed, the method is inlined, the quoted snippet is rewritten to say something
else. Nothing fails when that happens — not a test, not the build, not the compiler — so what is
left is a standard arguing for itself with something that is no longer there, and a reader with no
way to tell which half went stale.

This is not hypothetical here. The configuration guide these documents replace walked a reader
through five classes of a mail configuration domain that has never existed in this server, and
spelled a nested provider class in a way the naming rule three paragraphs above it forbids. Both had
been wrong for a year. Neither could fail.

The shape does not rot, because it is the rule written twice.

**Pointing is not illustrating.** A document may say where a rule is implemented — the security
constants that name a role, the reflection metadata a generated mapper is registered in, the message
bundle a code resolves against — because that is the reader's next stop, and a path that has moved
is obviously wrong rather than quietly wrong. What it may not do is prove a rule by exhibiting a
class that obeys it, or quote code as evidence that the rule is real.

**A name that *is* the rule is vocabulary, not an example.** The exception type a layer must throw,
the annotation a class must carry, the interface a repository must extend: a standard that refused
to name these would have no way to state its rule at all, and none of them can rot into a lie
without the rule itself changing. What a standard still may not do is name a class that merely
*obeys* a rule.

**A rule that cannot be stated without pointing at code is not a rule yet.** Write the shape it must
have instead; if there is no shape, what has been found is a fact about one class, and it belongs in
that class as a [comment](comment-standard.md).

**Do not enumerate a set that will grow.** Not the count, and not the members. A list of what exists
today — the purposes a flow can have, the surfaces the server exposes, the providers that ship, the
message bundles — reads as the complete set whether or not it says so, and it is silently wrong the
day the set is extended, which is the day nobody rereads the document. A count in prose is the worst
form of it: "the five purposes" is already a lie the moment a sixth is added, and it is a lie no
test can catch. A table of all five is the same lie with more surface area.

Describe the **criterion for membership** instead, and name the source of truth as authoritative —
the sealed type, the enum and its KDoc, the configuration. A reader who needs the current members
reads them where they cannot be out of date; a reader who needs to know whether a *new* member
belongs needs the criterion, which is the thing only a document can give them.

Members may still be named, as **illustrations of the criterion** rather than as the elements of a
complete list — and the difference has to be visible in the writing. "Confirming an action is a
gate" survives a seventh purpose; a table with a row per purpose does not.

**A deviation is not documented either.** Where the code breaks a rule this set states, the rule is
still written as the rule, and the breach goes to an issue. Listing today's offenders in a standard
is exhibiting code by another name, and the list is wrong the moment one of them is fixed.

## Shape of a document

- **One subject per document**, cross-linked rather than restated. A rule written in two places is a
  rule that will eventually disagree with itself — the same reason a [comment](comment-standard.md)
  does not repeat what is written here.
- **A rule leads in bold, its reasoning follows in plain text.** The bold sentence is what a reader
  scanning for the rule needs; the paragraph under it is why, and is what makes the rule arguable
  rather than arbitrary.
- **Enumerable rules go in a table**, prose rules in paragraphs. A table with one column of prose is
  a list wearing a costume.
- **The reasoning is part of the document, not an appendix.** A standard that only lists rules gets
  followed until the first inconvenience; one that says what it is protecting gets applied to cases
  it never listed.
- **What a standard deliberately leaves open is written down**, in a closing section. An absence
  that is a decision and an absence that is an oversight look identical, and only one of them should
  survive review a second time.

## Mechanics

Wrapped at 100 columns, to match the code. Headings are sentence case. Plain Markdown: these files
are read on GitHub and in an IDE, where a VitePress admonition renders as the literal characters
that spell it. Links between documents are relative and keep their `.md`, so they resolve in both;
links to the [public documentation](https://sympauthy.github.io) are absolute, because that site is
built from another repository and nothing here can check them.

Every document ends with a rule and a link back to the index.

A new document is added in the same commit to two places: the [index](index.md)'s contents, and
`.claude/rules/`, as a symlink to the file here. The symlink is what puts the standard in front of
an agent working in this repository, and a document only here is one only humans read. Descriptions
are not symlinked — an agent needs the rules it must hold to, not a tour of the system it can read.

---

← [Design documentation](index.md)
