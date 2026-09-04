---
description: What a comment carries, and where the rationale that does not belong in one goes
  instead.
paths:
  - "**/*.kt"
  - "**/*.sql"
  - "**/*.yml"
---

# Comment standard

What a comment in this codebase carries, and where the rationale that does not belong in one goes
instead. It applies to Kotlin, to SQL and to YAML alike, and it keeps the density
[the code standards](general-code-standard.md) ask for from becoming one sentence written in fifteen
files.

**A comment carries what the reader cannot get from the code, from the framework's own
documentation, or from these documents.** An annotation that maps an entity to a table, marks a
method transactional or secures a route is documented where it is defined.

**A declaration that follows a convention is left uncommented.** The convention is what makes the
silence legible; a comment says that something here was worth stopping for.

**A load-bearing rule earns a comment where this declaration is the exception** — an `open` that is
not there for a proxy, a handler that had to break the pattern. Following the rule is written by
following it.

**Comment what is particular to the declaration in front of you**, which is usually one of
these:

| Comment | Carries |
| --- | --- |
| a departure | why this declaration does not follow the convention |
| a value the convention misses | which strings a column admits, what a field copies |
| a coupling the file hides | an ordering or a format another system owns |

**A function's KDoc documents the function.** State what it does, what it requires of its arguments,
what it returns and what it throws, naming the error code a caller has to handle.

**The rationale goes where it stays true:**

| Rationale | Goes to |
| --- | --- |
| true of the whole class | the type's KDoc |
| a decision a later reader would re-litigate | `docs/` |
| about this one declaration | the function's KDoc, a blank line below the contract |

**The contract is written as prose.** Name the argument, the result and the failure in the sentences
that say what the function does, and reference an argument as `[name]` so that it links.

**A declaration is documented with KDoc.** Keep `//` for a statement inside a body, where it
explains the line beneath it and is deleted along with it.

**A property is documented above the property.** That is where a reader looks, and where a published
schema takes its description from.

## The same rule outside Kotlin

**A migration carries no comment.** It exists once per dialect, and
[the database standard](database-standard.md#one-schema-spelled-per-dialect) owns the shape of both.

**The rationale a migration would have carried goes on the entity that mirrors the column.** What
is true of the value rather than of the shape it is stored in is documented on the business model
instead.

**A message in a bundle is documented by being written well.** The technical half already explains
the failure.

**A configuration example is commented for the operator.** Say what the value does and what happens
if it is wrong.

## What this standard does not cover

**TODO and FIXME markers.** Nothing says whether one may be left in the tree, or what it has to
reference.

**Commit messages and pull request descriptions.** What a change explains about itself is written in
git, and nothing here shapes it.

---

← [Design documentation](index.md)
