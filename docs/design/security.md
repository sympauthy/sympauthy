# Security

Three questions, asked of every request in that order: who is calling, what are they allowed to do,
and what are they allowed to be told. Everything here answers one of them — the gate each of [the
surfaces](architecture.md#surfaces) applies, the scopes that say what a caller may do, the
authentications a credential turns into, and the tokens that carry the answers off this server. What
a caller may be told about a person is [the claims](claims.md), which those gates and scopes are
enforced through. It closes with what this design deliberately does not do.

The rules for writing a secured controller are [the `api` layer
standard](../standards/api-layer-code-standard.md); this document is what those annotations are
annotating. What a request is believed about — the address it came from, and the proxy taken at its
word — is [the security context](security-context.md).

## What each surface is protected by

### The protocol surfaces

**The OAuth2 surface is protected by the protocol, not by a role.** Client authentication, PKCE,
redirect-URI matching, the signed state, one-time authorization codes: the endpoints are anonymous
because a specification says they are, and every check they do is one the specification names.

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

## Scopes

`Scope` is a sealed hierarchy, and **the split is about where a scope comes from** rather than what
it is called: who decides that a caller has it is the question each kind answers differently — a
scope the person agrees to, a scope a configured rule decides, a scope a client holds in its own
right. The sealed type is the authority on which kinds there are, and a new one has to answer that
same question.

**A grantable scope cannot be asked for, and that is the point.** Anything a client could request it
would request, so a scope representing authority — administrating, acting as another user — is
decided by a rule evaluated against the user and the client, never by the request. Making it a
different type means the code path that grants it and the code path that reads a request cannot be
confused.

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
`published-in`](claims.md#where-a-claim-is-published) names five. One question about a deployment's
configuration has one key whichever half of it is being configured, so an operator who has read
either has read both.

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

**A token may be bound to a key the client holds**, in which case the proof accompanying the request
is verified against the method and URI it was made for. That binding is what stops a stolen token
being usable on its own.

**A revoked token stops working immediately**, because revocation is a row rather than a shorter
expiry. This is the deliberate cost of not being purely stateless: every request presenting a token
asks the database about it.

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
records that moment and every token it produces states it. It is not the moment the code was
exchanged and it is not `iat`, which is why it is recorded where the authorization is recorded
rather than computed when a token is minted; [the design
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
resource server told an unattended caller had just signed in. No generator writes one, and the
mapper refuses such a row when it is read back — so a date that reached the column would take that
token out of service rather than travel on it unnoticed.

**A client may ask for a recent authentication with `max_age`**, which the authorize endpoint reads
with the meaning §3.1.2.1 gives it, refusing anything but a non-negative number of seconds. It is
satisfied by construction: this server keeps no session between authorizations — see
[the state authentication is not a session](#the-state-authentication-is-not-a-session) and
[the interactive flow](interactive-flow.md#what-this-design-does-not-do) — so every authorization
signs the person in anew and the authentication a code represents is always younger than the flow
that produced it. Should a session ever be kept, this is what would prepend the re-authentication
gate; today there is nothing to prepend, and what a client observes is the `auth_time` §3.1.2.1
asks for, which every id token states whether or not one was asked for.

**A surface may refuse an authentication that is too old**, with the challenge RFC 9470 §3 defines:
a `401` carrying
`WWW-Authenticate: Bearer error="insufficient_user_authentication", …, max_age="300"`, and the
[API standard's body](../standards/api-standard.md#errors) under
`authentication.insufficient_user_authentication` beside it, so a client reading headers and one
reading bodies learn the same thing. The client re-authorizes with `max_age` and retries with the
token it is issued, which a public client can do because authorizing is a redirect and nothing in
the challenge needs a secret. The challenge names `max_age` alone of the two parameters RFC 9470
allows: this server publishes no vocabulary of authentication strengths, so it has no `acr_values`
to ask for. [The design
FAQ](design-faq.md#is-a-stale-authentication-answered-with-a-challenge-or-with-a-re-authenticated-flow)
argues the re-authenticated flow that lost to it.

**Nothing emits that challenge yet.** The per-claim age a deployment marks is
[the claims'](claims.md#the-person), and the surface that would read it — the person's own claim
write through their access token — is designed and not yet built. A read is never challenged
whenever it does arrive.

### The id token

**An id token names the access token it was issued beside**, as the `at_hash` claim of OpenID
Connect Core §3.1.3.6. A third-party provider's id token is held to the same claim by the same
computation, so the rule this server enforces and the rule it obeys cannot drift apart.

**An id token is issued only for a grant carrying `openid`**, at the authorization-code exchange and
at the refresh alike. A grant that never asked for OpenID Connect is a plain OAuth 2.0 one, and
[the design FAQ](design-faq.md#is-a-grant-that-did-not-ask-for-openid-owed-an-id-token) argues the
alternative.

**A refresh reissues the identity, not only the access**: the subject and the audience are the
original authentication's, the claims, the expiry and the `at_hash` are this response's, and the
token is filed under the session it descends from, so revoking that session reaches it. What is read
again is the claim values — the consented scopes filtering them are the set recorded with the grant,
the audience filtering them is the refreshing client's, and a consent revoked since refuses the
refresh outright rather than narrowing the token it would have issued.
[The design FAQ](design-faq.md#does-a-refresh-issue-a-new-id-token) argues the alternative.

## What this design does not do

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

**It does not change the identifier an account signs in with.** An account takes its identifier
claims at sign-up and keeps them. No surface writes one afterwards — not the client claim endpoint,
which refuses them by name, and not an invitation, whose pre-assigned claims reach an identifier
only on an account the flow created — because none of them can prove the person controls the new
value or that the person is the one asking. Proving both is an interactive flow with a purpose of
its own, and it is designed and not yet built.

---

← [How the system works](index.md)
