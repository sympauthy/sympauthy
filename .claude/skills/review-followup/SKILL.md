---
name: review-followup
description: File a SympAuthy issue for a defect, or a breach of a rule in docs/standards/, that a
  code review
  turned up and the branch under review will not fix. Use after /code-review — or any review —
  surfaces something real that predates the diff or is not that diff's to answer.
---

# Filing a follow-up from a review

A review turns up things the branch under review is not going to fix: a defect that predates the
diff, a rule the diff made visible, a breach whose answer is somebody else's to decide. This files
one of them as an issue, so the finding outlives the review that found it.

## What gets filed

**Only a defect, or a breach of a rule in `docs/standards/`.** A defect is code doing something
other than what it promises — its KDoc, its error code, its configuration key, or the behaviour a
caller is entitled to. A breach is code not conforming to a standard. Nothing else is a finding: a
preference, a shape somebody would have written differently, or a capability nobody has decided to
build is not one.

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

## Already filed

**Search for the finding before anything else, and stop there if it is already filed.** Open and
closed both, by the symbol it names and by the symptom it describes:

```sh
gh issue list --state all --search "<symbol or symptom>"
```

A match ends the work on that finding: no agent checks it and nothing is drafted. Tell the user the
number, and for a closed one whether it was fixed or refused — a refusal is a decision already
taken, and a fixed one is the next thing to read if the finding still looks real. The findings with
no match go on to be checked.

## Checking a finding

**One Opus agent per finding, and they all go out together.** Each gets its own `Agent` call with
`model: "opus"` and `subagent_type: "general-purpose"`, in a single message so they run at once. A
review hands over several findings, and checking them in sequence spends wall-clock on work that
does not interact.

**The agent verifies; it files nothing.** Hand it the finding, the PR or branch the review ran on,
and this:

```
Verify one code-review finding against `main` in this repository, and answer about it alone.

<the finding, as the review stated it>

1. Is it real? Read the code as it stands on `main`, not the diff the review ran on. Name the
   file and line behind every claim you keep, and say which claims you could not substantiate.
2. Has later work already answered it? A finding can be true of the diff and stale against the
   tree. Read `git log` over the paths it names for a commit that changed the mechanism.

Answer with: a verdict of REAL, PARTLY REAL, STALE or NOT REAL; the evidence as `file:line`;
and, for PARTLY REAL, which claims survive and which do not.
Do not edit a file and do not open an issue.
```

**Draft only what survives, and say what did not.** A REAL finding is written up below. A PARTLY
REAL one is written up as the part that survived, never as it was reported — #480 is the shape this
catches, where two of three claims had been answered by #488 four weeks after it was filed. A STALE
or NOT REAL one is reported to the user and dropped.

## The body

**Read [the issue standard](../../../docs/standards/issue-standard.md#bug) before drafting.** What
every issue settles and what a bug carries are written there, and nowhere here.

**It is a bug, and it stops after the diagnosis.** A title, then *What is claimed*, *What happens*
and *How to reproduce*, and no other section. The review saw the symptom, not the design: a proposed fix, a
`## Verification` list and an `## Out of scope` section belong to whoever settles the fix, and a
review's guess at them is read as a specification it has not earned.

**The provenance says which review found it.** Put it at the top when it carries an argument — what
the reviewed change contributed, why the rule is newly visible — and at the bottom, after a
horizontal rule, when it is bare attribution.

## Filing

**Label `bug`, and open it in the earliest open milestone**, as the issue standard says:

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

**A feature or an enhancement.** A design gap wants the argument and the rejected alternatives that
a designed issue carries, and it is written by hand rather than filed from a review.
