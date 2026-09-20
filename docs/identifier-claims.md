# The identifier claims

A deployment says what it identifies a person by. `auth.identifier-claims` names the claims someone
signs in with, and it is a list: an address, or an address and a phone number, or whatever else a
deployment decides a person is known by. Most name one, and the list then reads as a formality.

It is not one. With a second claim configured, every question about an identity has two possible
shapes — all of the values, or any one of them — and they stop agreeing. The wrong shape is not a
failure that shows: it answers, and the answer is wrong only for the accounts holding one of the
values and not the rest.

This document says which shape each question has: how one typed login reaches one account, what
makes a value unique across the set rather than within a claim, and why the read that resolves an
identity is not the read that says whether one is free. When the uniqueness of an account being
signed up is settled is [the provisional user](provisional-user.md); who may read and write one of
these claims is [security](security.md).

## Signing in with any of them

**An account signs in with any one of its identifier claims, never with all of them at once.** A
person types a single login, and it is matched against every claim in the set — the address reaches
the account, and so does the phone number.

**Which claim matched is not remembered.** What a sign-in produces is an account. Somebody who
signed in with their number is the person who signed in with their address, and nothing downstream
is told which it was.

**The set belongs to the deployment, not to an account or an audience.** It is declared once, and
[a restriction on one is refused at startup](security.md#what-a-restriction-means): an audience that
could not see an identifier claim would lose its sign-in rather than be told it had.

## One value, one account

**A value resolves to one account, and that is the whole of the rule.** A login is a single value
matched against every claim in the set, so two accounts holding one value — under the same claim or
under different ones — make it resolve to either of them.

**The crossed pair is what that costs, and it is a sign-in against the wrong account.** Let one
account hold `email = a` and `preferred_username = b`, and another hold `email = b` and
`preferred_username = a`. Typing `a` matches two rows belonging to two accounts; the read returns
one of them, and the person who owns `a` is resolved to the other account and has their password
checked against it. Neither account is malformed on its own, which is why the rule has to range over
the whole set rather than over each claim in it.

**Nothing in the schema says it.** `collected_claims` holds a row per claim, so the rule ranges over
rows of one column and over several columns at once, and a unique index expresses neither.
`UserManager.findTakenIdentifierClaimIdOrNull` is the whole of it, and its KDoc is the authority on
what it compares.

**One account holding one value under two of its own identifier claims is not that.** Both rows
carry the same user, so a login resolves to that account whichever of them the read picks. The rule
refuses it anyway — a caller's own row is exempt only under the same claim — which is stricter than
the invariant needs and is [left open below](#what-this-document-does-not-settle).

**The rule sees committed rows only.** Two sign-ups may therefore hold one value at a time, and
which of them keeps it is settled when the first one
[promotes](provisional-user.md#when-uniqueness-is-settled).

## Resolving an identity is not asking whether one is free

**Which account is this, and is this value taken, are two questions and two reads.** They coincide
exactly when the set holds one claim, which is what makes reaching for the wrong one easy and what
keeps the mistake invisible until a deployment adds the second.

**Resolving asks which account holds *every* one of the values offered.** `findByIdentifierClaims`
is that read, and matching all of them is the point: the answer has to be one account rather than a
choice between several. It is what a provider sign-up merges on, where a wrong answer attaches a
stranger's third-party identity to somebody's account.

**Enforcing uniqueness asks whether *any* account holds *any* of those values under *any* claim in
the set.** `findTakenIdentifierClaimIdOrNull` is that read, and it is the rule of the section above.
An account holding one of the offered values and none of the others owns the identity just the same,
which is precisely what the resolving read answers nothing about.

**It names the claim that lost and raises nothing.** What to say about a value being taken belongs
where it was being claimed: the same loss is recoverable at one moment in a flow and not at the
next, and only the caller knows which moment it is standing in.

**A caller holding an account of its own names it, and its rows under that same claim are exempt.**
Rewriting a value it already holds takes nothing from anybody. A caller whose account does not exist
yet names none, and nothing is exempt.

**Every writer that makes a value an account's asks the second question, at the moment it claims
it.** Which writers those are is the rule's own KDoc to say; a list here would be a second copy of
it, and the copy that goes stale.

## What a provider asserts

**A provider link checks whatever subset of the set the provider asserts.** Linking writes no
collected claim, so what was asserted is the whole of what there is to compare, and an account
already holding one of those values owns the identity whichever of the others the provider is silent
about. Requiring every configured claim would leave the check dead in the ordinary deployment — an
address and a number configured, against a provider that carries an address and no number.

**Creating an account from a provider requires every one of them.** Those values are written onto
the account being created, so a partial assertion would leave a partial account behind, which is a
different thing from a partial check.

## What this document does not settle

**Whether an account may hold one value under two of its own identifier claims.** The invariant
above does not forbid it: both rows name the same account, so a login resolves the same either way.
The rule refuses it regardless, and an account in that state can then neither rewrite its own
identifier nor have a provider linked to it. Whether the strictness earns what it costs is
undecided.

**Whether an account may hold only some of the set.** Nothing decides it, and the two provider paths
above disagree by accident of what each of them writes rather than by a rule. Settling it is a
question about which claims a deployment requires and what the flow collects when one is missing.

**How two spellings of one value compare.** A value is compared exactly as `collected_claims` stores
it, so a difference of case or of surrounding whitespace makes two identities out of one address.
The lock serialising these checks hashes the stored spelling too, so two spellings are not even
ordered against each other.

**Changing the identifier an account signs in with.** An account takes its identifier claims at
sign-up and keeps them; why no surface writes one afterwards is
[security's](security.md#what-this-design-does-not-do).

---

← [Design documentation](index.md)
