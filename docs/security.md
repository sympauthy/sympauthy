# Security

How a credential becomes an authentication, what a scope is allowed to mean, and what each of
[the surfaces](architecture.md#surfaces) is actually protected by. The rules for writing a secured
controller are [the `api` layer standard](api-layer-code-standard.md); this document is what those
annotations are annotating.

## A credential becomes an authentication

The `security/` package turns each kind of credential into an implementation of the framework's
`Authentication`, and the roles it grants are what every `@Secured` annotation in the server is
ultimately comparing against. **A new kind of caller is a new implementation when it is
authenticated differently, not when it is merely authorized differently** — a caller distinguished
only by what it may do is a scope, not a principal.

An access token issued to a person grants a user role and that token's scopes; client credentials
grant a client role and the client's own scopes; the signed state of an interactive flow grants a
role that says only which flow is asking. The package is the source of truth for which exist.

**The user authentication carries the consented and granted scopes separately**, because they answer
different questions: what the person agreed to share, and what the deployment's rules decided they
may do. Merging them at the moment of authentication would lose the distinction everywhere
downstream, including in the token that gets issued.

**An administrator is a user with admin scopes, not a separate kind of principal.** There is one
user pool. The admin role and the individual admin scopes are derived from the scopes on the token,
so the admin console is an OAuth2 client like any other and an administrator signs in through the
same flow as everybody else.

## The state authentication is not a session

**`ROLE_STATE` is granted by a signed token that identifies an interactive flow session, and nothing
else.** It is presented as a query parameter on a `GET` and an authorization header on a `POST`, it
is minted when a flow starts, and it says only which session the request belongs to.

**It is emphatically not a user's access token, and it is not a cookie.** It carries no identity —
the flow it names may not have a user yet, which is the entire point of the sign-in step. So a
handler behind `ROLE_STATE` knows which session is asking and must load everything else, and a
handler that treated the state as proof of who the caller is would be trusting the one credential
that was designed to make no such claim.

**The signature is what makes it safe to put in a URL.** A person navigating between flow pages
carries the state through their address bar; it is short-lived, bound to one session, and useless
once that session ends.

## Scopes

`Scope` is a sealed hierarchy, and **the split is about where a scope comes from** rather than what
it is called. A scope the person agrees to is one kind; a scope a configured rule decides is
another; a scope a client holds in its own right is a third. The sealed type is the source of truth,
and a new kind of scope has to answer the same question: who decides that a caller has it.

**A scope the deployment turned off is a shape of its own, not an absence.** Those three kinds are
the `EnabledScope` half of the hierarchy, and a `DisabledScope` is the other: a scope this server
knows about and does not serve. Everything that consents, grants, resolves a request or issues a
token takes the enabled type, so a scope that is off cannot be handed to any of them — the compiler
refuses it, rather than each of those paths remembering to filter it out. Only the administration
API asks for the whole set, because an operator has to be able to see what they turned off.

**Being advertised is not being served.** The discovery document lists what a client that has not
been told what to ask for could ask for, and nothing consults that list when a request arrives. A
scope is served whether or not it appears there — the admin and the client scopes are the built-in
case, real and never listed — so a deployment may hide one it serves, and every client already
naming it is answered exactly as before. Turning a scope off is the other question, and it is
answered by the other half of the hierarchy.

**A grantable scope cannot be asked for, and that is the point.** Anything a client could request it
would request; a scope that represents authority — administrating, acting as another user — is
decided by a rule evaluated against the user and the client, never by the request. Making it a
different type means the code path that grants it and the code path that reads a request cannot be
confused.

**A client scope never reaches a user's token.** The two live in different grants: client
credentials produce a client authentication, an authorization code produces a user one, and a scope
of the wrong kind in either is a token that would authorize something nobody consented to.

**Scope strings are constants, never literals.** Both the admin and client scope identifiers are
declared once and referenced everywhere — in the security rules, in the API documentation, in the
grant logic. A scope spelled by hand in an annotation is one no compiler will ever compare against
the one that grants it, and the two spellings would differ silently.

## Claims

**A claim may be restricted to one audience, and it then leaves this server only to that audience.**
`Claim.audienceId` names the restriction and `Claim.belongsToAudience` is the whole of the test: a
claim naming no audience is every audience's, and a claim restricted to one is answered to no other.

**A read of a person's claims names the audience it is for, and the audience is not optional.**
Consent is recorded per user and audience, so a set of consented scopes is always some audience's,
and whoever holds them knows which. A reader taking the scopes and not the audience would be
modelling a state that cannot arise.

**What a client is told about is its own audience, resolved from the credential.** The id token, the
`/userinfo` response and the client API each take it from the client that authenticated, never from
anything the request carried, because a client belongs to exactly one audience.

**A client writes only its own audience's claims, and naming another's is refused rather than
ignored.** A restriction enforced on the way out alone would let a client set what it is not allowed
to read, choosing what another audience is told about a person while never being accountable for the
value.

**The audience an interactive flow works in is the one its authorization is for**, the audience of
the client that started it. It decides the whole of what that flow does with claims: which it offers
to collect, which it accepts, which it holds as required, and which it asks a person to confirm with
a validation code. Someone signing in to one audience is neither asked for another's claims nor held
to them.

**The administration surface reads across every audience, and it is the only reader that does.** An
administrator answers for the deployment rather than for one of its applications, so the audience a
claim is restricted to is something they are shown rather than something that hides it from them.

**A generated claim belongs to every audience.** `sub` and `updated_at` are computed rather than
collected, the parser gives them no audience whatever the configuration says, and nothing about a
person is disclosed by either.

**An identifier claim belongs to every audience, and restricting one is refused at startup.**
`auth.identifier-claims` is declared once for the deployment, so every audience signs people in with
the same claim; a restriction on it would be filtered out of the reads that resolve an account, and
a deployment would lose its sign-in rather than be told. The validator names it instead.

## What each surface is protected by

**The OAuth2 surface is protected by the protocol, not by a role.** Client authentication, PKCE,
redirect-URI matching, the signed state, one-time authorization codes: the endpoints are anonymous
because a specification says they are, and every check they do is one the specification names.

**Discovery is public**, deliberately and by specification. It lists endpoints that answer for
themselves and a key set that is public by definition.

**The flow surface is protected by the state**, and by CORS. Its origins are the pages a deployment
configured, which is the boundary that stops another site from driving a person's sign-in in the
background.

**The client and admin surfaces are protected by scope**, declared at class level so the whole
surface is gated in one place. Anything narrower than the surface — this administrator may act on
this row — is a check inside the manager, because at the moment the annotation runs, nothing has
been loaded yet.

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
expiry. This is the deliberate cost of not being purely stateless: every request that presents a
token asks the database about it.

## Where a request came from

The server reads the address a request came from, the user agent it claimed, and whatever location
the deployment's edge supplied. **It believes no header until an operator names the proxy that sets
it.** With nothing configured under `advanced.security-context`, the address is the peer of the
socket the request arrived on, no forwarded header is read at all, and no location is recorded.

**Naming a proxy is a promise that this server is only reachable through it.** There is no proxy
allow-list here, and nothing checks that a request carrying `CF-Connecting-IP` actually came from
Cloudflare. That check belongs to the deployment, made once with a firewall rule or an origin lock,
and this is the right place for it: the server cannot know its own topology, and a list of CIDRs in
a configuration file is a second copy of that topology which goes stale in silence.

**The consequence, stated plainly: if the origin is reachable directly while a proxy is named,
anyone can set that header and choose what gets recorded about them.** Nothing in this server can
detect it. A deployment that cannot guarantee a closed origin names no proxy and accepts the
proxy's address, which is the shipped default.

### The address and the location are configured apart

**The address comes from exactly one named proxy, and the location may come from several or be
detected.** They are separate settings because they carry different risk and admit different
answers, and holding them together would give the weaker half the reach of the stronger one.

**Only the proxy nearest this server knows the address**, as the peer it accepted a connection from
rather than a value it was handed. So `advanced.security-context.ip` names one proxy, or one header
read as it stands, and falls back to the socket peer. There is no detecting it, and no merging two
answers: an edge reading an entry of `X-Forwarded-For` reads it at a position only its own hop count
explains, so a server guessing which edge is in front would read that position under the wrong
assumption and reach an entry the caller wrote — a forgery that works through a *legitimate* proxy,
which a closed origin does not stop.

**A location is published by each edge under a header of its own** — `CF-IPCountry`,
`X-Akamai-Edgescape`, `CloudFront-Viewer-City` — rather than at a position in one they share. Two
edges reading their own headers cannot be read at cross purposes, so
`advanced.security-context.geo` takes a list applied in order, each entry overriding the fields the
ones before it answered, and `auto-detect` reads every edge that publishes one. What a wrong answer
costs there is a wrong location on a record rather than a request attributed to whoever asked for
it.

**An edge publishing no location cannot be named under the geo setting.** nginx, Traefik, Caddy,
Fastly and Azure Front Door publish an address and nothing else, so naming one there is refused at
startup rather than accepted to no effect.

**A Kubernetes cluster on Google behind an nginx ingress is the shape this split is for**: the
address comes from the ingress, which is adjacent to this server, and the location from the load
balancer in front of it.

### What is read, and how

**An edge is a rule for extracting values, not a table of header names.** An edge that packs several
fields into one header, as Google's load balancer and Akamai's EdgeScape do, or a port beside the
address, as CloudFront does, is one a `Map<field, header>` cannot describe — so each carries the
extraction its own edge needs.

**Where a header arrives more than once, the last value is the edge's.** A caller may have sent it
already and a proxy may append rather than replace, so everything before the last is the caller's —
the same rule that makes only the rightmost entries of `X-Forwarded-For` worth reading.

**A header a deployment names for one field replaces that field and never parses.** An operator
naming a header is saying the value is in it, as it stands. A deployment needing a value dug out of
a packed header names the edge that knows how. A name no request could carry — one holding a space
or a colon — is refused at startup rather than silently matching nothing.

**It is read once, at the boundary, and passed on as an ordinary parameter.** A filter reads every
request ahead of the security filter and leaves the result on the request; a handler is handed it
and passes it down. What is refused is a **manager** reaching back for it — there is no
request-scoped bean and no thread-local: [the general
standard](general-code-standard.md#dependency-rules) keeps a manager callable from a scheduled job
and a unit test, and every manager here is `suspend`, so a thread-local would be intermittently
absent across the coroutine boundaries they cross, recording a null address against a real security
decision, silently.

**Reading it early is what lets something act on it.** An address that exists only once a caller has
been authenticated is no use to anything deciding whether to answer them at all, which is what
throttling will have to decide about a caller who has presented nothing yet.

**A configuration that did not parse is believed about nothing.** The reading narrows the sealed
configuration type rather than throwing, and falls back to the socket peer — which is where a
deployment that configured nothing lands anyway. A file that did not parse names no proxy, so it
makes none of the promise that believing one rests on; and a reading that threw would fail every
request in the chain including the one telling an operator which key is at fault.

### What is kept, and for how long

**A place is recorded once a flow completes, against the person it completed for.** What is observed
when a credential verifies is held against the session that saw it, and folded into that person's
places once their flow succeeds — deduplicated on the address and the user agent, so a row is a
place somebody keeps signing in from rather than one per sign-in.

**Nothing unidentified is kept.** An observation made before a person is known belongs to the
interactive flow session that made it and is collected with it, so a failed sign-in and an abandoned
flow leave nothing behind. There is no retention setting for a population that is not stored.

**A place is kept for as long as it goes on being used**, and
`advanced.security-context.known-user-retention` says how long after it stops. The expiry is
measured from the last sighting rather than the first, because deleting the address somebody has
signed in from every week for six months is the opposite of what the record is for.

**A place is only as particular as the address it was read from.** Where nothing was configured, the
address is the socket peer — the proxy's own, identical for every caller — so what a deployment
behind one accumulates is a row per user agent naming its own ingress, and nothing tells a reader
that apart from a place. Naming the proxy is what makes the record say anything about where a person
is.

**A postal code is read and not kept.** It arrives where an edge publishes one and reaches the
observation, and the record declines it for the reason a coordinate pair is declined: it narrows to
a street group, and nothing here has a use for that.

**An address is personal data, and the deletion ships with the record rather than after it.** The
cutoff is computed when the sweep runs, so lowering the retention takes effect on the next run
instead of on each row's next sighting.

## What this design does not do

**It does not rate-limit or lock out.** Nothing limits password attempts, validation-code attempts,
or second-factor attempts — anywhere. An attacker with a valid identifier gets unlimited guesses at
whatever the flow is protecting. This is the largest known gap in this document and it is tracked as
its own work.

**It does not detect anomalies.** It records where a person signs in from and draws no conclusion
from it: no device fingerprint, no impossible-travel check, no risk score, and no geolocation from
an IP database — only what an edge said. A correct credential is a correct credential.

**It does not log an audit trail.** Who did what, and when, is reconstructible from application logs
and from the rows themselves, not from a designed record. An audit primitive is designed and not yet
built.

**It does not encrypt tokens at rest beyond hashing what must be hashed.** What the storage layer
does underneath is the deployment's.

---

← [Design documentation](index.md)
