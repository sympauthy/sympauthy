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
instead. It applies to Kotlin, to SQL and to YAML alike, and it is what keeps the density
[the code standards](general-code-standard.md) ask for from turning into the same sentence written
in fifteen files.

The rationale here is dense on purpose. What keeps it readable is that it is never written twice: a
comment earns its place by carrying something the reader cannot get from the code in front of them,
from the framework's own documentation, or from these documents.

**Do not paraphrase a framework.** An annotation that maps an entity to a table, marks a method
transactional, secures a route or declares a repository's dialect is documented by the framework
that defines it. A KDoc restating what it does is a second, unversioned copy of that documentation,
and it is wrong the day the annotation's behaviour changes. A reader looks an annotation up once,
not once per declaration.

**Do not restate a convention a standard already sets.** Every entity's identifier is nullable and
assigned by the database. Every repository has two dialect twins. Every manager method is `suspend`.
Those decisions and their reasons are written down once, in the standard that owns them. A KDoc
repeating one of them on a declaration is the first of *N* copies — one per entity, per repository,
per manager, forever — and *N* copies drift, at which point the code disagrees with itself about a
rule that only ever had one answer.

The convention is what makes the silence legible. A declaration following the rule, with no comment
on it, is the entire signal a reader needs. Annotating it says the opposite: that something here was
worth stopping for.

**A rule being load-bearing is not a licence to restate it.** The rules that most invite an
explanation at the declaration are the ones where getting it wrong compiles and fails later — `open`
on a class carrying an AOP annotation, a generated class the native image has to be told about, a
message placeholder that must not be quoted. That instinct is backwards. Those are the rules held in
the most places, so they are the ones a comment copies the most times, and the copies are what
drift: the second paraphrases, the fourth contradicts the standard, and a reader now has two answers
to a question that only ever had one.

What a load-bearing rule earns at the declaration is a comment when *this* declaration is the
exception — an `open` that is not there for a proxy, a handler that had to break the pattern.
Following the rule is written by following it.

**Comment what is particular to the declaration in front of you.** Three things usually qualify:

- a *departure* from a convention, which needs its reason on the spot;
- a value the convention does not cover — which strings a column admits, what a copied field is a
  copy *of*, why a number is allowed to be stale;
- a coupling the reader cannot see from the file — a format another project owns, an ordering
  something downstream depends on, a row another system writes.

**A function's KDoc documents the function, not the design.** It answers what a caller needs in
order to call it: what it does, what it requires of its arguments, what it returns, and what it
throws — including the error code, because a caller that has to handle a failure has to name it.
Preconditions count; "must not be blank" earns its line, because a caller cannot read it off the
signature. Why the function exists, why it is shaped this way, and what the alternative would have
cost do not.

That is not a licence to write less rationale; it is a rule about where the rationale goes. It has
three better homes, and the choice is usually obvious:

- the **type's** KDoc, when the reason is true of the whole class — a mapper that rejects rows, a
  manager that owns a transaction, an enum whose values mean different things to the engine reading
  them;
- **`docs/`**, when it is a decision a later reader would otherwise re-litigate;
- the **function's own KDoc, below the contract**, when it is genuinely about this one declaration
  and belongs nowhere else — a blank line after the throws, then the reason.

Two things go wrong when the design lands in a function's KDoc instead. It is read where a signature
is read — an IDE hover at the call site, a generated API description — where a paragraph about a
rejected alternative is noise in front of the one sentence the caller came for. And it is the copy
most likely to rot, because a function is rewritten far more often than the decision behind it
changes: the rationale outlives the body it was attached to and then quietly describes code that no
longer exists.

**That contract is written as prose, never as `@param`, `@return` or `@throws` tags.** The argument,
the result and the failure are named in the sentences that say what the function does, and an
argument is referenced as `[name]` so that it links instead of being spelled a second time.

A tag holds exactly one name, so a contract split across tags has nowhere to put the part a caller
most needs: that this argument is only read when that one is null, that this failure is the same
condition the return value describes from the other side. The fragments are then read in isolation,
and the sentence relating them is never written. Tags also invite the line that carries nothing —
the signature already gives the name, the type and the nullability, so a tag restating them is a
paraphrase of the code in the way a KDoc restating an annotation is a paraphrase of the framework.

**A declaration is documented with KDoc, never with `//` above it.** `//` belongs to statements
*inside* a body, where it explains the line beneath it and is deleted along with it. A `//` sitting
above a `fun`, a `val` or a `class` is a KDoc that has opted out of being one: it reaches no IDE
hover, no generated schema description, and no `[link]` from another file — so the reader who most
needs it, the one at the call site who never opens this file, is the one it hides from. The choice
is which section of the KDoc a sentence belongs in, not whether to use KDoc.

**A property is documented above the property, not in the class's KDoc.** A class-level tag listing
its properties puts the description somewhere the property is not, so the two drift the first time
one is renamed, and a reader looking at the property sees nothing. It also costs the description at
the only place it is generated from, for the resources whose properties become a published schema.

## The same rule outside Kotlin

**A migration carries no comment.** Every migration exists
[twice, once per dialect](database-standard.md#two-dialects-one-schema), so a sentence written in
one of them is either missing from its twin or written out a second time. The second copy is what
drifts: the two files are edited by separate changes, each reviewed as its own diff, and nothing —
not the compiler, not Flyway, not a test — ever compares their prose. A comment that is wrong in one
dialect and right in the other is worse than no comment at all, because a reader has no way to tell
which of the two they opened.

**The rationale a migration would have carried goes on the business model.** A column that needs
explaining is a property that needs explaining, and the model is the declaration every value passes
through on its way to the table — written once, and the one an IDE shows at the call site where the
value is being decided. Where a column's *shape* is what needs explaining rather than what it means,
that is a rule about the schema and belongs to
[the database standard](database-standard.md).

**A message in a bundle is documented by being written well.** The technical half already explains
the failure; a comment above it explaining the explanation is the clearest case of writing the same
sentence twice.

**Configuration examples are commented for the operator, not for us.** The sample file a deployment
copies is read by someone who will never open this repository, so a comment there says what the
value does and what happens if it is wrong — not why the parser is layered the way it is.

---

← [Design documentation](index.md)
