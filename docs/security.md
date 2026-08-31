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

**A token may be bound to a key the client holds**, in which case the proof accompanying the request
is verified against the method and URI it was made for. That binding is what stops a stolen token
being usable on its own.

**A revoked token stops working immediately**, because revocation is a row rather than a shorter
expiry. This is the deliberate cost of not being purely stateless: every request that presents a
token asks the database about it.

## What this design does not do

**It does not rate-limit or lock out.** Nothing limits password attempts, validation-code attempts,
or second-factor attempts — anywhere. An attacker with a valid identifier gets unlimited guesses at
whatever the flow is protecting. This is the largest known gap in this document and it is tracked as
its own work.

**It does not detect anomalies.** No device fingerprint, no impossible-travel check, no risk score.
A correct credential is a correct credential.

**It does not log an audit trail.** Who did what, and when, is reconstructible from application logs
and from the rows themselves, not from a designed record. An audit primitive is designed and not yet
built.

**It does not encrypt tokens at rest beyond hashing what must be hashed.** What the storage layer
does underneath is the deployment's.

---

← [Design documentation](index.md)
