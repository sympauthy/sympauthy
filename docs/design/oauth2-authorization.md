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
transaction.** The authorization's terminal effect runs the scopes through the granting pipeline,
records the consent for the client's audience, consumes the invitation where one brought the person
here, opens the grant every token of the authorization will be issued under, and generates the
authorization code last, naming that grant. It runs inside [the completion
transaction](interactive-flow.md#the-engine), so a purpose refusing after it rolls the code back
with everything else, and a session never completes without one. Where no scope was granted and the
deployment does not allow access to a client without one, the session fails there instead.

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
token endpoint refuses a code whose session did not complete an authorization, has expired, or
names another client; then one whose PKCE verifier does not match the challenge. The redirect URI is
not compared again, as [OAuth 2.1](security.md#the-protocol-surfaces) has it: PKCE is what binds the
code to the request that produced it, so a code read off a redirect is useless without what only
that client holds.

**A code is issued under a grant, and exchanging it issues tokens under that grant.** The grant
copies what the person went through for — the client, the scopes and how each set was obtained, when
they authenticated, the claims asked for — so it outlives the session, and a refresh issues its
tokens under the same grant: [the grants](grants.md) are that record. A code whose grant was revoked
before it was exchanged is refused, and a second exchange of the code revokes it, as
[security](security.md#the-authorization-code) has it.

## What implements it

**`OAuth2AuthorizeInteractiveFlowPurposeHandler` is the purpose**: the steps, the second factor it
appends, the terminal effect, and that it hands off once — the member [the
engine](interactive-flow.md#the-engine) asks of an initiating purpose.
`InteractiveAuthFlowSessionManager.startAuthorizationWith` is where a request is validated and its
session created, and `InteractiveFlowSessionOAuth2Manager` reads and writes the record.

**`AuthorizationCodeManager` owns the code, one per session.** The primary key over `session_id` is
what makes a second impossible, and a completion that lost the race to write one is told so as the
concurrent modification it is, to be routed by where the winner left the session.

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
