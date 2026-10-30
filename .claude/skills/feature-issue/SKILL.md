---
name: feature-issue
description: Design a SympAuthy feature issue — a new feature, or an improvement to one that
  exists — through a discussion with the user, then draft it and file it. Use when the user presents
  an idea for what the server should do and wants it written up as an issue.
---

# Designing a feature issue

The user brings an idea, and a discussion turns it into an issue. The issue is written to [the
issue standard](../../../docs/standards/issue-standard.md#feature): it describes what a person, a
client or an operator observes, and nothing of the code, which is renamed and moved long before the
issue stops being read.

## Before the first question

**Read the issue standard, and the documents in `docs/` the idea touches.** The standard says what
the issue will carry; the design documents give the vocabulary to discuss it in, and the sentences
a decision cites.

**Search the tracker for the idea, open and closed, and say what it holds.** An open issue may
already settle some of the questions, and a closed one may have refused the idea:

```sh
gh issue list --state all --search "<the behaviour, in a few words>"
```

**Read the code only to learn what the server does today.** Restate what it shows as behaviour; no
class, method, table, file or line reaches the discussion or the issue.

## The discussion

**Review the idea before asking anything.** Restate it as behaviour seen from outside — who
observes it, when, and what they are answered — so the user corrects the reading before the
questions build on it.

**Ask about what the issue would otherwise leave unclear.** Number the questions, ask a few at a
time with the most structuring first, and put a recommendation and its reason beside each.

| Subject | Ask about |
| --- | --- |
| who observes it | the person, the client or the operator, and on which surface |
| today | what they observe now, and why it falls short |
| decisions | each choice the behaviour rests on, and the options it lies between |
| configuration | each key a deployment writes, what it accepts, its default, and its refusal |
| errors | what a caller is refused with, named by its code |
| deployments running | what the change alters for a deployment already in production |
| public documentation | what the change impacts there |
| scope | what is left out, and where it goes |

**Answer each reply before asking more.** Say what it settles, where it departs from a sentence in
`docs/`, and what it newly leaves unclear; then ask the next questions.

**Keep the discussion going until the user says to draft.** Write no draft before then, even once
the questions run out — say they have, and wait.

## The draft

**Draft to the standard's feature sections, in chat.** Each decision the discussion settled heads a
section, each option the user turned down goes under *What was considered and not chosen* with the
reason given, and nothing goes in that the discussion did not settle.

**A remark on the draft goes back to the discussion.** Answer it, ask what it opens, and draft
again only when the user says to.

## Filing

**File once the user accepts the draft without a remark.** Label it `feature`, open it in the
earliest open milestone, and give the user its link:

```sh
gh api repos/:owner/:repo/milestones --jq '.[].title'
gh issue create --label feature --milestone <milestone> --title "<title>" --body-file <file>
```

## What this does not cover

**An enhancement or a bug.** An enhancement names the shape it gives the code, which this keeps out
of the issue; a bug a review turns up is the `review-followup` skill's.

**The documentation issue.** It is opened once the feature is closed, from what shipped, as the
standard says.

**Writing the design into `docs/`.** The argument that survived goes there with the change that
builds it, not with the issue.
