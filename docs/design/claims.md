# The claims

A claim is something this server knows about a person: a name they typed, an address a provider
asserted, a score an application computed. A deployment declares which claims exist, and everything
else about one follows from that declaration — whose it is, who may read and write it, which
audience has it, and which channel carries it off this server.

This document is those four questions, in that order. Between the audience and the channel it says
what a deployment writes to declare a claim, and what is known about a value it holds. What a
deployment identifies a person *by* is [the identifier claims](identifier-claims.md); a value
written by a sign-up that has not finished is [the provisional user](provisional-user.md); the
authentications, scopes and gates every rule here is enforced through are [security](security.md).

`Claim`, with the `ClaimAcl` it carries, is what a declaration is parsed into and the model every
rule below is a method of; its KDoc is the authority on how the two halves of the ACL combine. A
value collected for an account is a `CollectedClaim`, and `ConsentAwareCollectedClaimManager` is the
read and the write that apply the ACL to one.

## Kinds

**A claim is the person's or an application's, and it says which.** `kind: personal` or
`kind: application` declares it, the `openid` [template](#templates) declares the first and the
`default` one the second, and a claim overrides its template like any other key. The kind is
designed and not yet built; [the design
FAQ](design-faq.md#is-a-persons-profile-one-value-for-every-audience-or-one-per-audience) holds the
options that lost to it.

**The kind is not the origin.** `ClaimOrigin` says whether a claim's name is the specification's or
the deployment's own, and the administration API publishes it as `openid` or `custom`. A
deployment's own claim can be the person's and a standard-named one can be an application's, so the
name's provenance never said whose the value was, and access control is decided by the ACL and the
kind rather than by the origin.

### A personal claim

**A personal claim is the person's.** A name, an address, a birth date: collected from the person in
the flow, disclosed to a client once they agreed to a scope, and about them in the way nothing an
application computes is. An identifier claim is a personal claim because it is what the account
signs in with, and declaring one an application's is refused.

### An application claim

**An application claim is an application's, attached to the person.** A credit score, a tier, a flag
a backend computes: the person never types it, because a backend answers for the value. It may still
carry a consent scope, where the person has to agree before a client is told their score — consent
gates disclosure, and says nothing about whose the value is.

### A generated claim

**A generated claim is computed, not collected, and is neither kind.** `sub` and `updated_at` are
answered from the account rather than from a row, belong to every audience, and are written by
nobody. A key written on one is refused at startup, and
[the design FAQ](design-faq.md#why-is-a-key-written-on-a-generated-claim-refused-rather-than-applied-or-ignored)
says why refusing beat applying or ignoring it.

## Who may read and write a claim

**The ACL answers in two halves: what the person's consent unlocks, and what a client's own scopes
unlock regardless of it.** The consent half names a scope and says, per party and per direction,
whether agreeing to it opens the claim; the unconditional half names the client scopes that read or
write the claim whatever the person agreed. `Claim` is where each question is a method, and nothing
here restates a formula those methods already are.

**The credential a party holds is half the question, and [the kind](#kinds) is the other half.** A
door says what this caller was authorized to do; the kind says whether the value was ever theirs to
set. So each party below is answered once per kind, and the reads and the writes part company there.

### The person

**The person reaches their own claims through two doors, and only one of them is presence.** In the
flow they are on this server's own pages having just authenticated, which is the one caller nothing
has to be proven about. A bearer access token is the other, and it says only that a client was
authorized to act within some scopes for as long as a refresh token lets it — a month under the
shipped configuration — and nothing at all about the person being there.

#### A personal claim

**The flow collects what the consent half marks writable by the person, and `/userinfo` answers what
it marks readable.** The read is consent alone, because that endpoint is not client-authenticated.

**The person's own token writes through a permission of its own.** A claim it may write is marked
separately from the one the flow collects, is off unless a deployment marks it, and the surface that
honours it is the user surface; both are designed and not yet built. Honouring the flow's flag
instead would have made every profile claim writable through an API on the day of the upgrade, with
nobody asked.

**A write through that door may ask how recently the person authenticated.** A deployment marks a
claim with the age an authentication may have behind a write, and a surface presented with an older
one refuses with the challenge RFC 9470 defines, telling the client to re-authorize with `max_age`.
A read is never challenged. What a token says about when the person authenticated is
[security](security.md#tokens), and the marking is designed and not yet built.

#### An application claim

**It is never the person's to write, through either door.** Neither the flag the flow reads nor the
one the token reads may be set on it, so no flow ever asks somebody to type their credit score and
no token ever sets one; a declaration doing so is refused at startup.

**The person reads one where the ACL says so.** Seeing one's own score is disclosure, and disclosure
is the consent half's question whatever the value is.

### A client

**A client reads and writes by the person's consent or by its own scopes, and the two paths are the
ACL's two halves.** The consent path answers a client the person agreed to disclose to; the
unconditional path answers a client holding a scope of its own, `users:claims:read` or
`users:claims:write`, whatever the person agreed. The `default` template grants both
unconditionally, which is what makes a claim declared in a line an application's; the `openid`
template grants the consent path alone and no client write.

**The two OpenID channels do not gate alike.** `/userinfo` asks whether the person may read the
claim, which is consent alone, because that endpoint is not client-authenticated; the id token asks
whether the client may, which consent *or* the client's own unconditional scopes satisfy. A claim
can therefore be permitted in one and refused in the other before publication is asked at all.

**Either way, a client write reaches no further than the deployment drew it.** For an application
claim the deployment drew it wherever it put the claim; for a personal claim it is the client's own
audience, which is the isolation a deployment with several audiences wants, obtained without a
second population of accounts. Which of its audience's claims a client may name at all is [writing a
claim](#writing-a-claim).

#### A personal claim

**A client writes one only where it is restricted to the client's own audience.** A client of one
audience setting `name` would be choosing what every other audience is told a person is called, over
the person's head and with the person never asked; restricted to one audience, the value leaves this
server to that audience alone, and the clients of an audience already trust each other with what it
owns. A shared personal claim granting a client write is refused at startup, naming the claim and
the key.

**Reading one is the ordinary ACL question.** A client is told a personal claim of its audience by
consent or by its own scopes like any other, so the restriction above is about the write alone.

#### An application claim

**A client writes one wherever the deployment put it.** The value is the application's and the
deployment chose which applications have it, so an application claim left shared is one every
audience's backend may read and write — which is what leaving it shared means.

### An administrator

**An administrator reads every claim of every audience, whichever kind, and writes none.** An
administrator answers for the deployment rather than for one of its applications, so the audience a
claim is restricted to is something they are shown rather than something that hides it from them —
the one reader [the audience rules](#reading-a-persons-claims) exempt.

**The kind decides nothing here, because it decides who writes.** An application claim is its
application's to write and a personal claim is the person's, so neither is an operator's, and the
administration surface opens no claim write at all.

## Which audience has a claim

An audience is the set of applications entitled to a claim, and nearly every rule below keeps two
things straight: which audience a claim belongs to, and which audience a read or a write is made
for.

### What a restriction means

**A claim may be restricted to one audience, and it then leaves this server only to that audience.**
`Claim.audienceId` names the restriction and `Claim.belongsToAudience` is the whole of the test: a
claim naming no audience is every audience's, and a claim restricted to one is answered to no other.

**A generated claim belongs to every audience.** `sub` and `updated_at` are computed rather than
collected, the parser gives them no audience whatever the configuration says, and nothing about a
person is disclosed by either.

**An identifier claim belongs to every audience, and restricting one is refused at startup.**
`auth.identifier-claims` is declared once for the deployment, so every audience signs people in with
the same set of claims — what that set means, and how a person signs in with any one of them, is
[its own document](identifier-claims.md). A restriction on one would be filtered out of the reads
that resolve an account, and a deployment would lose its sign-in rather than be told; the validator
names it instead.

### Reading a person's claims

**A read of a person's claims names the audience it is for, and the audience is not optional.**
Consent is recorded per user and audience, so a set of consented scopes is always some audience's,
and whoever holds them knows which. A reader taking the scopes and not the audience would be
modelling a state that cannot arise.

**What a client is told about is its own audience, resolved from the credential.** The id token, the
`/userinfo` response and the client API each take it from the client that authenticated, never from
anything the request carried, because a client belongs to exactly one audience.

**The audience is the one the decision lands in, which is not always the caller's own.** An
authorization grants scopes to the client that started the flow, so the flow's audience is the
flow's own; an act-as token is issued for the audience the exchange names, which may be neither the
acting client's nor the default it falls back to. Read the target's claims, not the asker's.

**The audience an interactive flow works in is the one its authorization is for**, the audience of
the client that started it. It decides the whole of what that flow does with claims: which it offers
to collect, which it accepts, which it holds as required, and which it asks a person to confirm with
a validation code. Someone signing in to one audience is neither asked for another's claims nor held
to them.

**A configured rule sees the audience's claims, and consent is the only thing it sees past.** What a
rule may branch on and what a person agreed to disclose are different questions, so a rule runs on
claims regardless of consent — but one keyed on a claim restricted to another audience decides from
a value it may not be told, and what it decides leaves the server: a granted scope, an act-as token.
The authorization webhook is handed the same claims and posts them off this server outright.

**Those claims are read for one audience rather than read whole and narrowed after.**
`CollectedClaimManager.findByUserIdAndAudience` is that read, and it applies no consent — the
audience is a different question from what a person agreed to disclose.

**The administration surface reads across every audience, and it is the only reader that does.** An
administrator answers for the deployment rather than for one of its applications, so the audience a
claim is restricted to is something they are shown rather than something that hides it from them.

### Writing a claim

**A client writes only its own audience's claims, and naming another's is refused rather than
ignored.** A restriction enforced on the way out alone would let a client set what it is not allowed
to read — choosing what another audience is told about a person while never being accountable for
the value. Which of its own audience's claims a client may write at all is
[the kinds'](#kinds) question: an application claim wherever the deployment put it, and a personal claim
only where it is restricted to that audience.

**An invitation pre-assigns only its own audience's claims, and an administrator is held to that
too.** An invitation names the audience it is for and is consumed by a client of that one alone, so
a claim restricted to another is a value chosen for an audience nobody asked and never read back by
the flow that writes it. A bootstrap invitation is refused at startup rather than at creation,
because the file it is written in is what the deployment is being told about.

**No client writes an identifier claim, whatever its scopes.** An identifier is what an account
signs in with, and nothing in a claim write verifies the value it stores — so a client able to set
one could repoint an account's sign-in at an address it holds, with nobody asked and nothing sent.
The scopes that let a client write a claim say what it may record about a person, not what that
person signs in as. The client surface refuses one by name rather than dropping it, and the manager
behind it leaves it out the same way the interactive flow already does. Why no surface changes one
afterwards is [security's](security.md#what-this-design-does-not-do).

## Configuration

**A claim is declared under `claims.<id>`, and the id is the name its value travels under.** It is
the key a channel publishes it under, the key a client names to write it and the key
`auth.identifier-claims` reaches for, so it is chosen once and not renamed afterwards. What each key
means and accepts is
[the public documentation](https://sympauthy.github.io/technical/configuration/claim); what this
document holds is what a declaration is for.

**The type is declared on the claim and can be declared nowhere else.** It decides the form a value
takes on the wire, whether a value may identify a person, and how a value is cleaned before two of
them are compared — so it is the claim's own, and a template carrying one would be deciding those
three for claims it knows nothing about.

**A declaration that cannot mean anything is refused at startup, naming the claim and the key that
cannot mean it.** A server whose configuration is wrong refuses to report itself ready, so the
deployment is told in the file it owns rather than by a client's error log once a value fails to
come back. `ClaimsConfigValidator` is the authority on which refusals there are.

### Templates

**A template under `templates.claims.<id>` carries defaults, and a claim naming it overrides
whichever of them it declares for itself.** Everything a claim declares may be defaulted that way
except the three that cannot be shared: its type, the template it names, and the companion saying
its value was verified.

**A claim naming no template takes `default`, and naming that one explicitly is refused.** The
fallback is what makes `default` the shape of every claim a deployment declares in a line or two,
and a claim asking for it by name is asking for what it already has. A claim naming a template that
does not exist is refused too, and told which it could have named.

**A template takes no template of its own.** One level resolves, so reading a claim means reading
its own keys and one template's, and nothing walks a chain to find out what a claim is.

**A template's allowed values are read under each claim's own type.** The list is written once and
the type belongs to the claim, so the same line admits `42` under a `number` claim and `"42"` under
a `string` one — which is also why the values are converted once per type rather than once per
claim.

**The server ships two.** `openid` is the one every claim the specification defines names: it
carries both publication channels and the consent flags a profile claim wants, and it holds them
disabled, so a deployment turns on the ones its applications ask for rather than serving the whole
set. `default` is the one a claim naming none takes, and it grants the client scopes that read and
write a claim whatever the person consented to — which is what makes a claim declared in one line an
application's.

## A value, and what is known about it

**A value is held once per account and claim, as it was given.** `collected_claims` holds a row per
claim an account has a value for, and the row answers every reader with the value as the person or
the writer spelled it. How two spellings compare, and how a row is found by a value, is
[the identifier claims](identifier-claims.md#when-two-values-are-one-value).

**A value leaves this server as the type the deployment declared.** `ClaimDataType` is the set of
types, its KDoc the authority on what each admits, and
[the design FAQ](design-faq.md#what-form-does-a-claims-value-take-once-it-leaves-this-server) holds
why the declared type rather than the value's own decides the wire form.

**A claim may name a companion saying its value was verified, and the companion travels only beside
a value.** `email_verified` beside `email` is the specification's own case, and a deployment's claim
declares one the same way. The flow proves a value by sending a code to it, and a channel claiming
the companion true beside no value would be this server asserting it verified something it did not
send.

**A required claim holds up the flow of an audience that has it.** A person signing in to an
audience is asked for the required claims that audience has, and for no other audience's; what the
flow does with them once collected is [the interactive flow](interactive-flow.md).

## Where a claim is published

**A claim names the OpenID channels its value travels through, and publication is not permission.**
`claims.<id>.published-in` names them, `Claim.publishedIn` holds them and `Claim.isPublishedIn` is
the whole of the test. The ACL answers whether a caller may know a value at all; this answers which
channel carries one they may already know, so it only ever narrows. It is never a grant, and a claim
the ACL refuses is answered in no channel whatever it names.

**It reaches the two OpenID channels and nothing else.** The id token and `/userinfo` each filter
what they were handed, while the client, admin and user APIs are gated by the ACL alone and answer
whatever it permits — so a claim published in neither channel is still read through those. The
access token is not a channel: it carries the client, the scopes and the binding, and no attribute
of a person.

**A claim published in neither channel is not advertised as one either.** `claims_supported` lists
what a client could be told through OpenID Connect, and a claim no channel carries is a name no
client can ever obtain a value for. That is the other direction from [being advertised and being
served](security.md#scopes): a deployment stays free to serve what it does not list, and this stops
it listing what it cannot serve.

**A claim naming no channel is published in none.** A value leaves this server through the channels
a deployment named and through no other, so a claim its file never mentions reaches neither. The
shipped `openid` template names both, which is what keeps the claims the specification defines
travelling where a client expects them without every deployment writing it again.

**The silent answer is the withholding one, deliberately.** Publishing by default would put a
deployment's own claim into every id token a client may read on the strength of a line nobody wrote,
and nothing downstream would report it; withholding by default keeps back a value somebody meant to
send, which the deployment sees the first time it looks and fixes in the file it already owns.

**The administration API is where it looks.** `published_in` on the claim resource lists the
channels a claim travels through, and lists none where it travels through neither, so an operator
reads what their deployment publishes off the surface built for them rather than by decoding a
token. Publication is the part of a claim with no other reader — what the ACL permits shows up in
the consent a person is asked for, and where a value goes shows up nowhere else.

**`/userinfo` carries a claim it declares no property for.** `UserInfoResource` lists the
properties the specification names, and a deployment's own claim is serialized beside them out of
the claims naming `userinfo`. What it may carry is still what the person may read, consent alone,
and the shipped `default` claim template leaves that false.

**A generated claim's channels are recorded rather than configured.** `sub` and `updated_at` are
computed rather than collected, so neither channel reads them out of the claims it filters — the id
token claims the subject itself and the `/userinfo` mapper computes both. The enum still states
where each one arrives, because what the discovery document says a channel can supply is read off
it: `sub` reaches both and `updated_at` only `/userinfo`, which is what the id token has always
carried.

**Nothing refuses a configuration that makes a large id token.** What an audience publishes is
what the token carries, and no ceiling is imposed on that — [the design
FAQ](design-faq.md#should-a-configuration-that-makes-a-large-id-token-be-refused) holds what that
costs and where the limit actually bites.

## What this document does not settle

**Where a client may ask for a claim to be delivered.** OpenID Connect Core §5.5 defines the
`claims` request parameter, which asks for a named claim in `id_token` or in `userinfo`
specifically, and this server implements neither it nor the `claims_parameter_supported` that would
advertise it — so the discovery document tells a client it may not ask.
[Where a claim is published](#where-a-claim-is-published) is the deployment deciding instead, for
clients that ask for nothing, which is every client today; whether a client should get a say is
open.

**A claim holding a structured value.** `ClaimDataType` holds scalars, and a deployment wanting to
keep a document about a person on this server has no type for it. Whether one is added, and what the
ACL and the channels make of a value with an inside, is open.

**Whether an audience's own personal claim should be client-writable at all.** The kind leaves a
restricted one where a deployment puts it; whether an application should ever set what a person
would otherwise type, even for its own audience, is the deployment's call and is not decided here.

**Changing the identifier an account signs in with.** An account keeps the identifier claims it was
given, and why no surface writes one afterwards is
[security's](security.md#what-this-design-does-not-do); proving a new one is a flow of its own, and
[the identifier claims](identifier-claims.md#what-this-document-does-not-settle) leave it open too.

---

← [How the system works](index.md)
