---
description: What an issue in the tracker settles and what it leaves to the change — its title, its
  sections, what a feature and a bug each name, and the label and milestone it opens under.
---

# Issue standard

How an issue in the tracker is written. An issue is the design of a change before the change exists:
it is read by whoever builds it, by whoever reviews the build against it, and by whoever asks later
why the server behaves as it does. It reads like the documents here rather than like a ticket, and
it stops where the code starts.

## What an issue settles

**An issue settles what the server will do, and leaves how to the change.** The code that follows
is held to the behaviour the issue describes, and to nothing else in it.

**A feature issue describes behaviour and configuration, never the implementation.** It says what a
person, a client or an operator observes, which configuration a deployment writes, and what a caller
is answered with. A class, a method, a table, a column or a migration is the change's own, and
naming one seals a decision the author was not taking.

**A bug issue names where the behaviour is wrong, and proposes a fix without prescribing one.** The
diagnosis is a fact about the code and is stated as one, with the class or the key it was found in.
The fix is the author's first reading, and the change may find a better shape.

**A decision the issue rests on is cited from the document that states it.** Link the sentence in
`docs/` the design follows from, and say where the design departs from one.

**Behaviour is described in the vocabulary `docs/` uses.** Name a thing as the description governing
it names it — an identifier claim value, an audience, a purpose — and keep the wire's own word for
what travels on the wire.

## Title

**The title is one declarative sentence.** A feature's says what will be true once the issue is
closed; a bug's says what goes wrong today.

```markdown
Let a person change the identifier they sign in with, and prove the new one before it counts
A client's inherited default scopes skip the audience check, so another audience's scope is granted
```

## Body

**The body runs through fixed sections, in this order.** A section with nothing to say is left out
rather than left empty.

| Section | Carries |
| --- | --- |
| `## Summary` | the gap today, and the behaviour that closes it |
| one section per decision | what is decided, what it looks like from outside, and why |
| `## Configuration` | each key a deployment writes, what it accepts, and its default |
| `## Documentation` | the pages the public documentation rewrites, and the issue filed for it |
| `## What was considered and not chosen` | each option that lost, with the reason it lost |
| `## Verification` | numbered steps, each an observable outcome |
| `## Out of scope` | what the issue leaves out, and where each part goes |
| `## Related` | issues and references, each with what it has to do with this one |

**A decision section is headed by the decision.** Its paragraphs say why, and what a caller or a
person sees as a result.

**A configuration key is written as a deployment writes it.** Spell the key in full, say what values
it accepts and what the default is, and say what a wrong value is refused with.

**A change to what a deployment configures or a caller observes says what the public documentation
has to say.** Name each page to rewrite and what it has to say, and link the issue filed for it on
the documentation's own tracker.

**An error a caller is answered with is named by its code.** [The API
standard](api-standard.md#errors) makes the code the contract, so the issue fixes it and leaves the
wording free.

**A rejected option is given the reason it lost.** A dismissal is re-litigated in review; a reason
is read there and settles it.

**A verification step is what somebody observes, not what the code does.** Name the configuration
the step runs under where the outcome depends on it.

**An issue whose design changes is edited in place.** A dated note at the top says what changed, and
the sections below it are rewritten to the design as it stands.

## What an issue leaves to others

| Left out | Where it goes |
| --- | --- |
| the files a change touches | the pull request |
| the shape the change gave the code | the code, and the standard that names its shape |
| the argument that survived | `docs/`, in the same change |
| a status report | nowhere; the issue is edited in place |

## Label and milestone

**An issue is labelled `enhancement` or `bug`.**

**A new issue opens in the earliest open milestone, unless the person opening it names another.**
That milestone is the next release; read the list rather than assuming which one it is, since it
moves as releases ship.

## What this standard does not cover

**Pull request descriptions and commit messages.** What a change explains about itself is written
in git, and nothing here shapes it. The files a change touches are described there, which is why an
issue leaves them out.

**Issues grouping other issues.** Whether an epic is opened, and what it carries beyond a list, is
not set.

**How the public documentation is written.** It belongs to another repository; an issue here says
what its pages have to say, and nothing here shapes how they say it.

---

← [Design documentation](index.md)
