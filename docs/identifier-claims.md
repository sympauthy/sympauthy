# The identifier claims

A deployment says what it identifies a person by. `auth.identifier-claims` names the claims someone
signs in with, and it is a list: an address, or an address and a phone number, or whatever else a
deployment decides a person is known by. Most name one, and the list then reads as a formality.

It is not one. With a second claim configured, every question about an identity has two possible
shapes — all of the values, or any one of them — and they stop agreeing. The wrong shape is not a
failure that shows: it answers, and the answer is wrong only for the accounts holding one of the
values and not the rest.

This document says what a deployment may declare, what it means for a value to belong to one
account, when two values somebody typed are one value and how a row is found by one, which of three
reads a caller wants, and what a sign-up has to collect before an account exists. When the
uniqueness of an account being signed up is settled is [the provisional
user](provisional-user.md); who may read and write one of these claims is [security](security.md).

## The set a deployment declares

**An account signs in with any one of its identifier claims, never with all of them at once.** A
person types a single value, and it is matched against every claim in the set — the address reaches
the account, and so does the phone number.

**Which claim matched is not remembered.** What a sign-in produces is an account: somebody who
signed in with their number is the person who signed in with their address.

**The set belongs to the deployment, not to an account or an audience.** It is declared once, and
[a restriction on one is refused at startup](security.md#what-a-restriction-means): an audience that
could not see an identifier claim would lose its sign-in rather than be told it had.

**A type that cannot identify a person may not be named in it.** `ClaimDataType.canIdentify` is the
criterion and its KDoc is the authority: a `boolean`, a `date` or a `timezone` names a property a
whole population shares, and `AuthConfigValidator` refuses one at startup.

## One value, one account

**A value resolves to one account, and that is the whole of the rule.** A login is a single value
matched against every claim in the set, so two accounts holding one value — under the same claim or
under different ones — make it resolve to either of them.

**The crossed pair is what that costs, and it is a sign-in against the wrong account.** Let one
account hold `email = a` and `preferred_username = b`, and another hold `email = b` and
`preferred_username = a`: typing `a` matches a row of each, and the person who owns `a` is resolved
to the other account and has their password checked against it. Neither account is malformed on its
own, which is why the rule ranges over the whole set rather than over each claim in it.

**Nothing in the schema says it.** `collected_claims` holds a row per claim, so the rule ranges over
rows of one column and over several columns at once, and a unique index expresses neither.

**One account holding one value under two of its own identifier claims is not that, and is
allowed.** Both rows carry the same user, so a login over that value resolves to that account
whichever of them the read picks: every row the account already holds is its own, and only another
account's row is a conflict.

**The rule sees committed rows only.** Two sign-ups may therefore hold one value at a time, and
which of them keeps it is settled when the first one
[promotes](provisional-user.md#when-uniqueness-is-settled).

## When two values are one value

**A value is stored as the person wrote it and compared folded.** `collected_claims.value` is what
every reader is answered with — somebody who capitalised their own name keeps that — and nothing
rewrites it so that a comparison can be written.

**The folded value is the cleaned value as plain text, lowercased.** Cleaning belongs to the claim's
data type and is `ClaimValueValidator`'s — an `email` trimmed, a `phone_number` in E.164, a `number`
read as a `Long` — and `ClaimValueMapper.toFoldedValue` folds what comes back.

**Plain text and not the encoding a claim is exchanged in, so that one typed thing is one identity
across types.** `value` holds JSON, which spells `42` under a `number` claim and `"42"` under a
`string` one; folding those apart would let one typed `42` reach a row of one account under the
first and a row of another under the second, which is the crossed pair again.

**A value is cleaned under each claim before it is folded.** A `number` claim reads `0042` as `42`
where a `string` one does not, so a caller offers each claim its own folded value — and a claim that
could hold no such value at all is offered none, and matches nothing, since no row of it holds one
either.

## How a row is found

**A row is found by `collected_claims.folded_equality_hash`, and the name is the whole of what it
answers.** Equality, under folding, and nothing else: it does not order, it does not prefix-match,
and nothing publishes it.

**It is an integer, so the index and the comparison are one width whatever the value's length.** A
claim may hold a long document and none of it is indexed or compared, and an integer is spelled the
same by every dialect where a `LOWER(...)` comparison is not.

**Every claim carrying a value carries the hash, and the index covers every claim.** Neither of them
depends on the set a deployment declares, so a claim added to `auth.identifier-claims` is looked up
against the rows written before it — [the design
FAQ](design-faq.md#is-the-identifier-lookup-indexed-over-the-claims-a-deployment-identifies-by)
holds what that costs.

**`LockKey.IdentifierValue` takes the folded value and hashes a stripe of its own from it.** The
lock and the check therefore name one thing, which a comparison living inside a query could not — a
lock hashes a string and never sees one.

**A row the hash selected is a candidate, and the caller re-checks the folded value.** Eight bytes
are a birthday search away from a deliberate collision, and one that went unchecked would resolve a
provider sign-in to a stranger's account and merge the two.

**The re-check recovers the fold from what the row publishes** — `ClaimValueMapper` reads the value
column back and folds it — rather than storing the fold a second time. `UserManager` is where the
hash is answered and the fold confirmed, for every one of the reads below.

**The mapping is fixed for the life of the schema.** A row is found by it and by nothing else, so
two versions disagreeing about it makes an account unreachable rather than merely slow, and
`ClaimValueMapperTest` holds it to the values it answers today.

## Three reads, three questions

**Which account is this, does one account hold all of these, and is this value free are three
questions and three reads.** They coincide exactly when the set holds one claim, which is what makes
reaching for the wrong one easy and what keeps the mistake invisible until a deployment adds the
second.

**Which account holds this one value is `UserManager.findByAnyIdentifierClaimValue`.** It is the
sign-in read: one value somebody typed, folded once per claim, and a row of any of them answers.

**Which account holds *every* one of these values is `findByIdentifierClaims`.** Matching all of
them is the point, because the answer has to be one account rather than a choice between several,
and it is what a provider sign-up merges on — where a wrong answer attaches a stranger's
third-party identity to somebody's account.

**Whether *any* account holds *any* of these values under *any* claim of the set is
`findTakenIdentifierOrNull`.** It is [the rule above](#one-value-one-account), and an account
holding one of the offered values and none of the others owns the identity just the same — which is
precisely what the two resolving reads answer nothing about.

**The uniqueness read answers what was taken and who holds it, and raises nothing.** What to say
about a value being taken belongs where it was being claimed, and which account holds it reaches an
operator and never the person refused — [the exception
standard](exception-code-standard.md#a-code-names-two-messages) is why.

**A caller holding an account of its own names it, and every row that account already holds is
exempt.** A value it holds resolves to it under whichever claim the row sits, so there is no pair to
exclude; a caller whose account does not exist yet names none.

**Every writer that makes a value an account's asks the third question at the moment it claims it**,
and answers for the refusal itself. They are not listed here: a census stops being true the next
time one is added, which is [the comment standard's](comment-standard.md) rule and holds of a
document as much as of a KDoc.

## Collecting them at sign-up

**A sign-up collects every claim in the set, each of them carrying a value.** One arriving without
is refused on the step that collects it — recoverably, naming the claim — and no account is written:
the person fills the field in and posts again.

**A claim submitted blank is one being cleared, and an identifier claim cleared is a missing one.**
Emptying a field is what a blank submission means everywhere else, and an account created holding no
value for a claim it is identified by holds a row no login matches. Nobody reaches it again, and
nothing collects it either: it is committed, so it is not the abandoned account [the
cleaner](provisional-user.md#collecting-one-that-never-will) sweeps.

**The step collecting the value is where those two readings part, so the refusal is there.** What
validates a value is handed one claim and one value and is told nothing of which claims the
deployment identifies by; what promotes the account answers after it is written, at the end of a
flow where the person has no step left to correct.

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

**Whether an account may hold only some of the set.** A sign-up collects every one of them, and the
two provider paths disagree by accident of what each of them writes rather than by a rule. Settling
it is a question about which claims a deployment requires, and about the accounts a deployment that
adds one to the set already holds.

**Two values that differ in Unicode rather than in case.** An address whose domain is non-ASCII, or
whose local part differs only by normal form, is two values here. Settling it is a punycode and
normal-form decision of its own.

**Changing the identifier an account signs in with.** An account takes its identifier claims at
sign-up and keeps them; why no surface writes one afterwards is
[security's](security.md#what-this-design-does-not-do).

---

← [Design documentation](index.md)
