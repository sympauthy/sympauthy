# The grants

A grant is what a client was allowed, and every token this server issues is issued under one. This
document is the grant: what opens one and what it records, what a token under it keeps of its own,
how a revocation and a lifetime reach it, what an administrator sees of it, and what collects it
once it is finished. How a token is checked when it is presented — its signature, the key it is
bound to, the `auth_time` it states, the id token — is [security's](security.md#tokens); the
authorization that opens a grant before handing out its code is [the OAuth2
authorization](oauth2-authorization.md), and whom a client may act as is
[security's](security.md#acting-as-a-person) too.

## What a grant is

**Every token is issued under a grant, and is live only while its grant is.** A grant is opened by
an authorization an interactive flow completes, a `client_credentials` request or a token exchange,
and records what every token under it has in common: the person if there is one, the client, the
audience, the scopes and how each set was obtained, when the person authenticated, and the claims
the authorization requested. A token records only what is its own — its type, the grant type it was
issued by, its expiry and the key it is bound to.

**An authorization opens its grant before it hands out its code, and the code names it.** The grant
is written as the interactive flow session completes, just before the code and in the same
transaction, once everything it records is decided — the scopes granted and consented, when the
person authenticated, the claims asked for. A session opens one grant and no more: a completion
that loses the race to another is refused at the grant, as the concurrent modification it is, and
rolls back with everything it wrote. Exchanging the code then issues tokens under that grant rather
than opening one.

**A refresh issues its tokens under the grant of the refresh token it was given.** Every token an
authorization produced, however often its client refreshed, is therefore under the one grant the
authorization opened, and naming that grant names all of them. The key a token is bound to is the
token's own, which is what lets a refresh bind a grant's later tokens to a key where its first were
bound to none.

**A grant opened by a token exchange records the grant of the token it was exchanged from.** That
is the acting client's own `client_credentials` grant, and an act-as token follows it: it is refused
once that grant is revoked, or once it has expired — past its lifetime, left with no token that has
not expired, or collected for either.

**A token cannot outlive its grant.** A token names the grant it was issued under, and a grant is
deleted only with its tokens, so there is no state in which a token stands without one and nothing
has to decide what such a token is worth. A token whose grant is revoked is refused on every surface
as a token this server never issued would be, and once the grant is collected it is one.

**A grant does not outlive its client.** A grant opened for a client the configuration no longer
declares is one whose tokens do not validate, and each of them is refused with a `401` on every
surface it is presented to. Removing a client is how a deployment withdraws it, and a client
withdrawn can no longer authenticate to refresh, introspect or revoke what it holds — so a grant
surviving it would be one nothing could withdraw before its tokens expired. The grant's scopes are
already read this way, each looked up among the scopes the deployment serves now; a client cannot
be dropped from a grant and leave the grant behind.

**It is refused, not revoked.** Nothing is written to the grant, so a client declared again under
the same identifier finds the unexpired tokens of its grants served again. [The design
FAQ](design-faq.md#does-a-grant-outlive-its-client) argues the alternatives.

## How a revocation reaches a grant

**Every revocation revokes grants, never a single token.** An administrator revoking the grants a
request names, a consent revoked, an authorization code replayed and a client's call to the
revocation endpoint each name the grants they reach, and every token under them stops working at
once. A revocation almost always means everything an authorization was issued, and that is one row
however often its client refreshed.

**The revocation endpoint revokes the grant of whichever token it is given**, a refresh token or an
access token. RFC 7009 §2.1 lets a server revoke the refresh token when it is presented an access
token, and a client revoking either is almost always signing the person out. A client that revoked
an access token to discard it while keeping its refresh token loses both, and signs the person in
again. [The design
FAQ](design-faq.md#does-revoking-an-access-token-revoke-the-refresh-token-beside-it) argues the
alternatives.

**Whoever holds an access token of a public client can therefore sign the person out of it.** The
endpoint authenticates a public client by its identifier alone and reads no DPoP proof, so a
resource server the token was presented to, a log it was written to, or a thief holding a token
bound to a key nothing else honours can revoke the grant it stands under. That is accepted: the
holder reads nothing and acts as nobody, the person signs in again, and a refresh token of a public
client in the same hands revokes as much. [The design
FAQ](design-faq.md#does-revoking-an-access-token-revoke-the-refresh-token-beside-it) weighs it.

**Revoking a grant revokes the grants opened by exchanging its tokens**, so a client revoking the
token it presented to a token exchange takes every act-as token issued from it out of service too.

**Revoking a consent revokes every grant of the account for its audience, act-as grants aside.** A
consent is the audience's rather than one client's, so each client of the audience acts under it,
and a revocation reaching only the client that last asked would leave the others acting for a
person who withdrew: their access tokens served until they expire, and their refresh tokens waiting
only for the person to consent again through any client of the audience. A grant records the
audience it was opened for, because which clients belong to an audience is configuration: a client
moved to another audience or removed and declared again would otherwise take its grants out of
reach of the consent they were opened under. An act-as grant rests on the act-as rules rather than
on a consent, which [security](security.md#acting-as-a-person) argues.

**A grant is the audience's it was opened for.** A client moved to another audience can no longer
use what it was granted in the old one: a refresh and a code exchange under such a grant are refused
with `invalid_grant` and `grant.mismatching_audience`, a token issued under one authenticates
nothing and is answered as inactive at introspection, and a `client_credentials` token issued under
one is not exchanged for an act-as token. Everything such a grant stands for — the `aud` of its
tokens, the claims they carry, the consent or the rule they rest on — is the old audience's, and a
client of the new one would answer for an audience it is no longer part of. A grant resting on a
consent would also escape it: tokens issued for the new audience would stand under a grant that
revoking the consent could not reach.

**A code still to be exchanged is reached with its grant.** A revocation finds the grant of a code
as surely as the grant of a token, so a person signed out, or a consent revoked, between the sign-in
and the exchange takes the code out of service too: its exchange is refused with `invalid_grant` and
`grant.revoked`, and the code is left unspent.

**A replayed authorization code reaches its grant through the code.** A second exchange revokes the
grant the code names, under [the replay rule](oauth2-authorization.md#a-code-is-exchanged-once): the
first exchange's tokens and every refresh descended from them are under it. A grant a stored code
names is kept as long as the code is, revoked or not, so every later replay finds it too.

**A grant is revoked once, and keeps the revocation that came first.** Every revocation reaches only
the grants still live — not revoked yet, not past their lifetime, and holding a token that has not
expired or a code still to be exchanged — so a row goes on saying who revoked it and when, and a
grant that lapsed on its own is not recorded as revoked afterwards. A revocation reaching many
grants therefore counts the ones it took out of service, and repeating it finds none.

**An administrator's revocation answers how many grants it revoked.** A grant is what was taken out
of service, and how many tokens stood under one depends only on how often its client refreshed.

## How long a grant lives

**A grant may be given an absolute lifetime, which no refresh extends.** Once
`auth.grant.expiration` has passed since a grant was opened, its refresh token is refused with
`invalid_grant` and `grant.expired`, whatever its own expiry, and so is its code if it was never
exchanged; the person signs in again however often the client refreshed. Left unset, a grant has no
lifetime of its own, and refreshing keeps it alive for as long as its refresh tokens allow.

**Every token under a grant expires no later than the grant does.** An access token issued a minute
before the grant ends lives that minute, and states it as its `exp`, so a resource server that is
not this one stops honouring it when the grant stops. That is also what lets a grant past its
lifetime be collected: every token under it has expired by then.

**A refresh token's signed expiry is its own lifetime, not the grant's.** Nothing reads a refresh
token but this server, and the grant is what refuses one presented past the grant's end — with the
code saying so, rather than the one a token that ran out on its own is refused with. Its stored
expiry is the earlier of the two, as every token's is, so the grant says so only until it is
collected: a refresh presented after that names a token this server no longer holds, and is refused
with `token.invalid_token_id` like any other.

## What is collected

**A grant is kept while a token or an authorization code names it.** A scheduled job deletes every
expired token, every token of a revoked grant and every code past [its
lifetime](oauth2-authorization.md#how-long-a-code-is-exchangeable), exchanged or not, and then every
grant left with neither; a grant whose code was never exchanged therefore goes once the code does. A
refresh adds an access token to its grant every time, so a grant refreshed for months would
otherwise keep every one of them.

**A revoked grant is deleted on the next run, with its tokens.** Until then an administrator reads
who revoked it and when; afterwards it is answered as a grant this server does not hold, and a token
whose grant is gone is refused as surely as one whose grant is revoked. A record kept past that in
case somebody wants it would be an audit trail, which no surface shows. A refresh token without an
expiry keeps its grant for as long as it is not revoked. [The design
FAQ](design-faq.md#is-a-revoked-grant-kept) holds the retention that lost.

**A provisional account is not collected while a grant names it**, as [the provisional
user](provisional-user.md#collecting-one-that-never-will) has it of every row that refers to an
account and is not the account's own.

## What an administrator sees of a grant

**Grants are a collection of their own on the administration surface, and a person's grants are the
ones filtered on the person.** A `client_credentials` grant has no person behind it, so a collection
nested under a person could never show one, and could not answer what a client or an audience holds
either. An act-as grant is among the grants of the person it acts as. [The design
FAQ](design-faq.md#where-does-an-administrator-read-a-grant) weighs the nesting that lost.

**A grant is found by typing the person's address or the client's identifier.** The free text
matches the identifier claims of the person, the client and the audience, and a client or an
audience is filtered by its identifier while the filter shows it under its display name, which
depends on the reader's language. One the configuration no longer declares is offered too, under its
identifier, since its grants are still stored and still revocable. [The design
FAQ](design-faq.md#what-does-the-free-text-of-the-grant-collection-match) holds the names that lost.

**Every grant still stored is listed, each with where it stands.** A grant is *live* while a
revocation made now would reach it, *revoked* once one has, *expired* past its lifetime, and
*lapsed* when it is neither revoked nor past its lifetime but is left with no token that has not
expired and no code still to be exchanged. A revoked grant is revoked whatever else holds of it,
because the revocation is what somebody acted on, and a grant past its lifetime is expired rather
than lapsed, since every token under it has expired by then too. An administrator who has just
revoked a grant therefore sees that it took, until the grant is collected; [the design
FAQ](design-faq.md#does-the-grant-collection-list-a-grant-that-is-no-longer-live) holds why the
collection is not of live grants only.

**An administrator revokes the grants a request names, and leaves the rest.** A person's stolen
phone and their laptop are two grants of the same client, and signing the person out would take
both. The request names grants by identifier, by the person holding them, and by the client and the
audience they were opened for, each set narrowing the others, so one route revokes a grant, a
person's grants — signing them out — a person's grants on one client and everything a client holds.
Each grant records that an administrator revoked it and which one, the grants exchanged from it go
with it, and one no longer live is left as it was, as every revocation does. A request naming no set
is refused rather than read as every grant, and an empty set reaches none. It is an action rather
than a deletion, since the grants stay readable; [the design
FAQ](design-faq.md#how-does-an-administrator-revoke-grants) argues the shape.

**Reading grants and revoking them take scopes of their own.** A `client_credentials` grant has
nothing to do with a consent, and a grant says more than a consent does — when the person
authenticated, and the claims an authorization asked for. Revoking a consent stays under the consent
scope and still revokes the grants it reaches: the scope gates the action rather than what follows
from it. [The design FAQ](design-faq.md#are-grants-read-and-revoked-under-the-consent-scopes) holds
the reuse that lost.

**Nothing an administrator reads of a grant is about the tokens under it.** When it was last
refreshed and when its last token expires are the tokens' own, and listing them is left open below.
[The design FAQ](design-faq.md#what-does-the-detail-of-a-grant-show) holds what the detail weighed.

## What implements it

**`Grant` is the model and `grants` the table, and a token row names its grant under a foreign
key.** That key is what makes a token without a grant impossible rather than a case to handle. The
authorization code names its grant under a foreign key of its own, which is what keeps the grant of
a code from being collected while the code is kept. [Why it is called a
grant](design-faq.md#why-is-the-record-a-token-is-issued-under-called-a-grant) is the design FAQ's.

**`OAuth2AuthorizeInteractiveFlowPurposeHandler` opens an authorization's grant in its terminal
effect**, after the scopes are granted and the consent recorded and before
`AuthorizationCodeManager` generates the code naming it. It takes no lock of its own: the completion
transaction already holds the lock over the account, which the consent saved before it takes, or
the promotion of a sign-up named beside its identities. A unique index over the session a grant came
from is what settles two completions racing: the one refused there never reaches its code.

**`GrantManager` opens a grant, checks one presented to it and revokes grants**, and is where every
revocation above is written: whether a grant is live, and whether it was opened for the client and
the audience presenting it, are asked of it by a refresh and a code exchange alike. The use case of
each grant type in `business.manager.token` issues the tokens under a grant, and `TokenManager` reads
one back once issued; `ActorGrantValidator` is the check an act-as token adds.

**A refresh, a code exchange and a revocation of the same person's grants take turns.** A token
issued under a grant is revoked with it whichever commits first, because a token's liveness is read
off its grant. What the turns protect is the two judgments made from committed rows. A revocation
reads a grant live from the tokens and the code committed under it, so a refresh renewing a grant
whose last token is about to expire, or an exchange spending a code about to expire, would otherwise
have its tokens written after the revocation judged the grant lapsed, and left live. An exchange
reads its grant live before spending the code, so a revocation committing in between would
otherwise leave the client handed tokens that are already dead, with the code spent. All of them
therefore take the lock over the account first, as [the locking
standard](../standards/locking-standard.md#the-named-lock) describes. Whichever goes second sees
what the first committed: a refresh or an exchange reads its grant revoked and is refused, or a
revocation finds their tokens and revokes their grant.

**`GrantStatus` is where a grant stands, and `GrantManager` reads it.** A grant is live when it
passes the condition every revocation in `GrantRepository` writes under, read at the same moment,
so a grant listed live is one a revocation then would take out of service. The rest is read off the
grant itself. `GrantCollectionManager` is the administrator's collection, read in memory like every
other one there, and it reads the identifier claims its free text matches only when one was sent.

**`GrantCleaner` is the collecting job**, run by `CleanFinishedGrantCron` under a
[lease](../standards/locking-standard.md#the-lease) like every job one instance is enough for. It
deletes a grant only in the transaction deleting its tokens and its code, and a token or a code only
some minutes after it expired, so a refresh that found its token unexpired a moment before, or an
exchange its code, still finds the grant to write under.

**A grant keeps the interactive flow session it came from, and only a trace reads it.** The session
is collected long before most grants end, so the reference is left dangling by design: it is what
the unique index keys a session's one grant on, and it lets somebody reading the store trace a grant
back to its flow while that flow is still there. A token request under the grant carries it as
`session.id`, linked to the span that started the session, which the grant keeps beside it — see
[telemetry](telemetry.md#the-requests-of-one-flow-session).

## What this document does not settle

**It does not rotate refresh tokens.** A refresh token is presented as often as its client likes
until it expires, and one replaced by a refresh is not refused afterwards, so a refresh token that
leaked is not recognised as a replay when the thief and the client both use it.

**It does not show a grant to the client it was opened for.** The OpenID Grant Management API, which
puts a grant's identifier on the wire for a client to query and revoke, is not served; the name
leaves room for it.

**It does not keep a record of revocations.** A revoked grant is deleted on the next run, and with
it who revoked it and when; an audit trail is a gap
[security](security.md#what-this-document-does-not-settle) names of its own.

**It does not list the tokens under a grant.** An administrator reads a grant and what it records,
and nothing of the tokens issued under it: how often it was refreshed, and when its last token
expires, wait for a collection of their own.

---

← [How the system works](index.md)
