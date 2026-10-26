# The OAuth2 authorization

A client sending a person to `/api/oauth2/authorize` is asking for an authorization code it can
exchange for tokens. The request starts an interactive flow session whose initiating purpose is
`OAUTH2_AUTHORIZE`, and this document is that purpose: what a request becomes, what the person is
put through for it, how the session hands them back to the client, and what the code is then good
for. The engine sequencing it is [the interactive flow](interactive-flow.md); the tokens the code is
exchanged for are [security's](security.md#tokens), the claims they carry are [the
claims'](claims.md), and the account a sign-up creates along the way is [the provisional
user](provisional-user.md).

## What a request becomes

**Every authorization request becomes a session, a refused one too.** The client, the redirect URI,
the scopes, the PKCE challenge, `max_age`, the `claims` parameter and an invitation are each
validated, and the first refusal is recorded on a session created already failed. The person is
sent to the error page, which reads the refusal off that session like any other failure, and an
operator finds it among the sessions with the rest. The OAuth2 record is written for it too, holding
whatever was known, so the session carries the error that explains it.

**A request object is refused, never read.** OpenID Connect Core §6 lets a client move any parameter
of its request into a JWT, sent in `request` or behind `request_uri`, and requires a server that
reads neither to refuse it. Serving what was left in the query would answer a request the client did
not make — a `nonce` or a `claims` the object carried dropped without a word — so the refusal is
recorded ahead of every parameter the object could have held. The discovery document says the same,
with `request_parameter_supported` and `request_uri_parameter_supported` both false.

**What the client asked for is the session's OAuth2 record**, keyed by the session id and read
through its own manager, the way [the interactive flow](interactive-flow.md#the-session) has every
purpose keep its state. It holds the request as it was validated — the client, the redirect URI, the
scopes and claims asked for, the client's `state` and `nonce`, the PKCE challenge, the invitation —
and the consent and grant decisions the flow takes on it.

## What the person goes through

**The steps are what the account still lacks for the client's audience.** A person not yet signed
in is sent to sign in, or to sign up where an invitation brought them; an account missing a claim
the audience requires is asked for it; a claim that must be validated is validated. The handler's
`nextStepOrNull` is the authority on which steps there are and in what order.

**A second factor follows them, where the deployment enables one**, as a purpose the authorization
appends after itself: a challenge for an account already enrolled, an enrolment for one that signed
up in this session or that the deployment requires to have one. It is the last thing the person
passes, so no code exists for an authorization whose second factor has not.

## How the session ends

**Completing grants the scopes, records the consent, opens the grant and generates the code, in one
transaction.** The authorization's terminal effect runs the scopes through the granting pipeline
first, before [the completion transaction](interactive-flow.md#the-engine) opens, because the
pipeline asks the client's authorization webhook where one is configured and that transaction holds
the promotion's locks. Inside it, the effect records the granted scopes and the consent for the
client's audience, consumes the invitation where one brought the person here, opens the grant every
token of the authorization will be issued under, and generates the authorization code last, naming
that grant — so a session never completes without a code, and a code never outlives a completion
that failed. Where no scope was granted and the deployment does not allow access to a client
without one, the session fails before that transaction opens instead.

**The authorization webhook is asked a question, not told an outcome.** It is called before the
promotion, so the user id it is handed may belong to a sign-up that a concurrent one then keeps
from becoming an account, and the authorization it decides on may still fail to complete. A client
reads the call as a request for a decision and learns of an authorization from the code.

**The code is handed out once.** It goes to the client's redirect, answered to the request that
completed the session. A later request with the same state to a step that answers with where the
flow goes — a reload, the back button, a form submitted twice — is sent to the error page, which
says the authorization has already completed, and the session is left completed; a step answering
with a body of its own refuses it as over, as it refuses every session that has ended. Handing the
code out again would give it to whoever holds the state, which outlives the flow and travels in
URLs, and would send the client a code it has already exchanged.

**Cancelling answers the client with `access_denied`.** A person declining is the client's to know,
so the session hands them back to the redirect URI with the error RFC 6749 §4.1.2.1 names and the
client's `state` echoed, and no code.

## What the code is good for

**A code is good only for the client that asked for it, with the verifier of its challenge.** The
token endpoint refuses a code it does not hold, one past [its own
lifetime](#how-long-a-code-is-exchangeable), or one whose grant was opened for another client or
for an audience its client [no longer belongs to](grants.md#how-a-revocation-reaches-a-grant); then
one whose PKCE verifier does not match the challenge. The redirect URI is not compared again,
as [OAuth 2.1](security.md#the-protocol-surfaces) has it: PKCE is what binds the code to the request
that produced it, so a code read off a redirect is useless without what only that client holds.

**A code is issued under a grant, and exchanging it issues tokens under that grant.** The grant
copies what the person went through for — the client, the scopes and how each set was obtained, when
they authenticated, the claims asked for — and a refresh issues its tokens under the same grant:
[the grants](grants.md) are that record. A code whose grant was revoked before it was exchanged is
refused, and [a second exchange](#a-code-is-exchanged-once) of the code revokes it.

**An exchange reads the code and its grant, never the session that issued them.** Everything it
checks either comes with the code — the PKCE challenge, the `nonce` the id token repeats — or is
already on [the grant](grants.md#what-a-grant-is): the client, the person, the scopes, when they
authenticated, the claims asked for. A session therefore does not have to outlive its code, and a
code issued near the end of a flow's time is exchangeable for its whole lifetime, the session
collected or not.

### How long a code is exchangeable

**A code is exchangeable for a lifetime of its own, counted from when it was issued.**
`auth.authorization-code.expiration` says how long, a minute unless a deployment says otherwise. A
client exchanges a code as soon as the person is redirected back to it, so a minute covers a slow
network and a retry, and leaves a code that leaked little time to be used. How fast the person got
through the flow has no bearing on it: a code ending with its session would stay exchangeable for
nearly the whole flow's time when they were quick, and for a moment when they were slow. [The design
FAQ](design-faq.md#how-long-is-an-authorization-code-exchangeable) holds the lifetimes that lost.

### A code is exchanged once

**An authorization code is exchanged once**, as RFC 6749 §10.5 requires. The exchange that is
issued tokens is the one that spends it: the code is marked in the transaction that writes those
tokens, so two exchanges racing over one code are issued one set between them, and an exchange that
fails while issuing leaves the code unspent.

**A second exchange is refused, and revokes the grant the code was issued under.** RFC 6749 §4.1.2
recommends it. A code presented twice is either a client that lost track of its own exchange or a
code somebody else is using, and the two cannot be told apart; revoking the grant — under which the
first exchange's tokens and every refresh descended from them stand — is what takes back the tokens
of whichever of them came first. A client that retries an exchange whose answer it lost pays for
that: the person signs in again. The code names the grant its authorization opened, which is how [a
replay reaches it](grants.md#how-a-revocation-reaches-a-grant) rather than through the session the
grant came from, which is collected long before most grants end.

**Only an exchange that passes every other check is a second one.** Client authentication and the
PKCE verifier are checked before the code is spent, and a request failing either is refused for that
reason and revokes nothing. A code alone is what leaks — into a log, a
browser's history, a `Referer` — and a party holding only that is not handed a way to sign a person
out of the client that exchanged it. [The design
FAQ](design-faq.md#does-any-presentation-of-a-spent-authorization-code-revoke-what-it-was-exchanged-for)
argues the alternative.

**A spent code is kept until its own lifetime ends, not deleted when it is exchanged.** A replay is
recognised by the code it presents: a row deleted on exchange would make the second exchange read as
a code nobody issued, refused like any unknown one and revoking nothing. Kept that long, a replay is
recognised for as long as the code could have been exchanged at all.

**A code presented after its lifetime is refused as expired, and revokes nothing**, exchanged or
not. The revocation answers a code that reached somebody who exchanged it before the client did, and
both of them exchange within its lifetime, the client because it exchanges as soon as it is
redirected. [The design FAQ](design-faq.md#how-long-is-a-spent-authorization-code-kept) holds the
retention that lost.

## What implements it

**`OAuth2AuthorizeInteractiveFlowPurposeHandler` is the purpose**: the steps, the second factor it
appends, the terminal effect, and that it hands off once — the member [the
engine](interactive-flow.md#the-engine) asks of an initiating purpose.
`InteractiveAuthFlowSessionManager.startAuthorizationWith` is where a request is validated and its
session created, and `InteractiveFlowSessionOAuth2Manager` reads and writes the record.

**`AuthorizationCodeManager` owns the code, one per grant.** The primary key over `grant_id` is what
makes a second impossible, and a session opens one grant, so a completion that lost the race is
refused at [the grant](grants.md#what-implements-it) before it reaches its code. The code names no
session: it outlives the one that issued it, and reaches it through its grant while the session is
there to be reached.

## What this document does not settle

**It does not keep a session between authorizations.** Every authorization signs the person in
anew, which is what makes `max_age` satisfied by construction —
[security](security.md#when-the-person-authenticated) argues it.

**It does not serve two submissions racing to complete it.** One that loses the version check while
the other is still completing fails the session, as [any conflict
does](interactive-flow.md#the-session), so neither is handed a code and the person starts again from
the client. One arriving after the other finished is a later request like any other.

**It does not resend a hand-off that never arrived.** A request that completed the session and
failed before answering leaves a code nobody was given; a later request is told the authorization
has completed, and the person starts again from the client. Resending the code would be the second
hand-off the rule above refuses.

---

← [How the system works](index.md)
