---
name: review-followup
description: File a SympAuthy issue for a defect, or a breach of a rule in docs/, that a code review
  turned up and the branch under review will not fix. Use after /code-review — or any review —
  surfaces something real that predates the diff or is not that diff's to answer.
---

# Filing a follow-up from a review

A review turns up things the branch under review is not going to fix: a defect that predates the
diff, a rule the diff made visible, a breach whose answer is somebody else's to decide. This files
one of them as an issue, so the finding outlives the review that found it.

## What gets filed

**Only a defect, or a breach of a rule in `docs/`.** A defect is code doing something other than
what it promises — its KDoc, its error code, its configuration key, or the behaviour a caller is
entitled to. A breach is code not conforming to a standard. Nothing else is a finding: a preference,
a shape somebody would have written differently, or a capability nobody has decided to build is not
one.

**Never say whether the answer is a fix or a feature.** The issue reports what is wrong and stops
there. Whether that is answered with one line, with a validator, or with something that does not
exist yet is decided by whoever takes it on, and an issue that has already sized the response
narrows the reading of the problem before anybody has read it.

**File what the branch will not fix.** It predates the diff, or it lands in files the diff does not
touch, or answering it needs a decision the review is not in a position to take.

**Fix what the branch introduced, in the branch.** Then name it in the PR comment rather than in an
issue.

**File nothing the compiler, detekt or a test already catches.** Those have a signal of their own,
and an issue duplicating it goes stale the moment the build does its job.

## Checking a finding

**One Opus agent per finding, and they all go out together.** Each gets its own `Agent` call with
`model: "opus"` and `subagent_type: "general-purpose"`, in a single message so they run at once. A
review hands over several findings, and checking them in sequence spends wall-clock on work that
does not interact.

**The agent verifies and searches; it files nothing.** Hand it the finding, the PR or branch the
review ran on, and this:

```
Verify one code-review finding against `main` in this repository, and answer about it alone.

<the finding, as the review stated it>

1. Is it real? Read the code as it stands on `main`, not the diff the review ran on. Name the
   file and line behind every claim you keep, and say which claims you could not substantiate.
2. Has later work already answered it? A finding can be true of the diff and stale against the
   tree. Read `git log` over the paths it names for a commit that changed the mechanism.
3. Is it already filed? `gh issue list --state all --search "<symbol or symptom>"`, open and
   closed both. A closed one may mean it was fixed, or that it was refused — say which.

Answer with: a verdict of REAL, PARTLY REAL, STALE or NOT REAL; the evidence as `file:line`;
the number of any existing issue; and, for PARTLY REAL, which claims survive and which do not.
Do not edit a file and do not open an issue.
```

**Draft only what survives, and say what did not.** A REAL finding with no issue of its own is
written up below. A PARTLY REAL one is written up as the part that survived, never as it was
reported — #480 is the shape this catches, where two of three claims had been answered by #488 four
weeks after it was filed. A STALE or NOT REAL one is reported to the user and dropped. One already
filed gets a comment on that issue carrying what this review added, not a second issue.

## The body

A title and four sections, and no others. The review saw the symptom, not the design — a `## Files`
table, a proposed fix, a `## Verification` list and an `## Out of scope` section belong to whoever
settles the design, and a review's guess at them is read as a specification it has not earned.

**The title is a declarative sentence naming the failure and its consequence.** Not the subsystem,
not the answer.

```markdown
A client's inherited default scopes skip the audience check, so a scope restricted to another
audience can be granted
```

**The provenance says which review found it, and whether it is a regression.** Put it at the top
when it carries an argument — what the reviewed change contributed, why the rule is newly visible —
and at the bottom, after a horizontal rule, when it is bare attribution. Say so explicitly when the
defect predates the change: *"Neither half is a regression — both predate it."*

**What is claimed quotes the `docs/` sentence the code breaks.** Link it as a permalink to the file
on `main`. This is what makes the finding a breach rather than an opinion; where no standard governs
it, quote what the code itself promises instead — the KDoc, the error code, the key.

**What happens names the mechanism in real symbols.** The class, the function and the line, the call
chain from the entry point to the failure, and the end state it leaves. Quote the five or fewer
lines that carry it, and say what the failure looks like from outside — silence, a wrong answer, a
wait — because that is what somebody will one day search for.

**How to reproduce is written only when the sequence causing it is clear and fully known.** The
configuration, the request, the order of two operations: enough that somebody else reaches the same
state. Where the review did not establish it, the section is absent — a half-known reproduction is
read as a known one, and a concurrency window or a throw nobody observes often has none that can be
written. *What happens* already carries the call path.

## Filing

**Label `bug`, and open it in the earliest open milestone.** Read them rather than assuming, since
the earliest moves as releases ship:

```sh
gh api repos/:owner/:repo/milestones --jq '.[].title'
gh issue create --label bug --milestone <milestone> --title "<title>" --body-file <file>
```

**Draft the body to the user and file on their word.** The issues here are written by hand, in
prose, and a review-filed one is no different — it is drafted for the author, not published over
them.

## Closing the loop

**Comment on the reviewed PR with what was filed and what was fixed in it instead.** One line per
issue saying what it is, then a line naming what the branch absorbed, so the review's disposition is
legible from the PR itself. The comment on #462 is the model.

## What this does not cover

**Producing the findings.** What the review looks for and how it reports is `/code-review`'s
question; this starts from the findings it hands over.

**An enhancement.** A design gap wants the argument and the rejected alternatives that a designed
issue carries, and it is written by hand rather than filed from a review.
