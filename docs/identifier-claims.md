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

## One spelling, and the claim's type decides it

**A value is compared exactly as `collected_claims` stores it, so two spellings are one identity
only where one of them is what got stored.** Nothing folds anything at comparison time: the rule
above is an equality, the read resolving a login is an equality, and `LockKey.IdentifierValue`
hashes the stored string.

**Which spelling that is belongs to the claim's data type, and `ClaimValueValidator` is where each
type says so.** An `email` is trimmed and lowercased, a `phone_number` is E.164 and a `date` is
`yyyy-MM-dd` before anything asks, and a `string` is kept as it was typed — whether two
capitalisations of a username are one person is a deployment's policy rather than this server's.

**An address is folded whole, local part included.** RFC 5321 leaves that half to the receiving
host, but no mail provider a deployment will meet delivers `Alice@x.com` and `alice@x.com` to two
mailboxes, and a server honouring the distinction would refuse to recognise somebody who
capitalised their own name at sign-in.

**Every writer of an identifier value goes through that validator**, including the ones taking a
value from a provider rather than from the person it belongs to. A row written around it holds a
spelling nothing else here will ever match.

**A read cleans the value it offers, once per claim, the way that claim cleans what it stores.** One
login becomes a `(claim, value)` pair per identifier claim rather than one string matched against
the set, because folding a single spelling for all of them would reach a genuinely capitalised
username by a login that is not it. A claim that could hold no such value at all is offered none,
and matches nothing: no row of it holds a value that claim would have refused.

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
