---
description: How a standard in `docs/standards/` is written — the shape of a rule, and what a
  standard states in place of code.
paths:
  - "docs/standards/**"
  - ".claude/rules/**"
---

# Standards standard

How a standard in `docs/standards/` is written — a document named `<subject>-standard.md`, holding
the rules a change is held to. [The standards index](index.md) names every one of them and says what
each covers. What holds of every document in `docs/` is [the documentation
standard](docs-standard.md), and this is read on top of it.

## Shape of a standard

**A standard is named `<subject>-standard.md` and holds one subject's rules.** Link to the standard
that owns a neighbouring rule.

**A rule leads in bold, and at most two sentences follow it.** Those sentences carry further
directive — what to write, what to name, what to do next.

```markdown
**A migration is named `V{major}_{minor}_{patch}_{sequence}__{table}_{new|edit}.sql`.** The version
is the release the change ships in. The sequence orders the migrations within that release.
```

**A rule is written as what to do.** State the form the reader has to produce.

**An example shows a compliant case, in five lines or fewer.** Add one where showing the form is
shorter than stating it.

**Enumerable rules go in a table.** Keep a cell to a value or a short phrase, and write anything
longer as a paragraph.

## Frontmatter

**A standard opens with frontmatter.** The block carries `description` and, where a file loads it,
`paths`; the heading under it names the standard.

```yaml
---
description: <what the standard governs, in one line>
paths:
  - "<a glob from the repository root>"
---
```

**`description` says what the standard governs, in one line.** Write it so that it stays true when a
rule inside changes. Continue it on an indented line past 100 columns.

**`paths` lists quoted globs, and it is what loads the standard.** A standard reaches an agent when
a file one of its globs matches is read. Keep every glob matching something, and move a glob when
the package it names moves.

**The key is spelled `paths`, the way the tooling that reads it spells it.** Rename it here when
what reads it renames it.

**A standard carrying `paths` is symlinked into `.claude/rules/` in the same commit that writes
it.** The symlink is what a file read resolves, and it carries the frontmatter with it.

**A standard governing what lives outside the tree carries `description` alone.** No file read loads
it, so it is not symlinked, and `CLAUDE.md` names it beside the task it governs.

## What a standard names

**A shape carries the rule.** Write the pattern a name, a path or a key must match — `…Manager`,
`find…OrNull()`, `V{major}_{minor}_{patch}_{sequence}__{table}_{new|edit}.sql`. The shape is the
whole instruction.

**A name that is itself the rule is written as it is.** The exception type a layer must throw, the
annotation a class must carry and the interface a repository must extend are named directly.

## What a standard sends elsewhere

**A rule the code breaks is still written as the rule.** File the breach as an issue and leave the
standard unqualified.

**A follow-up is filed once the tracker has been searched for one that covers it.** Broaden an
existing issue where it is too narrow.

**A departure is documented where it departs.** The class that breaks a pattern carries the reason
in [its own documentation](comment-standard.md). A carve-out belongs in a standard when a second
case would also fall under it.

**A fact about one class is documented in that class**, as a [comment](comment-standard.md). A
standard carries what has a shape.

## What this standard does not cover

**The design documentation.** [Its own standard](docs-design-standard.md) holds it. The two kinds
are split rather than written together because they are read at different moments — a rule by
somebody about to break it, a design document by somebody about to change the part it describes.

**What both halves share.** [The documentation standard](docs-standard.md) holds the naming, the
mechanics and the rule that a document is edited in place; a rule stated there is not restated here.

**How long a standard may be.** The ones written run from a page to several, and nothing says which
is right for a subject.

---

← [How the code is written](index.md)
