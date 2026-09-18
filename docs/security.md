# Security

Three questions, asked in that order: who is calling, what are they allowed to do, and what are they
allowed to be told. Everything here answers one of them — the authentications a credential turns
into, the gate each of [the surfaces](architecture.md#surfaces) applies, the scopes that say what a
caller may do, the audiences that bound both what it may hold and what it may be told, and the
tokens that carry the answers off this server. It closes with what this design deliberately does
not do.

The rules for writing a secured controller are [the `api` layer
standard](api-layer-code-standard.md); this document is what those annotations are annotating. What
a request is believed about — the address it came from, and the proxy taken at its word — is
[the security context](security-context.md).

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
user pool, and the admin role and the individual admin scopes are derived from the scopes on the
token — so the admin console is an OAuth2 client like any other, and an administrator signs in
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

## What each surface is protected by

**The OAuth2 surface is protected by the protocol, not by a role.** Client authentication, PKCE,
redirect-URI matching, the signed state, one-time authorization codes: the endpoints are anonymous
because a specification says they are, and every check they do is one the specification names.

**Discovery is public**, deliberately and by specification. It lists endpoints that answer for
themselves and a key set that is public by definition.

**The flow surface is protected by the state**, and by CORS. Its origins are the pages a deployment
configured, which is the boundary that stops another site driving a person's sign-in in the
background.

**The client and admin surfaces are protected by scope**, declared at class level so the whole
surface is gated in one place. Anything narrower than the surface — this administrator may act on
this row — is a check inside the manager, because nothing has been loaded at the moment the
annotation runs.

## Scopes

`Scope` is a sealed hierarchy, and **the split is about where a scope comes from** rather than what
it is called: a scope the person agrees to is one kind, a scope a configured rule decides is
another, a scope a client holds in its own right is a third. The sealed type is the source of truth,
and a new kind of scope has to answer the same question — who decides that a caller has it.

**A grantable scope cannot be asked for, and that is the point.** Anything a client could request it
would request, so a scope representing authority — administrating, acting as another user — is
decided by a rule evaluated against the user and the client, never by the request. Making it a
different type means the code path that grants it and the code path that reads a request cannot be
confused.

**A client scope never reaches a user's token.** The two live in different grants: client
credentials produce a client authentication, an authorization code produces a user one, and a scope
of the wrong kind in either is a token authorizing something nobody consented to.

**A scope may be restricted to one audience, and a client of another audience is refused it.**
`Scope.audienceId` names the restriction exactly as [a claim's does](#what-a-restriction-means), and
a scope naming no audience is every audience's. The refusal is made in both places a client comes by
a scope: the configuration validator refuses at startup a client configured with another audience's
scope, and `ScopeManager` refuses one at request time, whether it arrived in an authorization
request or is being resolved for a client directly.

**Every admin scope is restricted to the admin audience.** That restriction is what stops an
ordinary client being granted administration by naming an admin scope, so a deployment that
configures no admin audience has no admin scopes at all rather than admin scopes nothing is
restricted from.

**A scope the deployment turned off is a shape of its own, not an absence.** Those three kinds are
the `EnabledScope` half of the hierarchy, and `DisabledScope` is the other: a scope this server
knows about and does not serve. Everything that consents, grants, resolves a request or issues a
token takes the enabled type, so a scope that is off cannot be handed to any of them — the compiler
refuses it, rather than each of those paths remembering to filter it out. Only the administration
API asks for the whole set, because an operator has to be able to see what they turned off.

**Being advertised is not being served.** The discovery document lists what a client that has not
been told what to ask for could ask for, and nothing consults that list when a request arrives. A
scope is served whether or not it appears there — the admin and client scopes are the built-in case,
real and never listed — so a deployment may hide one it serves, and every client already naming it
is answered exactly as before. Turning a scope off is the other question, and the other half of the
hierarchy answers it.

**Scope strings are constants, never literals.** Both the admin and client scope identifiers are
declared once and referenced everywhere — in the security rules, in the API documentation, in the
grant logic. A scope spelled by hand in an annotation is one no compiler will ever compare against
the one that grants it, and the two spellings would differ silently.

## Claims and audiences

A claim is something this server knows about a person; an audience is the set of applications
entitled to it. Nearly every rule below keeps those two straight: which audience a claim belongs to,
which audience a read is made for, and which audience a writer may name.

### What a restriction means

**A claim may be restricted to one audience, and it then leaves this server only to that audience.**
`Claim.audienceId` names the restriction and `Claim.belongsToAudience` is the whole of the test: a
claim naming no audience is every audience's, and a claim restricted to one is answered to no other.

**A generated claim belongs to every audience.** `sub` and `updated_at` are computed rather than
collected, the parser gives them no audience whatever the configuration says, and nothing about a
person is disclosed by either.

**An identifier claim belongs to every audience, and restricting one is refused at startup.**
`auth.identifier-claims` is declared once for the deployment, so every audience signs people in with
the same claim. A restriction on it would be filtered out of the reads that resolve an account, and
a deployment would lose its sign-in rather than be told; the validator names it instead.

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
the value.

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
behind it leaves it out the same way the interactive flow already does.

## Tokens

**An access token is validated on every request**, as a signature over this server's own keys, with
its issuer, audience and expiry checked. Nothing is trusted because it parsed.

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

**A token may be bound to a key the client holds**, in which case the proof accompanying the request
is verified against the method and URI it was made for. That binding is what stops a stolen token
being usable on its own.

**A revoked token stops working immediately**, because revocation is a row rather than a shorter
expiry. This is the deliberate cost of not being purely stateless: every request presenting a token
asks the database about it.

## Where a request came from

The server reads the address a request came from, the user agent it claimed, and whatever location
the deployment's edge supplied, and it believes none of it until a deployment has named the proxy
that sets it — which is a promise about the deployment's own topology rather than a setting.

That trust model and what it costs where the promise is not kept, why the address and the location
are configured apart, what each edge publishes, and how long a place is kept, are [the security
context](security-context.md).

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

← [Design documentation](index.md)
