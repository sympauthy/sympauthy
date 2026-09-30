---
description: How a design document in `docs/design/` is written — what it answers about the part of
  the server it names, and what it leaves to the code and to the FAQ.
paths:
  - "docs/design/**"
---

# Design documentation standard

How a design document is written — one of the design documentation in `docs/design/`, saying how one
part of the server works. [The design index](../design/index.md) names every one of them and says
what each covers. What holds of every document in `docs/` is [the documentation
standard](docs-standard.md), and this is read on top of it; [the standards
standard](docs-standards-standard.md) holds the other half, the rules a change is held to.

This standard reaches an agent reading anything under `docs/design/`, including the two documents
there that are not design documentation; the closing section names them.

## What a design document answers

**A design document answers two questions about its part: what it does, and what implements it.**
Write
the functional half first — what a deployment declares, what a person gets, what the part is for —
and the technical half as the types a reader has to open to change it.

**The technical half names where a change starts, not everything it touches.** The base class, the
engine, the enum, the table: two or three names a reader opens next, rather than a map of the
package.

**The authority is named and deferred to.** Where a type already states something — an enum's KDoc,
an interface's contract — name it as the authority and write only the part the type cannot say.

```markdown
**`InteractiveFlowPurpose` is the authoritative list, and its KDoc is the authoritative description
of each value.** What this document holds is the part the enum cannot say: the three roles a
purpose plays, and how the engine treats each.
```

**A design document says why, where the why would otherwise be re-decided.** A reader who can see
what the code does and not why it does it is the one who undoes it.

## Shape of a design document

**A design document is named for the part it describes, and carries no suffix** —
`identifier-claims.md`, `provisional-user.md`, `security-context.md`. It opens no frontmatter
either: only a standard carries one.

**It opens by saying what the part is and what the document covers**, in a paragraph or two under
the heading, and links the document that owns what it leaves out.

**A section is one question about the part, and its heading is that question or its answer** —
*The engine*, *What is kept, and for how long*, *A row that does not count yet*. A subsection
answers one case of it, because [a heading's depth is how general it
is](docs-standard.md#mechanics) here as in a standard.

**A claim leads in bold, and the sentences under it argue it.** Two to five: a design document
carries the reasoning that a standard compresses into a rule.

**The sections run from function to technical, and the functional half is readable by somebody
entering the project.** What a deployment declares, what a person gets and what the part refuses
come first, and nothing there asks the reader to have opened a class; the sections naming the types,
the tables, the configuration properties, the lock a writer takes, the encodings and the channels
come after. A reader who stops halfway is left with what the part is for rather than with how it is
spelled, and the [two questions](#what-a-design-document-answers) are answered in the order they
are asked.

## What a design document leaves out

**The options that lost.** A design document states the decision; what was considered against it and
why it was rejected is an entry in [the design FAQ](../design/design-faq.md), linked from the
sentence that states the decision.

**The cost, the measurement and the migration path.** Name a consequence in a clause and send the
argument elsewhere. Writing it out is what turns a paragraph a reader skims into one they skip.

```markdown
**Every claim carrying a value carries the hash, and the index covers every claim that does.**
Neither of them depends on the set a deployment declares — [the design
FAQ](../design/design-faq.md#is-the-identifier-lookup-indexed-over-the-claims-a-deployment-identifies-by)
holds what that costs.
```

**A signature, a field list or a call sequence.** Write what a reader cannot get from the file in
front of them; [the comment standard](comment-standard.md) holds the same rule for a KDoc, including
its ban on a census of call sites.

**A code listing.** A fenced block holds a shape the prose cannot draw — a map of the surfaces, a
directory tree — and nothing that would go stale with a rename.

## What this standard does not cover

**The standards.** [The standards standard](docs-standards-standard.md) holds how a rule is written
and what a standard states in place of code.

**What both halves share.** [The documentation standard](docs-standard.md) holds the naming, the
mechanics and the rule that a document is edited in place; a rule stated there is not restated here.

**[The design FAQ](../design/design-faq.md).** Its own preamble says what belongs in it and what an
entry carries, and that is the authority.

**[Running locally](../design/running-locally.md).** It is a getting-started guide rather than a
document describing a part of the server, and nothing here governs one.

**[Technology](../design/technology.md)'s catalogue.** Its entries are named things — a framework, a
library, a runtime — so an entry's heading is that name rather than a question, and the reason a
thing was picked stays under it rather than moving to [the design FAQ](../design/design-faq.md).
Neither rule is waived for convenience: a catalogue answers *what is this built on*, one entry at a
time, and a reader comparing two picks wants both reasons on the page in front of them rather than
one of them a link away. The rest of this standard holds of it, and so does the [closing
section](docs-standard.md#shape-of-a-document).

**How long a design document may be.** The ones written run from a page to ten, and nothing says
which is right for a part.

---

← [How the code is written](index.md)
