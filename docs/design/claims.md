# The claims

A claim is something this server knows about a person: a name they typed, an address a provider
asserted, a score an application computed. A deployment declares which claims exist, and everything
else about one follows from that declaration — whose it is, who may read and write it, which
audience has it, and which places this server publishes it in.

This document is those four questions, in that order. Between the audience and the places it says
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

**A generated claim is computed, not collected, and is neither kind.** `sub`, `updated_at` and
`auth_time` are answered by this server rather than from a row, belong to every audience, and are
written by nobody. A key written on one is refused at startup, and
[the design FAQ](design-faq.md#why-is-a-key-written-on-a-generated-claim-refused-rather-than-applied-or-ignored)
says why refusing beat applying or ignoring it.

**What they share is where the value comes from, not who it is about.** `sub` and `updated_at`
describe the account and are answered for a person wherever this server is asked about one.
`auth_time` describes a single authorization, so there is no value to answer for a person in the
abstract, and the surfaces that list what is known about somebody leave it out rather than
answering null for everybody.

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

**Each door is named for itself, and no flag covers both.** The consent half says separately what
the flow collects, what the person's token reads and what the person's token writes, so the word
*person* on a flag always means the person holding their own access token and the flow is never
one of them. It is the same distinction `ConsentAcl` draws in Kotlin, and the reason it is drawn
at all: one flag spanning two doors is turned on for the near one and read by the far one.

**The flow collects what the consent half marks collected in the flow, and `/userinfo` answers what
it marks readable by the person.** The read is consent alone, because that endpoint is not
client-authenticated.

**The flow reads a person's claims back through the same flag it collects them through.** One door
is one permission in both directions, so the claims the flow offers, the values it shows back, the
required ones it holds a person to and the address it sends a confirmation code to are all one
read. A flow reading them through a client's permission instead would hold a person to a required
set drawn from one flag and look for it collected under another: a claim a deployment collects and
discloses to no client would be asked for again on every pass, and the sign-in would never
complete.

**The person's own token writes through a permission of its own.** A claim it may write is marked
separately from the one the flow collects and is off unless a deployment marks it; the surface that
honours it is the user surface, which is designed and not yet built. Honouring the flow's flag
instead would have made every profile claim writable through an API on the day of the upgrade, with
nobody asked.

**A write through that door may ask how recently the person authenticated.** A deployment marks a
claim with the age an authentication may have behind a write, and a surface presented with an older
one refuses with the challenge RFC 9470 defines, telling the client to re-authorize with `max_age`.
A read is never challenged, and the flow's own write is presence by definition and is never
challenged either. What a token says about when the person authenticated, and what the challenge
carries, are [security's](security.md#when-the-person-authenticated).

**The age is refused where no access token may write the claim.** It qualifies that one permission
and nothing else, so on a claim that door is shut for it would never be asked — and a key that
cannot mean anything is refused at startup rather than accepted in silence, like every other one
here.

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

**The places a claim is published in do not gate alike.** `/userinfo` asks whether the person may
read the claim, which is consent alone, because that endpoint is not client-authenticated; every
other place asks whether the client may, which consent *or* the client's own unconditional scopes
satisfy. A claim can therefore be permitted in one and refused in another before publication is
asked at all — [which half each place asks](#where-a-claim-is-published) is its own.

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
the key every place publishes it under, the key a client names to write it and the key
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
carries the two OpenID channels, the discovery document and the consent flags a profile claim wants,
and it holds them
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
declares one the same way. The flow proves a value by sending a code to it, and a place claiming the
companion true beside no value would be this server asserting it verified something it did not send.

**A required claim holds up the flow of an audience that has it.** A person signing in to an
audience is asked for the required claims that audience has, and for no other audience's; what the
flow does with them once collected is [the interactive flow](interactive-flow.md). Required is read
against [what the flow collects](#the-person), so a claim outside that set holds up nothing.

**An identifier claim is required whatever its file says, and the sign-up is what collects it.** It
is what the account signs in with, so an account holding no value for one holds a row no login
matches — which is not something a `required` key or a client's consent decides. The flow holds a
person to it beside the required claims and never offers it at the claims step, so neither the set
it offers nor the set it holds them to asks the flow's own flag about one. How a sign-up collects
them, and what is still open about an account that predates one being added, is
[the identifier claims](identifier-claims.md#collecting-them-at-sign-up).

## Where a claim is published

**A claim names the places it is published in, and publication is not permission.**
`claims.<id>.published-in` names them, `Claim.publishedIn` holds them and `Claim.isPublishedIn` is
the whole of the test. The ACL answers whether a caller may know a value at all; this answers which
of the places they may already know it through carry it, so it only ever narrows. It is never a
grant, and a claim the ACL refuses is published in no place whatever it names.

**There are five, and four of them carry a value.** The id token and `/userinfo` are the two OpenID
channels; a JWT access token is where [RFC 9068
§2.2.3](https://www.rfc-editor.org/rfc/rfc9068#section-2.2.3) defines the profile carrying identity
claims, and an introspection response where [RFC 7662
§2.2](https://datatracker.ietf.org/doc/html/rfc7662#section-2.2) leaves room for one. The fifth is
the discovery document, which publishes the claim's name and never its value.
`ClaimPublicationPlace` is the authoritative set and its KDoc is the authoritative description of
each.

**The four that carry a value each filter what they were handed, and the client, admin and user APIs
are gated by the ACL alone** — so a claim naming no place is still read through those.

**Which half of the ACL is asked is the place's question, and for three of the four it is the
client's.** An id token and an access token are issued to a client and an introspection response is
answered to one, so each asks whether the client may read the claim, which consent *or* the client's
own unconditional scopes satisfy. `/userinfo` asks whether the person may, which is consent alone,
because that endpoint is not client-authenticated. A claim can therefore be permitted in one and
refused in another before publication is asked at all, and
`ConsentAwareCollectedClaimManager.findByUserIdAndReadableByClientAndPublishedIn` is the read the
first three make.

**The audience restriction holds in every one of them.** A claim restricted to another audience is
left out wherever the value would have gone, because [the audience is asked beside the
ACL](#what-a-restriction-means) rather than inside it — and the audience a place answers for is the
one the token was issued for, which for an act-as token is the audience the exchange named rather
than the acting client's own.

**A claim looks the same in every place that carries it.** `CollectedClaim.publishedMembers` is the
one wire form: the value under the claim's id, the `<claim>_verified` companion beside it where the
claim declares one, and the claims of the address group assembled into the single `address` object
OpenID Connect Core §5.1.1 defines. A place naming its own spelling for any of those would answer a
client differently depending on which of them it read.

**A claim never displaces what a place states about the authorization.** A token says what it is —
its subject, its audience, its scopes, its binding — and an introspection response says the same in
RFC 7662's own members; a claim a deployment happens to have named after one of those is left out
rather than written over it. Each place hands its own set to `CollectedClaim.publishedMembers`,
which is why there is no default for it: a token reads back what it has already claimed, so nothing
has to remember to grow a list, and the introspection response passes
`IntrospectionResource.DECLARED_MEMBERS`.

### The access token is the place a value travels furthest

**An access token is a bearer credential presented on every request, to every resource server of its
audience, for the whole life of the token.** An id token is handed over once, so a claim in one
reaches the client the person authorized and stops there; a claim in an access token reaches
whatever that client presents it to, for as long as the token lives.

**What a deployment gets for that is a resource server reading an attribute of the person off the
credential it already holds**, rather than a round trip to `/userinfo` or to introspection on every
request, or a cache of its own that it then has to invalidate.

**Withdrawing a claim from a token already issued means revoking that token.** Editing the file
stops the next token carrying the value and does nothing to the ones already out; nothing here
re-reads a claim a token states.

**A claim in an access token is readable by whoever holds it.** Nothing encrypts a token this server
issues, so the deployment naming a place is deciding that the value may be read by every party the
credential passes through.

### Advertising and serving come apart, in both directions

**`claims_supported` is what a claim's file says and nothing else.** A claim naming `discovery` is
advertised, whichever half of the specification its name comes from; a claim naming no `discovery`
is served to every client that asks for it by name and named to none that does not. That is the
freedom the scopes already have, stated in [security](security.md#scopes): a deployment may hide
what it serves, and every client already naming it is answered exactly as before.

**The other direction stays shut: `discovery` on a claim naming no place that carries the value is
refused at startup.** Advertising a name no client could ever obtain a value for is what a derived
list prevented by construction, and it survives as a rule about the file rather than as a
consequence of one.

**A generated claim's places are recorded rather than configured.** `sub`, `updated_at` and
`auth_time` are computed rather than collected, so no place reads them out of the claims it filters
— the id token claims the subject and the authentication time itself, the `/userinfo` mapper
computes what it answers, and the access token and the introspection response state `auth_time` out
of the authorization. The enum states where each one arrives all the same, because that is the truth
an operator is owed off the administration API and what the discovery document is answered from:
`sub` reaches both channels and the document, `updated_at` `/userinfo` and the document, and
`auth_time` the id token, the access token, the introspection response and the document.

### The default, and where a deployment reads it back

**A claim naming no place is published in none.** A value leaves this server through the places a
deployment named and through no other, so a claim its file never mentions reaches none of them. The
shipped `openid` template names the two channels and the document, which is what keeps the claims
the specification defines travelling where a client expects them, and advertised, without every
deployment writing it again. It names neither carrier a token holds: putting a deployment's profile
claims into a credential presented on every request is not a decision a file shipped with the server
takes on its behalf.

**The silent answer is the withholding one, deliberately.** Publishing by default would put a
deployment's own claim into every token a client may read on the strength of a line nobody wrote,
and nothing downstream would report it; withholding by default keeps back a value somebody meant to
send, which the deployment sees the first time it looks and fixes in the file it already owns. It is
the other way round from [a scope's default](security.md#scopes), and deliberately: a scope has no
value to keep back, and hiding one nobody asked to hide would take it out of `scopes_supported` on
upgrade.

**The administration API is where it looks.** `published_in` on the claim resource lists every place
a claim is published in, and lists none where it is published in none, so an operator reads what
their deployment publishes off the surface built for them rather than by decoding a token.
Publication is the part of a claim with no other reader — what the ACL permits shows up in the
consent a person is asked for, and where a value goes shows up nowhere else.

**`/userinfo` carries a claim it declares no property for.** `UserInfoResource` lists the properties
the specification names, and a deployment's own claim is serialized beside them out of the claims
naming `userinfo`. What it may carry is still what the person may read, consent alone, and the
shipped `default` claim template leaves that false.

**Nothing refuses a configuration that makes a large token.** What an audience publishes is what the
token carries, and no ceiling is imposed on that — [the design
FAQ](design-faq.md#should-a-configuration-that-makes-a-large-token-be-refused) holds what that costs
and where the limit actually bites.

## What this document does not settle

**Where a client may ask for a claim to be delivered.** OpenID Connect Core §5.5 defines the
`claims` request parameter, which asks for a named claim in `id_token` or in `userinfo`
specifically, and this server implements neither it nor the `claims_parameter_supported` that would
advertise it — so the discovery document tells a client it may not ask.
[Where a claim is published](#where-a-claim-is-published) is the deployment deciding instead, for
clients that ask for nothing, which is every client today; whether a client should get a say is
open.

**A per-client choice, and a per-place one.** `published-in` is the claim's, so every client of the
audience is told the same thing, and a place is named for the claim rather than for the pairing of a
claim with a client.

**A claim holding a structured value.** `ClaimDataType` holds scalars, and a deployment wanting to
keep a document about a person on this server has no type for it. Whether one is added, and what the
ACL and the places it is published in make of a value with an inside, is open.

**Whether an audience's own personal claim should be client-writable at all.** The kind leaves a
restricted one where a deployment puts it; whether an application should ever set what a person
would otherwise type, even for its own audience, is the deployment's call and is not decided here.

**Changing the identifier an account signs in with.** An account keeps the identifier claims it was
given, and why no surface writes one afterwards is
[security's](security.md#what-this-design-does-not-do); proving a new one is a flow of its own, and
[the identifier claims](identifier-claims.md#what-this-document-does-not-settle) leave it open too.

---

← [How the system works](index.md)
