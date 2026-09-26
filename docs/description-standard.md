---
description: How a description in `docs/` is written — what it answers about the part of the server
  it names, and what it leaves to the code and to the FAQ.
paths:
  - "docs/*.md"
---

# Description standard

How a description is written — a document in `docs/` that says how one part of the server works.
[The index](index.md) names every description and says what each of them covers, and
[the documentation standard](docs-standard.md) holds the other kind of document there, the
standards. This standard reaches an agent reading anything in `docs/`, because a glob cannot tell a
description from a standard by its name; the closing section names the documents it does not govern.

## What a description answers

**A description answers two questions about its part: what it does, and what implements it.** Write
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

**A description says why, where the why would otherwise be re-decided.** A reader who can see what
the code does and not why it does it is the one who undoes it.

**The naming rules a standard follows hold here too.** A set that will grow is described by its
criterion and never counted, a member is named as an illustration, and a place the reader is sent
to is named in full — [the documentation standard](docs-standard.md#what-a-standard-names) states
them.

## Shape of a description

**A description is named for the part it describes, and carries no suffix** —
`identifier-claims.md`, `provisional-user.md`, `security-context.md`.

**It opens by saying what the part is and what the document covers**, in a paragraph or two under
the heading, and links the document that owns what it leaves out.

**A section is one question about the part, and its heading is that question or its answer** —
*The engine*, *What is kept, and for how long*, *A row that does not count yet*.

**A claim leads in bold, and the sentences under it argue it.** Two to five: a description carries
the reasoning that a standard compresses into a rule.

**What the document does not settle is written in a closing section.** Name the open question and
say it is open, so that an absence reads as a decision rather than as an oversight.

**A description ends with a horizontal rule and a link back to the index.**

## What a description leaves out

**The options that lost.** A description states the decision; what was considered against it and why
it was rejected is an entry in [the design FAQ](design-faq.md), linked from the sentence that states
the decision.

**The cost, the measurement and the migration path.** Name a consequence in a clause and send the
argument elsewhere. Writing it out is what turns a paragraph a reader skims into one they skip.

```markdown
**Every claim carrying a value carries the hash, and the index covers every claim that does.**
Neither of them depends on the set a deployment declares — [the design
FAQ](design-faq.md#is-the-identifier-lookup-indexed-over-the-claims-a-deployment-identifies-by)
holds what that costs.
```

**A signature, a field list or a call sequence.** Write what a reader cannot get from the file in
front of them; [the comment standard](comment-standard.md) holds the same rule for a KDoc, including
its ban on a census of call sites.

**A code listing.** A fenced block holds a shape the prose cannot draw — a map of the surfaces, a
directory tree — and nothing that would go stale with a rename.

## A description says what is true now

**A description describes the system as it stands, and is edited in place.** Rewrite the sentence
that stopped being true; git and the tracker hold what it said before.

**A new description joins [the index](index.md)'s *How the system works* section in the same
commit**, with a one-line summary of what it covers. It is not symlinked into `.claude/rules/`,
which holds the standards.

**Wrap at 100 columns and write headings in sentence case**, as
[the documentation standard](docs-standard.md#mechanics) asks of every document here.

## What this standard does not cover

**The standards.** [The documentation standard](docs-standard.md) holds how a rule is written and
what a standard states in place of code.

**[The index](index.md).** It is a list of the documents and a statement of the project's goals, and
nothing here shapes it.

**[The design FAQ](design-faq.md).** Its own preamble says what belongs in it and what an entry
carries, and that is the authority.

**[Running locally](running-locally.md).** It is a getting-started guide rather than a description
of a part of the server, and nothing here governs one.

**How long a description may be.** The ones written run from a page to ten, and nothing says which
is right for a part.

---

← [Design documentation](index.md)
