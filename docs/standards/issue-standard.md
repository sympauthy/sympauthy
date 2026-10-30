---
description: What an issue in the tracker settles and what it leaves to the change — its kind, its
  title, the sections each kind runs through, and the label and milestone it opens under.
---

# Issue standard

How an issue in the tracker is written. An issue is the design of a change before the change exists:
it is read by whoever builds it, by whoever reviews the build against it, and by whoever asks later
why the server behaves as it does. It reads like the documents here rather than like a ticket, and
it stops where the code starts.

## Kinds of issue

**An issue is of one kind, and its kind is its label.**

| Kind | What it changes | Label |
| --- | --- | --- |
| feature | what a person, a client or an operator observes, new or already there | `feature` |
| enhancement | how the server is built — the code's shape, the build, the tests, the analysis | `enhancement` |
| bug | behaviour that departs from what `docs/`, a specification or the code promises | `bug` |

**A change to what anybody outside the code observes is a feature, whatever else it changes.** A
configuration key a deployment writes, an answer a caller reads and a metric an operator watches are
all observed, so an issue adding one is not an enhancement.

**A departure from a promise is a bug, however large its fix.** Behaviour nobody promised is a
feature, however wrong it looks.

## What every issue settles

**An issue settles what the change will do, and leaves the rest to the change.** The code that
follows is held to what the issue describes, and to nothing else in it.

**A decision the issue rests on is cited from the document that states it.** Link the sentence in
`docs/` the design follows from, and say where the design departs from one.

**Behaviour is described in the vocabulary `docs/` uses.** Name a thing as the document governing
it names it — an identifier claim value, an audience, a purpose — and keep the wire's own word for
what travels on the wire.

**The title is one declarative sentence.** A feature's or an enhancement's says what will be true
once the issue is closed; a bug's says what goes wrong today, and what that leads to.

**A section with nothing to say is left out rather than left empty.** Each kind's sections run in
the order its table gives.

**A rejected option is given the reason it lost.** A dismissal is re-litigated in review; a reason
is read there and settles it.

**An issue whose design changes is edited in place.** A dated note at the top says what changed, and
the sections below it are rewritten to the design as it stands.

## Feature

**A feature describes behaviour and configuration, never the implementation.** It says what a
person, a client or an operator observes, which configuration a deployment writes, and what a caller
is answered with. A class, a method, a table, a column or a migration is the change's own, and
naming one seals a decision the author was not taking.

```markdown
Let a person change the identifier they sign in with, and prove the new one before it counts
```

| Section | Carries |
| --- | --- |
| `## Summary` | the gap today, and the behaviour that closes it |
| one section per decision | what is decided, what it looks like from outside, and why |
| `## Configuration` | each key a deployment writes, what it accepts, and its default |
| `## Documentation` | what the change impacts in the public documentation |
| `## What was considered and not chosen` | each option that lost, with the reason it lost |
| `## Verification` | numbered steps, each an observable outcome |
| `## Out of scope` | what the issue leaves out, and where each part goes |
| `## Related` | issues and references, each with what it has to do with this one |

**A decision section is headed by the decision.** Its paragraphs say why, and what a caller or a
person sees as a result.

**A configuration key is written as a deployment writes it.** Spell the key in full, say what values
it accepts and what the default is, and say what a wrong value is refused with.

**A change to what a deployment configures or a caller observes names what it impacts in the public
documentation.** Name the configuration key, the endpoint or the flow, and leave which page says
what to the documentation's own issue.

**The documentation's own issue is opened once this one is closed, naming what is impacted and by
which commits in which repository.** The build can change what a caller observes, so the issue is
written from what shipped; choosing what to say, and where, is that issue's own work.

**An error a caller is answered with is named by its code.** [The API
standard](api-standard.md#errors) makes the code the contract, so the issue fixes it and leaves the
wording free.

**A verification step is what somebody observes, not what the code does.** Name the configuration
the step runs under where the outcome depends on it.

## Enhancement

**An enhancement names the shape it gives the code, since that shape is what it changes.** Head each
decision with it — the package a rule lives in, the one place a value is read, the order the
analysis holds — and leave the files it touches to the pull request.

```markdown
Read a token request once into the grant it asks for, and leave the OAuth2 managers only the rules
the protocol names
```

| Section | Carries |
| --- | --- |
| `## Summary` | the shape today, what it costs, and the shape that replaces it |
| one section per decision | the shape decided, and why |
| `## What was considered and not chosen` | each option that lost, with the reason it lost |
| `## Verification` | numbered steps, each what the build, a test or the analysis reports |
| `## Out of scope` | what the issue leaves out, and where each part goes |
| `## Related` | issues and references, each with what it has to do with this one |

**An enhancement says what it leaves unchanged for a caller.** The suites already proving that
behaviour are what its verification runs, unchanged.

**A shape the tree is to keep is written into a standard by the same change.** The issue names the
standard that will state it, so the rule outlives the change that first applied it.

## Bug

**A bug states its diagnosis first, and its fix only once the fix is settled.** The diagnosis is a
fact about the code; until somebody settles the fix, the issue stops after it.

```markdown
A client's inherited default scopes skip the audience check, so a scope restricted to another
audience can be granted
```

| Section | Carries |
| --- | --- |
| `## What is claimed` | the promise the code breaks, quoted and linked |
| `## What happens` | the mechanism in real symbols, and what the failure looks like from outside |
| `## How to reproduce` | the sequence that reaches the failure, when it is fully known |
| one section per decision | the behaviour the fix restores, and why |
| `## What was considered and not chosen` | each option that lost, with the reason it lost |
| `## Verification` | numbered steps, each an observable outcome |
| `## Out of scope` | what the issue leaves out, and where each part goes |
| `## Related` | issues and references, each with what it has to do with this one |

**What is claimed quotes the `docs/` sentence the code breaks.** Link it as a permalink to the file
on `main`; where no document governs the behaviour, quote what the code itself promises — its KDoc,
its error code, its configuration key, or the specification it implements.

**What happens names the mechanism in real symbols.** Give the class, the function and the call
chain from the entry point to the failure, and quote the five or fewer lines that carry it. Say what
the failure looks like from outside — silence, a wrong answer, a wait — since that is what somebody
will one day search for.

**A bug says whether it is a regression.** Name the change that introduced it, or say it predates
the change that made it visible.

**How to reproduce is written only when the sequence is fully known.** Give the configuration, the
requests and their order, enough that somebody else reaches the same state. A half-known
reproduction is read as a known one, so leave the section out instead.

**The fix is described as the behaviour it restores, the way a feature is.** The diagnosis names the
code because it is a fact about it; the fix is the author's first reading, and the change may find a
better shape.

## What an issue leaves to others

| Left out | Where it goes |
| --- | --- |
| the files a change touches | the pull request |
| the shape a feature or a fix gave the code | the code, and the standard that names its shape |
| the argument that survived | `docs/`, in the same change |
| a status report | nowhere; the issue is edited in place |

## Milestone

**A new issue opens in the earliest open milestone, unless the person opening it names another.**
That milestone is the next release; read the list rather than assuming which one it is, since it
moves as releases ship.

## What this standard does not cover

**Pull request descriptions and commit messages.** What a change explains about itself is written
in git, and nothing here shapes it. The files a change touches are described there, which is why an
issue leaves them out.

**Which review findings are filed.** The `review-followup` skill decides which findings become an
issue and how each is checked first; the issue it files is written to this standard.

**Issues grouping other issues.** Whether an epic is opened, and what it carries beyond a list, is
not set.

**How the public documentation is written.** It belongs to another repository; an issue here says
what a change impacts there, and nothing here shapes what its pages say or where.

---

← [How the code is written](index.md)
