# Security

Three questions, asked of every request in that order: who is calling, what are they allowed to do,
and what are they allowed to be told. Everything here answers one of them — the gate each of [the
surfaces](architecture.md#surfaces) applies, the scopes that say what a caller may do, the rules
that say whom a client may act as, the authentications a credential turns into, and the tokens that
carry the answers off this server. What a caller may be told about a person is [the
claims](claims.md), which those gates and scopes are enforced through. It closes with what this
design deliberately does not do.

The rules for writing a secured controller are [the `api` layer
standard](../standards/api-layer-code-standard.md); this document is what those annotations are
annotating. What a request is believed about — the address it came from, and the proxy taken at its
word — is [the security context](security-context.md).

## What each surface is protected by

### The protocol surfaces

**This server speaks OAuth 2.1.** Where 2.1 departs from RFC 6749, what 2.1 says is what this server
does, and a client relying on something 2.1 removed is not served for it. Every authorization
request carries PKCE, a redirect URI is compared as the exact string the client registered, the port
of a loopback address aside, and the implicit and password grants are not offered: `ResponseType`
and `GrantType` are the authority on what is.

**The OAuth2 surface is protected by the protocol, not by a role.** Client authentication, PKCE,
redirect-URI matching, the signed state, [one-time authorization
codes](oauth2-authorization.md#a-code-is-exchanged-once): the endpoints are anonymous because a
specification says they are, and every check they do is one the specification names.

**A client that fails to authenticate is refused with `invalid_client`, and challenged wherever no
public client can be the one refused.** RFC 6749 §5.2 requires a `401` and a `Basic` challenge where
the client sent its credentials in the `Authorization` header, and RFC 7662 §2.3 requires the `401`
at introspection whatever it sent. An endpoint or a grant only a confidential client may use
challenges every refusal. Where a public client may call — the authorization code and the refresh
grants, and revocation — only a client that used the header is challenged, and the rest are answered
`400` like every other protocol error: a challenge asks for a password, and a public client is often
a page in a browser that would put the prompt in front of the person. [The design
FAQ](design-faq.md#is-a-client-that-fails-to-authenticate-answered-400-or-401) holds the rules that
lost to it.

**Discovery is public**, deliberately and by specification. It lists endpoints that answer for
themselves and a key set that is public by definition.

### The flow surface

**The flow surface is protected by the state**, and by CORS. Its origins are the pages a deployment
configured, which is the boundary that stops another site driving a person's sign-in in the
background.

### The client and admin surfaces

**The client and admin surfaces are protected by scope**, declared at class level so the whole
surface is gated in one place. Anything narrower than the surface — this administrator may act on
this row — is a check inside the manager, because nothing has been loaded at the moment the
annotation runs.

**The client's claim routes are gated on a client token alone, and each claim's ACL decides the
rest.** [A client reads and writes by the person's consent or by its own
scopes](claims.md#a-client), so a scope required on the route would turn away the client the person
consented to before its door was asked.

## Scopes

`Scope` is a sealed hierarchy, and **the split is about where a scope comes from** rather than what
it is called: who decides that a caller has it is the question each kind answers differently — a
scope the person agrees to, a scope a configured rule decides, a scope a client holds in its own
right. The sealed type is the authority on which kinds there are, and a new one has to answer that
same question.

**A grantable scope cannot be asked for, and that is the point.** Anything a client could request it
would request, so a scope representing authority — administrating, say — is decided by a rule
evaluated against the user and the client, never by the request. Making it a different type means
the code path that grants it and the code path that reads a request cannot be confused.

**A client scope never reaches a user's token.** The two live in different grants: client
credentials produce a client authentication, an authorization code produces a user one, and a scope
of the wrong kind in either is a token authorizing something nobody consented to.

**A scope may be restricted to one audience, and a client of another audience is refused it.**
`Scope.audienceId` names the restriction exactly as [a claim's
does](claims.md#what-a-restriction-means), and a scope naming no audience is every audience's. The
refusal is made in both places a client comes by a scope: the configuration validator refuses at
startup a client configured with another audience's scope, and `ScopeManager` refuses one at request
time, whether it arrived in an authorization request or is being resolved for a client directly.

**Every admin scope is restricted to the admin audience.** That restriction is what stops an
ordinary client being granted administration by naming an admin scope, so a deployment that
configures no admin audience has no admin scopes at all rather than admin scopes nothing is
restricted from.

**A scope the deployment turned off is a shape of its own, not an absence.** Every kind that
answers the question above is an `EnabledScope`, and `DisabledScope` is the other half of the
hierarchy: a scope this server knows about and does not serve. Everything that consents, grants,
resolves a request or issues a token takes the enabled type, so a scope that is off cannot be handed
to any of them — the compiler refuses it, rather than each of those paths remembering to filter it
out. Only the administration API asks for the whole set, because an operator has to be able to see
what they turned off.

**Being advertised is not being served.** The discovery document lists what a client that has not
been told what to ask for could ask for, and nothing consults that list when a request arrives. A
scope is served whether or not it appears there — the admin and client scopes are the built-in case,
real and never listed — so a deployment may hide one it serves, and every client already naming it
is answered exactly as before. Turning a scope off is the other question, and the other half of the
hierarchy answers it.

**`scopes.<id>.published-in` is where a deployment says so, and it is the word a claim uses.**
`ScopePublicationPlace` holds the one place a scope is published in, the discovery document, and
`EnabledScope.isPublishedIn` is the whole of the test; [a claim's
`published-in`](claims.md#where-a-claim-is-published) names five, and [a claim alone has a second
list](claims.md#a-channel-a-client-may-ask-a-claim-into) for the channels a client may ask it into.
The key is spelled the same on both halves of the configuration, so an operator who has read either
knows what the word means — a scope has no condition to attach to it, because a scope is not
something a client asks for by name in a channel.

**A scope is advertised unless the deployment says otherwise, which is the other way round from a
claim.** A claim keeps a value back until a file says to send it; a scope has no value to keep back,
so what a silent default would cost is a scope taken out of `scopes_supported` for every deployment
on upgrade — and a client configuring itself from the document would stop asking for something this
server still serves. A client scope is the exception and no deployment decides it: it is unusable
outside `client_credentials`, so advertising it to a client configuring an authorization would say
nothing true.

**Scope strings are constants, never literals.** Both the admin and client scope identifiers are
declared once and referenced everywhere — in the security rules, in the API documentation, in the
grant logic. A scope spelled by hand in an annotation is one no compiler will ever compare against
the one that grants it, and the two spellings would differ silently.

## Acting as a person

A client may act as a person: it presents its own credential, names the person, and is issued an
access token saying it acts for them, through the token exchange RFC 8693 defines. Whether it may is
the deployment's to decide, in `rules.act-as` — never the person's, and never the request's.

**A client acts as a person only where an act-as rule allows it.** A rule is evaluated against the
acting client and the person's claims; the matching rule of the highest order decides, a denial
decides between two of the same order, and an exchange no rule matches is refused. A deployment that
writes no rule therefore has no client acting as anybody.

**An act-as rule grants no scope, and the token carries none.** What it decides is whether this
client may act as this person, and the token says exactly that: the person as its subject, the
client in its `act` claim. What the client may then do as the person is the resource server's to
decide from those two.

**Acting as a person rests on the act-as rules, not on a consent.** A client may act as a person who
has never signed in to the audience the exchange names, so the exchange reads no consent, and
revoking the person's consent revokes no act-as grant and leaves a client free to exchange again.
[The design FAQ](design-faq.md#does-acting-as-a-person-need-their-consent) argues the alternative.

**The rules read the claims of the audience the token is issued for**, which is the one the exchange
names and may be neither the acting client's nor its default. That is where the decision lands, so
that is the audience whose claims it may be keyed on — [the
claims](claims.md#reading-a-persons-claims) hold the rule for every reader.

**The client proves it is the actor with a token of its own.** The token it exchanges is one it was
issued under `client_credentials`; a person's token, another client's, and an act-as token are
refused, so one act-as token is never exchanged for the next. The exchange opens a grant of its
own recording the grant of the token exchanged, and [the grants](grants.md#what-a-grant-is) hold
how an act-as token follows it.

**No authentication of the person stands behind an act-as token.** It states no `auth_time`, for
[the reason every such token states none](#when-the-person-authenticated), and comes with neither a
refresh token nor an id token: a client wanting another exchanges again.

`ActAsRuleManager` evaluates the rules, and the functions an expression may call are
`ActAsRuleExpressions`; `TokenExchangeManager` serves the exchange around them.

## Claims and audiences

A claim is something this server knows about a person, and an audience is the set of applications
entitled to it. Whose a claim is, who may read and write one, which audience has it and which places
this server publishes it in are [the claims](claims.md). Every rule there is asked after the
gate of the surface a request reached and with the scopes its credential carries, which is what
stays here.

## Where a request came from

The server reads the address a request came from, the user agent it claimed, and whatever location
the deployment's edge supplied, and it believes none of it until a deployment has named the proxy
that sets it — which is a promise about the deployment's own topology rather than a setting.

That trust model and what it costs where the promise is not kept, why the address and the location
are configured apart, what each edge publishes, and how long a place is kept, are [the security
context](security-context.md).

## A credential becomes an authentication

The `security/` package turns each kind of credential into an implementation of the framework's
`Authentication`, and the roles it grants are what every `@Secured` annotation in the server
compares against. **A new kind of caller is a new implementation when it is authenticated
differently, not when it is merely authorized differently** — a caller distinguished only by what it
may do is a scope, not a principal.

An access token issued to a person grants a user role and that token's scopes; client credentials
grant a client role and the client's own scopes; the signed state of an interactive flow grants a
role that says only which flow is asking. The package is the source of truth for which exist.

**An authentication made from an access token carries the client the token was issued to**, looked
up once where the token is validated. A handler reads the client there rather than asking the
configuration again, so a client the configuration no longer declares is refused in one place and
with one status, because [a grant does not outlive its client](grants.md#what-a-grant-is).

**The user authentication carries the consented and granted scopes separately**, because they answer
different questions: what the person agreed to share, and what the deployment's rules decided they
may do. Merging them at the moment of authentication would lose the distinction everywhere
downstream, including in the token that gets issued.

**An administrator is a user with admin scopes, not a separate kind of principal.** There is one
pool of accounts, and the admin role and the individual admin scopes are derived from the scopes on
the token — so the admin console is an OAuth2 client like any other, and an administrator signs in
through the same flow as everybody else.

### The state authentication is not a session

**`ROLE_STATE` is granted by a signed token that identifies an interactive flow session, and nothing
else.** It is minted when a flow starts, presented as a query parameter on a `GET` and an
authorization header on a `POST`, and it says only which session the request belongs to.

**It is emphatically not a user's access token, and it is not a cookie.** It carries no identity:
the flow it names may not have a user yet, which is the entire point of the sign-in step. So a
handler behind `ROLE_STATE` knows which session is asking and must load everything else, and one
treating the state as proof of who the caller is would be trusting the single credential designed to
make no such claim.

**The signature is what makes it safe to put in a URL.** A person navigating between flow pages
carries the state through their address bar; it is short-lived, bound to one session, and useless
once that session ends.

## Tokens

**An access token is validated on every request**, as a signature over this server's own keys, with
its issuer, audience and expiry checked. Nothing is trusted because it parsed.

**A token may be bound to a key the client holds**, in which case it is presented under the `DPoP`
scheme beside a proof signed by that key, and the proof is verified against the method and URI it
was made for and against the token it accompanies. That binding is what stops a stolen token being
usable on its own.

**Every token is issued under a grant, and is live only while its grant is.** A grant is what a
client was allowed, and every token of an authorization stands under it however often its client
refreshed: a revocation reaches the grant rather than one token, and a lifetime set on the grant
ends every token under it. What a grant records, how a revocation and a lifetime reach it and what
collects it are [the grants](grants.md).

**A revoked grant stops every token under it at once**, because revocation is a row rather than a
shorter expiry. This is the deliberate cost of not being purely stateless: every request presenting
a token asks the database about it and about its grant.

### A token bound to a key

**A client that sends a DPoP proof when it asks for tokens is issued tokens bound to the key that
signed it**, as RFC 9449 defines. From then on the access token is presented as
`Authorization: DPoP`, with a fresh proof in the `DPoP` header on every request, and a refresh
token is refreshed only beside a proof signed by the same key.

**A bound token presented as a bearer token is refused, even beside a valid proof.** RFC 9449 §7.2
asks it of every resource that understands the scheme, and a binding the holder of the token could
decline would bind nothing: whoever stole the token would present it the old way. Accepting the
`DPoP` scheme only adds a way in, so this refusal is the whole of the protection.

**A token bound to no key presented under the `DPoP` scheme is refused too.** There is no key for
its proof to be matched against, and accepting it would let a proof stand for a binding nobody made.

**The binding is checked by whatever accepts an access token, not by the surface it reaches.** The
userinfo endpoint and the client and admin surfaces are resource servers inside this one, and every
one of them takes its token through `AccessTokenValidator`, which hands the token, the scheme it
came under and the proof beside it to `DpopManager`. A surface added behind an access token is
therefore checked without asking to be, and the refresh grant asks `DpopManager` the same question
of a bound refresh token, so the token endpoint and the resource surfaces cannot disagree about
which key a proof must be signed with. The framework reads the `Bearer` scheme alone, and
`DpopTokenReader` is what reads the other.

### A refused token is challenged

**Every refusal of a request to a route an access token is accepted on carries a `WWW-Authenticate`
challenge**, as RFC 6750 §3 requires of a request presenting no token and of one whose token does
not give access. The body beside it is [the API standard's](../standards/api-standard.md#errors),
and the challenge is how a client reading headers learns the same thing.

**A route accepts a token where its gate names a role or a scope a token grants.** A request no
route answers, and one to a route gated on the state or open to anyone, is refused without a
challenge, whatever it presented: telling it how to present a token would send it after a token
that route does not take.

**The challenge names both schemes a token is accepted under, `Bearer` and then `DPoP`.** Every
route accepting one accepts the other, and RFC 9449 §7.2 leaves a server free to name both. A client
whose bound token was refused would otherwise be challenged only under the scheme [it may not
present that token with](#a-token-bound-to-a-key). The `DPoP` challenge lists the algorithms a proof
may be signed with, which are the ones the discovery document publishes.

**The error is carried by the challenge of the scheme the token came under**, as RFC 9449 §7.2
recommends, and a request that presented no token is told of no error at all, as RFC 6750 §3.1 asks.
The one exception is a token that should have been bound, whose error [rides the `DPoP`
challenge](#a-refusal-carrying-several-challenges) whatever scheme it came under:

| The request presented | Status | `error` |
| --- | --- | --- |
| no token | `401` | none |
| a token that does not validate | `401` | `invalid_token` |
| a token whose DPoP proof does not validate | `401` | `invalid_dpop_proof`, on the `DPoP` challenge |
| a valid token, without the role or the scope the route is gated on | `403` | `insufficient_scope` |
| a valid token refused for more than a scope | `401` | one per condition, as below |

**What refuses the proof is what RFC 9449 §4.3 checks**, which includes the `ath` naming the token
and the key matching the one the token is bound to. A bound token presented as a bearer token, and
an unbound one presented under `DPoP`, are refused for the token they are rather than for a proof,
and are told `invalid_token`.

**Both challenges name the realm `sympauthy`.** RFC 6750 §3 requires a `Bearer` challenge to carry a
parameter, and one answering a request that presented no token has no error to carry. A token is
accepted on the userinfo endpoint and on the client and admin surfaces, so no one surface names the
space it is good in, and the realm names the server. The `Basic` challenge of [a client that fails
to authenticate](#the-protocol-surfaces) names its surface, because client credentials are accepted
on that one and nowhere else.

**`insufficient_scope` names the scope where the refusal knows it.** RFC 6750 §3.1 lets it say
which scope the request requires, space-separated in a `scope` parameter. The framework's `403`
names none: it is raised from a `@Secured` annotation, and what answers it is not told which rule of
the route the token failed, so the operation's published `403` names the scope instead. A refusal
this server decides itself knows the scope, and names it.

**One description of a challenge, and one rendering of it.** What a refusal asks of the client is
decided where the refusal is decided, as a list of challenges — each an error and its parameters —
and written into the header in one place, together with the realm, both schemes and the `algs`. A
refused token, the framework's `403` and a refused claim write are therefore never written two ways
for one error, and a challenge added later is written once.

**A page in a browser is told what a server-side client is told.** A response to another origin
shows a page only the headers it lists, so every surface challenging a token lists
`WWW-Authenticate` among the ones [it exposes](../standards/api-standard.md#cors).

#### A refusal carrying several challenges

**Every condition the token did not meet is a challenge of its own, under the scheme the token came
under.** RFC 6750 §3 gives a challenge one `error`, and RFC 9110 §11.6.1 lets a response carry more
than one challenge, so a refusal owing several errors repeats the scheme once for each. A bearer
token refused [a claim write](claims.md#the-person) for a scope the person did not consent to and
for an authentication too old is answered:

```http
WWW-Authenticate: Bearer realm="sympauthy", error="insufficient_scope", scope="profile:write",
  Bearer realm="sympauthy", error="insufficient_user_authentication",
  error_description="A more recent authentication is required", max_age="300",
  DPoP realm="sympauthy", algs="…"
```

**The challenges come in a fixed order: the scope, the age, the binding.** A client reading every
challenge re-authorizes once for all of them — a redirect naming the scopes and `max_age`, with a
DPoP key. The order is for a client reading only the first challenge of a scheme, which is then told
the condition the person has to be asked about.

**A token that is not bound to a key is challenged on the `DPoP` challenge, with `invalid_token`.**
RFC 9449 defines no error for a token that should have been bound, and its §7.1 answers a token
whose binding does not hold with `invalid_token` on the `DPoP` challenge; that is the challenge a
client answers with a bound token. It carries `error_description="A token bound to a key is
required"`, so a client tells it from a token that does not validate. It is the one error not
carried by the scheme the token came under: the condition is met under the other scheme alone, and
an unbound token never arrives under `DPoP`, since it is [refused there
already](#a-token-bound-to-a-key).

**The status is `403` where `insufficient_scope` is the only challenge, and `401` otherwise.** RFC
6750 §3.1 answers `insufficient_scope` with `403`, and RFC 9470 §3 and RFC 9449 §7.1 answer theirs
with `401`. A refusal any other challenge is owed is one the same request succeeds at with a
different token, which is what `401` says, and it carries the challenge RFC 9110 §15.5.2 requires
of a `401`.

**The body names the first challenge answered with the response's status.** The [API standard's
body](../standards/api-standard.md#errors) carries one code, so a refusal owing several names one:
the first in the order above that its status belongs to — `forbidden` for a scope standing alone,
`authentication.insufficient_user_authentication` for an age, `authentication.unbound_token` for a
binding. A `401` refused for a scope and an age therefore names the age, while its header leads
with the scope, and `forbidden` is only ever read beside the `403` the [API
standard](../standards/api-standard.md#verbs-and-status-codes) gives a missing scope. [The design
FAQ](design-faq.md#is-a-refusal-owing-several-challenges-answered-with-one-or-with-all-of-them)
argues the single challenge that lost.

### When the person authenticated

**Every token issued for a person says when they proved a credential of their account**, as the
`auth_time` OpenID Connect Core §2 defines. The id token carries it, the access token carries it
where RFC 9068 §2.2.1 puts it so a resource server that is not this one can read it too, and the
introspection response reports it where RFC 9470 §6.2 puts it. `claims_supported` lists it, and a
deployment configures nothing about it: it is [a generated claim](claims.md#a-generated-claim), so a
section written under its name is dropped and every key under one is refused at startup.
It is stated whether or not a client asked for it, and [the design
FAQ](design-faq.md#does-a-token-state-auth_time-only-when-a-client-asked-for-it) argues the
minimum the specification would have allowed.

**It is the moment the credential verified, and nothing else.** A password checked, a third-party
provider's callback resolved to the account, or the account created at sign-up: the authorization
records that moment, the grant its code opens copies it, and every token under that grant states
it. It is not the moment the code was exchanged and it is not `iat`, which is why it is recorded
where the authorization is recorded rather than computed when a token is minted; [the design
FAQ](design-faq.md#is-iat-the-recency-a-resource-server-should-read) holds what reading `iat`
instead would cost.

**A second factor does not move it.** Passing one is a purpose that follows in the same session, so
`auth_time` says when the credential was proven and whether a second factor followed is a question
for `amr`, which this server does not publish.

**A refresh reissues it unchanged.** A refresh reissues the identity of the original authentication
and the time of that authentication is part of it — reading it again would have the server say a
person authenticated at the moment their client asked for a new token, which is the whole of what
`iat` already fails to answer. An authentication a month old therefore states a month-old
`auth_time` on a token minted a minute ago.

**A token no person's authentication is behind carries none.** A `client_credentials` grant has no
person at all, and a token exchange asserts an identity nobody proved; a date on either would be a
resource server told an unattended caller had just signed in. Neither grant records one, and the
mapper refuses such a grant when it is read back — so a date that reached the column would take
every token under it out of service rather than travel on them unnoticed.

**A client may ask for a recent authentication with `max_age`**, which the authorize endpoint reads
with the meaning §3.1.2.1 gives it, refusing anything but a non-negative number of seconds. It is
satisfied by construction: this server keeps no session between authorizations — see
[the state authentication is not a session](#the-state-authentication-is-not-a-session) and
[the interactive flow](interactive-flow.md#what-this-document-does-not-settle) — so every
authorization signs the person in anew and the authentication a code represents is always younger
than the flow that produced it. Should a session ever be kept, this is what would prepend the
re-authentication gate; today there is nothing to prepend, and what a client observes is the
`auth_time` §3.1.2.1 asks for, which every id token states whether or not one was asked for.

**A surface may refuse an authentication that is too old**, with the challenge RFC 9470 §3 defines:
a `401` carrying `error="insufficient_user_authentication", …, max_age="300"` on the challenge of
the scheme the token came under, as [every refused token's error is](#a-refused-token-is-challenged),
and the [API standard's body](../standards/api-standard.md#errors) under
`authentication.insufficient_user_authentication` beside it, so a client reading headers and one
reading bodies learn the same thing. The client re-authorizes with `max_age` and retries with the
token it is issued, which a public client can do because authorizing is a redirect and nothing in
the challenge needs a secret. The challenge names `max_age` alone of the two parameters RFC 9470
allows: this server publishes no vocabulary of authentication strengths, so it has no `acr_values`
to ask for. [The design
FAQ](design-faq.md#is-a-stale-authentication-answered-with-a-challenge-or-with-a-re-authenticated-flow)
argues the re-authenticated flow that lost to it.

**Nothing emits that challenge yet.** The age a deployment asks of an authentication is a term of
[a claim's write door](claims.md#the-person), whose refusal carries this challenge beside the others
the door's expression names, and the surface that would read it — the person's own claim write
through their access token — is designed and not yet built. A read is never challenged whenever it
does arrive.

### The id token

**An id token names the access token it was issued beside**, as the `at_hash` claim of OpenID
Connect Core §3.1.3.6. A third-party provider's id token is held to the same claim by the same
computation, so the rule this server enforces and the rule it obeys cannot drift apart.

**An id token is issued only for a grant carrying `openid`**, at the authorization-code exchange and
at the refresh alike. A grant that never asked for OpenID Connect is a plain OAuth 2.0 one, and
[the design FAQ](design-faq.md#is-a-grant-that-did-not-ask-for-openid-owed-an-id-token) argues the
alternative.

**An id token is signed with the one algorithm `advanced.jwt.public-alg` names, and the discovery
document lists that algorithm alone.** It is ES256 unless a deployment chooses otherwise. OpenID
Connect Discovery 1.0 §3 has every server list RS256, and FAPI 2.0 §5.4.1 allows PS256, ES256 and
EdDSA and nothing else; where the two disagree this server takes the newer text, as it takes OAuth
2.1 over RFC 6749. Listing RS256 without signing with it would send a client configuring itself from
the document after a signature it never receives. [The design
FAQ](design-faq.md#does-the-discovery-document-list-rs256-as-openid-connect-discovery-requires)
holds the options that lost.

**A refresh reissues the identity, not only the access**: the subject and the audience are the
original authentication's, the claims, the expiry and the `at_hash` are this response's, and the
token is issued under the grant it descends from, so revoking that grant reaches it. What is read
again is the claim values — the consented scopes filtering them are the set recorded with the grant,
the audience filtering them is the refreshing client's, and a consent revoked since refuses the
refresh outright rather than narrowing the token it would have issued.
[The design FAQ](design-faq.md#does-a-refresh-issue-a-new-id-token) argues the alternative.

## What this document does not settle

**It does not rate-limit or lock out.** Nothing limits password attempts, validation-code attempts,
or second-factor attempts — anywhere. An attacker with a valid identifier gets unlimited guesses at
whatever the flow is protecting. This is the largest known gap in this document and it is tracked as
its own work.

**It does not detect anomalies.** It records where a person signs in from, as
[the security context](security-context.md), and draws no conclusion from it: no device fingerprint,
no impossible-travel check, no risk score, and no geolocation from an IP database — only what an
edge said. A correct credential is a correct credential.

**It does not log an audit trail.** Who did what, and when, is reconstructible from application logs
and from the rows themselves, not from a designed record. An audit primitive is designed and not yet
built.

**It does not encrypt tokens at rest beyond hashing what must be hashed.** What the storage layer
does underneath is the deployment's.

**It does not remember a DPoP proof, and hands out no nonce.** RFC 9449 §11.1 asks only that a
proof be accepted for a short while — here a minute either side of the time it names — and leaves
recording its `jti` optional, so a proof captured with the token it accompanies can be replayed
against the same method and URI until it ages out. Without the nonce of §8, nothing stops a client,
or the person using it, signing proofs ahead for a future `iat` and carrying them away from the key;
the `ath` they carry bounds that to the access token's lifetime.

**It does not challenge a request to the flow surface that carries no state.** The state is not an
access token, and on a `GET` it travels as a query parameter rather than under an authentication
scheme, so neither [challenge](#a-refused-token-is-challenged) applies to it. RFC 9110 §15.5.2 asks
one of every `401` all the same. Answering it is the flow's decision — a `State` challenge naming the
scheme a `POST` already presents it under, or a status other than `401` — and it has not been taken.

**It does not change the identifier an account signs in with.** An account takes its identifier
claims at sign-up and keeps them. No surface writes one afterwards — not the client claim endpoint,
which refuses them by name, and not an invitation, whose pre-assigned claims reach an identifier
only on an account the flow created — because none of them can prove the person controls the new
value or that the person is the one asking. Proving both is an interactive flow with a purpose of
its own, and it is designed and not yet built.

---

← [How the system works](index.md)
