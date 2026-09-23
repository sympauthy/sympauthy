# The identifier claims

A deployment says what it identifies a person by. `auth.identifier-claims` names the claims someone
signs in with, and it is a list: an address, or an address and a phone number, or whatever else a
deployment decides a person is known by. Most name one, and the list then reads as a formality.

It is not one. With a second claim configured, every question about an identity has two possible
shapes — all of the values, or any one of them — and they stop agreeing. The wrong shape is not a
failure that shows: it answers, and the answer is wrong only for the accounts holding one of the
values and not the rest.

This document says which shape each question has: how one typed login reaches one account, what
makes a value unique across the set rather than within a claim, which spelling of a value all of
that compares on, and why the read that resolves an identity is not the read that says whether one
is free. When the uniqueness of an account being signed up is settled is [the provisional
user](provisional-user.md); who may read and write one of these claims is [security](security.md).

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
`UserManager.findTakenIdentifierOrNull` is the whole of it, and its KDoc is the authority on what
it compares.

**One account holding one value under two of its own identifier claims is not that, and is
allowed.** Both rows carry the same user, so a login over that value resolves to that account
whichever of them the read picks. The rule is the invariant and nothing more: every row the account
already holds is its own, under whichever claim it sits, and only another account's row is a
conflict.

**The rule sees committed rows only.** Two sign-ups may therefore hold one value at a time, and
which of them keeps it is settled when the first one
[promotes](provisional-user.md#when-uniqueness-is-settled).

## The value the row publishes, and the key it is found by

**A row holds `value` as the person wrote it, and nothing rewrites it.** It is what every reader is
answered with — somebody who capitalised their own name keeps that. Case is not theirs to lose so
that a query can be written.

**What an identifier is found by is `folded_equality_hash`, and the name is the whole of what it
answers.** Equality, under folding, and nothing else: it does not order, it does not prefix-match,
and it is never published. The folded value it hashes is the cleaned value as plain text,
lowercased.

**Plain text and not the exchanged encoding, so one typed thing is one identity across types.**
`value` holds the JSON a claim's type is exchanged as, which spells `42` under a `number` claim and
`"42"` under a `string` one. Folding those apart would let one typed `42` reach a row of one account
under the first and a row of another under the second, which is the crossed pair of the section
above.

**An integer, so that the index and the comparison cost the same whatever the value's length.** A
claim may hold a long document; none of it is compared, and none of it is indexed. It is also what
lets the key be spelled identically by every dialect, where a `LOWER(...)` comparison is not — and
`LockKey.IdentifierValue`, which hashes a string and never sees a query, could not agree with one.

**A row the hash selected is a candidate, and every caller re-checks the folded value itself.**
Eight bytes of a digest are a birthday search away from a deliberate collision, and a collision that
went unchecked would merge a provider into a stranger's account. `UserManager` recovers the folded
value from what the row publishes — `ClaimValueMapper.toFoldedValueOfStored` — rather than storing
it a second time.

**The fold has one declaration, and so does its hash.** `ClaimValueMapper.toFoldedValue` and
`toFoldedEqualityHash` are they; the entity mapper writes the column from them and
`CollectedClaimManager.getFoldedValueOf` cleans a value under its claim and then asks them. The
mapping is fixed for the life of the schema, because a row is found by it and by nothing else.

**A read offers each claim its own folded value, and a claim that could hold no such value is
offered none.** A `number` claim reads `0042` as `42` where a `string` one does not, so one typed
value is cleaned per claim before it is folded — and a claim that would refuse it matches nothing,
since no row of it holds one either.

**A type that cannot identify a person is refused at startup.** `ClaimDataType.canIdentify` is the
criterion and its KDoc is the authority: a `boolean`, a `date` or a `timezone` names a property a
whole population shares, which is a collision waiting for the second person rather than an identity.

## Resolving an identity is not asking whether one is free

**Which account is this, and is this value taken, are two questions and two reads.** They coincide
exactly when the set holds one claim, which is what makes reaching for the wrong one easy and what
keeps the mistake invisible until a deployment adds the second.

**Resolving asks which account holds *every* one of the values offered.** `findByIdentifierClaims`
is that read, and matching all of them is the point: the answer has to be one account rather than a
choice between several. It is what a provider sign-up merges on, where a wrong answer attaches a
stranger's third-party identity to somebody's account.

**Enforcing uniqueness asks whether *any* account holds *any* of those values under *any* claim in
the set.** `findTakenIdentifierOrNull` is that read, and it is the rule of the section above. An
account holding one of the offered values and none of the others owns the identity just the same,
which is precisely what the resolving read answers nothing about.

**It answers what was taken and who holds it, and raises nothing.** What to say about a value being
taken belongs where it was being claimed: the same loss is recoverable at one moment in a flow and
not at the next, and only the caller knows which moment it is standing in. Which account holds the
value reaches an operator and never the person refused — [the exception
standard](exception-code-standard.md#a-code-names-two-messages) is why.

**A caller holding an account of its own names it, and every row that account already holds is
exempt.** A value it holds resolves to it under whichever claim the row sits, so there is no pair to
exclude. A caller whose account does not exist yet names none, and nothing is exempt.

**Every writer that makes a value an account's asks the second question, at the moment it claims
it**, and answers for the refusal itself. They are not listed here or anywhere else: a census of
them stops being true the next time one is added, which is [the comment
standard's](comment-standard.md) rule and holds of a document as much as of a KDoc.

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

**Whether an account may hold only some of the set.** Nothing decides it, and the two provider paths
above disagree by accident of what each of them writes rather than by a rule. Settling it is a
question about which claims a deployment requires and what the flow collects when one is missing.

**Two spellings that differ in Unicode rather than in case.** An address whose domain is non-ASCII,
or whose local part differs only by normal form, is folded by nothing above. Settling it is a
punycode and normal-form decision of its own.

**Changing the identifier an account signs in with.** An account takes its identifier claims at
sign-up and keeps them; why no surface writes one afterwards is
[security's](security.md#what-this-design-does-not-do).

---

← [Design documentation](index.md)
