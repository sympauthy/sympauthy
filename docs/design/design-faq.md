# Design FAQ

Questions that came up during development, the options considered, and what was decided. It exists
so that a settled question stays settled, and so that a decision that looks arbitrary from the code
can be read with its reasoning attached.

**An entry belongs here when the answer is a choice rather than a rule.** A rule — something every
future change has to hold to — belongs in the standard that owns it, where it will be found by
someone about to break it. A choice is something that was decided once, could defensibly have gone
the other way, and does not generalise: it is only ever read by someone asking "why is it like
this?"

Each entry names the decision, the options that lost, and why. An entry may be reopened, and is
edited in place when it is.

---

## Cryptographic keys

### Why is `public_key` `NOT NULL`, holding an empty array where there is no public key?

**Decision:** The column is `NOT NULL`, the row of a symmetric key holds an empty array in it, and
`StoredPublicKeyMapper` translates that array into the `null` the domain spells absence with.

**Options considered:**

- **A nullable column** — what the schema had, and the natural spelling of a key with no public
  half.
- **`NOT NULL`, an empty array, translated in the mapper** — the entity mirrors the column and the
  business model keeps its null.
- **`NOT NULL`, an empty array, carried into the domain** — `CryptoKeys.publicKey` non-null too, and
  every reader of a key testing `isEmpty()` where it tested `== null`.

**Rationale:**

The first option is not available. A null cannot reach a `bytea` column through this stack at all:
`micronaut-data-r2dbc` binds a null `ByteArray` as a `Byte[]`, r2dbc-postgresql encodes that as
`smallint[]`, and PostgreSQL refuses it.

```
[42804] column "public_key" is of type bytea but expression is of type smallint[]
```

The base `QueryStatement` that binding overrides binds the same case as a `byte[]`, which the driver
encodes as a `bytea`, and no `DataType` reaches it — `BLOB` binds as an `Object`, which the driver
refuses in turn. H2 accepts the null either way, so the nullable column was a state one dialect
stored, the other refused, and only an insert could tell the two apart.

Between the two spellings that remain, the mapper is where this codebase already puts the difference
between a row and a model. Carrying the empty array into the domain would spread one storage
limitation across every reader of a key, and `HMACKeyImpl` — the one algorithm with no public half —
would go on saying so with a workaround rather than with a null.

What it costs is a column holding a value that means no value, which is what the database standard's
*absence is spelled `NULL`* forbids. The deviation stops at the mapper, and the standard names the
`bytea` exception so that the next binary column is written knowing it.

---

## MFA

### Should clients control whether MFA is required, and which methods are enabled?

**Decision:** No. MFA policy is global.

**Options considered:**

- **Global policy only** — the requirement and the enabled methods apply to every client uniformly.
- **Per-client overrides** — each client's configuration could carry its own requirement and method
  list.

**Rationale:**

SympAuthy is built around a single pool of accounts shared by every client. Per-client MFA control
would produce a confusing experience — the same person challenged on one application and not on
another — and a real bypass, since a client could opt out of a policy the deployment enforces
globally.

The deeper reason is that enrolment is a property of the person, not of the client: someone has one
authenticator regardless of which application sent them here. A policy attached to the client would
be describing something the client does not own.

Audiences group clients for the purposes of consent, but they do not create separate populations of
accounts. Per-audience MFA policy is the version of this that could be reconsidered later;
per-client is not.

---

## Tokens

### Is a grant that did not ask for `openid` owed an id token?

**Decision:** No. One rule covers every response this server sends: an id token is issued where the
grant carries `openid`, and `IdTokenGenerator` answers that for the authorization-code exchange and
the refresh alike.

**Options considered:**

- **Gate both** — ask the condition once, where both grant paths pass, and stop issuing an id token
  to a grant that did not request an identity.
- **Ungate both** — drop the condition from the refresh as well, and go on issuing one either way.

**Rationale:**

OpenID Connect Core §3.1.2.1 makes `openid` REQUIRED of an authentication request. A request without
it is a plain OAuth 2.0 request, and RFC 6749 §5.1 defines no `id_token` in the response to one. An
id token is the only thing this server signs that says who a person is, and a client that did not
ask to know who they are is being answered a question it never put.

Ungating was the compatible answer, and what decided against it is where this server is. It has no
stable release, so the deployments a break reaches are few and still correctable by hand. The same
change made after a 1.0 would cost every one of them a migration.

**What a deployment sees.** A client requesting `openid` is unaffected: the scope is auto-granted
when requested. A client that does not request it stops receiving an `id_token` in either response,
and reads the person's claims from `/userinfo`, which is the endpoint for exactly that.

**What correcting one takes.** The client sends `openid` in its authorization request, which is what
a client wanting an id token was always meant to send. Where the deployment names `allowed-scopes`
for that client, `openid` joins it — a scope outside that set is refused as
`scope.parse_requested.not_allowed` rather than ignored — and it joins `default-scopes` too where
that is named, for a request that sends no `scope` at all. The two are [held to each
other](#may-a-clients-default-scopes-fall-outside-its-allowed-scopes), so naming it in one and not
the other is refused at startup.

---

### Does a refresh issue a new id token?

**Decision:** Yes, where the grant is one an id token is owed at all. The refresh response carries
an `id_token` beside the access token, holding the subject and the audience of the original
authentication and issued under its grant, with a fresh issue date, expiry, claim set and
`at_hash`.

**Options considered:**

- **Issue one** — the refresh reissues the identity as well as the access, from what the refresh
  token already records.
- **Issue none** — and delete the `IdTokenGenerator` overload written for it. A client wanting
  current claims calls `/userinfo`, which is the endpoint for exactly that.

**Rationale:**

Either would conform. OpenID Connect Core §12.2 says the refresh response is the token response of
§3.1.3.3 "except that it might not contain an `id_token`", so this is a choice, and what settles it
is what a client holding a long session can otherwise know.

An id token is the only thing this server signs that says who the person is. Issued once at sign-in
and never again, it describes the account as it was then: a claim the person has since corrected
stays wrong in it until the session ends. `/userinfo` answers with the current values, but it
answers only to a client that asks — and a relying party that validates an id token and reads its
claims, which is the ordinary shape of one, has no reason to.

Refresh is also the one moment after sign-in when this server rechecks consent, and it already
refuses a refresh the consent behind which was revoked. The identity it reissues is therefore one a
live consent stands behind, which is not true of the id token still sitting in the client.

**What is read again, and what is not.** The claim values are; the consented scopes that filter them
are the set recorded with the grant, which each refresh copies forward. Consent here is a yes or a
no — revoking is what a person does to it, and a revoked consent stops the refresh rather than
narrowing what it issues. Filtering on `Consent.scopes` as it stands at each refresh would be a
different decision from this one.

Which grants are owed an id token at all is the question above, and it is not asked a second time
here: a refresh reissues what the authorization it descends from was issued, or issues nothing.

---

### Does a token state `auth_time` only when a client asked for it?

**Decision:** No. Every token issued for a person states it — the id token, the access token and
the introspection response alike — whether or not the authorization carried `max_age`.

**Options considered:**

- **Only where `max_age` was asked**, which is the minimum OpenID Connect Core §2 allows: the claim
  is REQUIRED when the request asked for it and OPTIONAL otherwise.
- **Always in the id token**, and nowhere else.
- **Always in the id token, the access token and introspection**, which is what was built.

**Rationale:**

The whole point of the claim is that somebody downstream can tell a password typed a minute ago
from one typed a month ago. The party that needs to do the telling is a resource server, and a
resource server does not make the authorization request — so making the claim conditional on a
parameter the *client* sent decides, at authorization time, whether a rule the resource server has
not been written yet will be enforceable. A deployment that adds a recency rule later would have to
go round every client and have them send `max_age` before the rule could refuse anything, and until
they all had, the rule would fail open on exactly the tokens it was written for.

Nothing is disclosed by stating it unasked. `auth_time` is a property of the authentication rather
than an attribute of the person, and the client it reaches is the one that just drove that
authentication.

The access token carries it because RFC 9068 §2.2.1 puts it there, and because a resource server
that is not this one holds an access token and no id token. Introspection carries it because
RFC 9470 §6.2 puts it there, for the resource server that validates by asking rather than by
verifying a signature.

The cost is a claim in every token nobody may read. That is the shape of `iat` and `jti` too.

---

### Is `iat` the recency a resource server should read?

**Decision:** No, and that is the gap `auth_time` closes rather than a second way of saying the
same thing.

**Options considered:**

- **`iat`** — the token is already stamped with it, and no column, no claim and no plumbing would
  have been needed.
- **A claim of its own**, recorded where the authorization is recorded and reissued unchanged by
  every refresh.

**Rationale:**

`iat` is the minting time. A refresh mints a new token whenever the client likes — for thirty days
under the shipped `auth.token.refresh-expiration` — so an authentication thirty days old shows an
`iat` of a minute ago on every refreshed token. A resource server reading recency off it would let
through precisely the unattended caller it was asking about, and would do so silently.

---

### Is a stale authentication answered with a challenge or with a re-authenticated flow?

**Decision:** With the challenge RFC 9470 §3 defines — a `401` naming
`insufficient_user_authentication` and the `max_age` the operation demands — rather than with a
flow of its own.

**Options considered:**

- **The RFC 9470 challenge.** The client re-authorizes with `max_age` and retries with the token it
  is issued.
- **A re-authenticated edit session**, Keycloak's shape for its sensitive actions, and what #470
  keeps for changing an identifier.
- **Refuse, and say nothing about how to succeed.**

**Rationale:**

The second is right where the *new value* has to be proven as well as the person — changing the
address an account signs in with is that, and it stays a flow. For a claim write there is no new
value to prove: the question is only whether the person is there, and a redirect already answers
it. An edit session would cost a browser round trip through a purpose of its own on every write,
to obtain what one redirect obtains.

The third is what a bare `403` would be. A client cannot tell a permission it will never have from
one it could have by sending the person through a redirect it already knows how to make, so the
challenge is the difference between an error a client can act on and one it can only report.

The challenge names `max_age` alone of the two parameters RFC 9470 allows. `acr_values` would mean
inventing a vocabulary of authentication strengths for one value, and advertising
`acr_values_supported` for a server that offers no choice: an account is proven by its credential,
and by its second factor where one is enrolled.

---

### Is a refusal owing several challenges answered with one, or with all of them?

**Decision:** With all of them, one challenge per error, repeated under the scheme the token came
under, in a fixed order — the scope, the age, the binding.
[Security](security.md#a-refusal-carrying-several-challenges) holds the rule.

**Options considered:**

- **Every challenge, the scheme repeated for each.**
- **One challenge, the first in a fixed priority**, the others left for the retry to discover.
- **One challenge per scheme carrying every error**, as a list in `error`.

**Rationale:**

The third is not available: RFC 6750 §3 gives `error` a single code, and a client parsing it as one
would match none of them.

The second is what a client reading only the first challenge of a scheme gets from the first anyway,
and it costs every other client a round trip per condition: told to re-authenticate, it comes back
with a fresh authentication, is refused for the binding, and goes through the redirect a second time
for what one redirect would have obtained. It is also the under-reporting [the ACL's
expression](#is-a-claims-acl-a-set-of-flags-or-an-expression-per-door) rejected when it chose to
report every term that failed; writing one challenge would throw away at the header what the manager
was built to derive.

Repeating a scheme is allowed by RFC 9110 §11.6.1 and is rare in practice, so a client library may
read the first `Bearer` challenge and ignore the next. The fixed order is what makes that client
correct as far as it goes.

---

### Should a configuration that makes a large token be refused?

**Decision:** No. What an audience publishes is what the token carries, for an id token and for an
access token alike, and nothing caps the number of claims, the size of a value, or the serialized
token.

**Options considered:**

- **Refuse at startup** a configuration whose audience publishes past some ceiling of claims or
  bytes.
- **Refuse at issue**, when a token is actually built and its size is known.
- **Warn at startup** and issue the token anyway.
- **Cap nothing**, and publish what the deployment asked for.

**Rationale:**

The limits that bite are not this server's. An id token is returned in a response body rather than
a header, so this server hands over whatever it built; what fails is downstream — a browser cookie
a client chose to store it in, an `id_token_hint` on a logout request travelling as a query
parameter, a proxy's header ceiling on a request carrying it. Each of those belongs to a component
this server does not know it is talking to, and a deployment whose clients hold the token in memory
meets none of them.

A startup ceiling would therefore refuse a configuration that works. It cannot be computed from the
configuration either: what a token carries depends on the claim values a person actually holds, so
the only honest startup figure is a worst case over value lengths nothing bounds. Refusing at issue
is worse — it turns a sizing problem into a failed sign-in, at the moment a person is waiting, for
a token the client may have been perfectly able to hold.

Warning at startup is the option that nearly wins and loses on where the warning goes. It would fire
on every boot of a deployment that has already decided it is fine, which is the shape of message an
operator learns to scroll past, and it would say nothing about the client that is actually in
trouble. The deployment that hits a real limit finds out from the component that imposed it, and
what it changes in response is `published-in` on the claims it does not need in the token — which is
the setting it already owns, readable off the administration API.

**An access token meets a limit more often, and it is the same limit.** It travels in an
`Authorization` header on every request rather than once in a response body, so the ceiling it hits
is a proxy's or a resource server's rather than a browser's — a component this server knows even
less about, and one a deployment discovers by trying. Nothing here changes: a header a token no
longer fits in is reported by whatever imposed the header, and the deployment answers by naming
fewer places for its claims.

What the chosen option costs is that a deployment can configure a token too large for a client or a
resource server it has, and nothing says so until that party fails. It is the same trade [the silent
answer is the withholding one](claims.md#where-a-claim-is-published) makes from the other direction:
the defaults keep a value back rather than publish it, so reaching a size worth worrying about takes
a deployment writing `published-in` on claim after claim deliberately.

---

### Does a grant outlive its client?

**Decision:** No. Every token of a grant opened for a client the configuration no longer declares is
refused with a `401` whenever it is presented, and the grant is left as it is.

**Options considered:**

- **Honour its tokens until they expire** — they were valid when they were issued, and removing the
  client already stops it opening another grant.
- **Refuse its tokens when they are presented** — the client is looked up on every request, beside
  the grant.
- **Revoke it** — write the revocation to every grant naming the client, once the client is found
  missing.

**Rationale:**

Honouring it leaves a grant nothing can withdraw. Revocation under RFC 7009 is the client's own and
needs it to authenticate, which a client the configuration no longer declares cannot do; an
administrator revokes by person, and a `client_credentials` grant has none. Its tokens would serve
until they expired, and the operator who removed the client would have no way to say otherwise.

Revoking writes rows because a file changed, which nothing in this server does at startup, and it
cannot be taken back: an identifier mistyped in an edit would sign every person of that client out
for good, where refusing costs them only until the edit is corrected. Revocation also records who
revoked, and no person and no client did.

What refusing costs is that a client declared again under the same identifier finds the unexpired
tokens of its grants served again. A refresh is the client's own and needs it to authenticate, so
what returns is what the client still holds, for as long as it has left to run.

---

### Does any presentation of a spent authorization code revoke what it was exchanged for?

**Decision:** No. A spent code revokes the grant it was issued under only when it is presented by
an exchange that passes client authentication and the PKCE verifier — one that would have been
issued tokens had the code not been spent. Any other request presenting it
is refused for what it got wrong, and revokes nothing.

**Options considered:**

- **Revoke on any presentation** — the literal reading of RFC 6749 §4.1.2, *"If an authorization
  code is used more than once"*, checking the code before anything else the request carries.
- **Revoke on an exchange that would otherwise succeed** — the code is spent at the moment tokens
  are issued, and a request is a second exchange only where it reached that moment.
- **Never revoke** — refuse the second exchange, which is all the specification requires, and leave
  the tokens of the first in service.

**Rationale:**

Revocation is there for one case: a code that reached somebody able to exchange it, who did so
before the client it was issued to. The client's own exchange is then the second, and revoking is
the only way the first set of tokens comes back. Never revoking leaves that case uncontained, and it
is the case RFC 6749 recommends against leaving.

Being able to exchange a code here means holding its verifier. PKCE is required of every
authorization request this server accepts, and the verifier never leaves the client that generated
it unless that client is compromised. A code on its own leaks routinely — into a server log, a
browser's history, a `Referer` header — and a party holding only that cannot be issued anything. Had
any presentation revoked, that same party could end the person's session at the client whenever it
liked, with something it had been handed for free; a recommendation qualified *"when possible"* is
not one that asks for that.

Nothing is left uncontained by it. A request refused before the code is spent was never issued
anything, so the tokens it leaves in service are those of an exchange that passed every check — and
where nobody but the client holds the verifier, that exchange was the client's own.

---

### How long is an authorization code exchangeable?

**Decision:** For a lifetime of its own, `auth.authorization-code.expiration`, counted from when the
authorization hands it out — a minute unless a deployment says otherwise.

**Options considered:**

- **Until the session that issued it expires** — what this server did while a code copied the
  expiry of its session, which is given the whole time a flow is allowed when it starts.
- **A lifetime of its own, ten minutes by default** — the most RFC 6749 §4.1.2 recommends.
- **A lifetime of its own, a minute by default.**

**Rationale:**

A code is what leaks — into a log, a browser's history, a `Referer` — so how long it stays
exchangeable is how long a leaked one is worth something, and that is a security property a
deployment should be able to set. Ending it with the session made it depend on how fast the person
was instead: one who signed in within a minute handed the client a code exchangeable for almost the
whole of the flow's time, and one who took nearly all of it handed over a code with a moment left,
so the client most likely to be refused was the one whose person had been slowest.

A client exchanges a code as soon as the person is redirected back to it, so the lifetime has to
cover that one request, a slow network and a retry, and a minute does. Ten would add nine minutes no
client needs, and the only party they help is one holding a code that leaked.

---

### How long is a spent authorization code kept?

**Decision:** Until its own lifetime ends. A code is collected then whether or not it was exchanged,
and one presented after it is refused as expired and revokes nothing.

**Options considered:**

- **Until its session is collected** — what this server did while a code went with its session.
- **Until its lifetime ends.**
- **Until its grant is collected** — a replay recognised for as long as anything stands under the
  grant.

**Rationale:**

A spent code is kept so that a replay is recognised, and revokes the grant [the OAuth2
authorization](oauth2-authorization.md#a-code-is-exchanged-once) says it does. That revocation
answers one race: a code reaching somebody who exchanged it before the client did. Both of them
exchange within the code's lifetime — the client because it exchanges as soon as it is redirected —
so a code kept that long recognises every replay the race can produce.

Keeping it until its grant is collected would recognise a replay for as long as the grant lives, but
a grant is kept while a code names it, so the two would keep each other alive long after the race
was over. Keeping it until its session is collected tied it to the time a flow is allowed, which no
longer bounds anything about the code.

---

### Does revoking an access token revoke the refresh token beside it?

**Decision:** Yes. The revocation endpoint revokes the grant of whichever token it is given, so an
access token presented to it takes the refresh token of its grant, and every other token under it,
out of service with it.

**Options considered:**

- **Revoke the grant, whichever token is presented** — RFC 7009 §2.1 allows a server to revoke the
  refresh token when handed an access token.
- **Revoke a single access token, and the grant only when handed a refresh token** — what this
  server did while revocation was written to tokens one by one.
- **Revoke an access token by deleting it** — the refresh token survives, and nothing is recorded.

**Rationale:**

A client revoking a token is almost always signing the person out, and the refresh token is the
part of that it cannot do on its own: an access token it discards expires within minutes anyway.
Revoking one access token would also need a revocation on the token as well as on the grant — two
places a token's liveness is read from, and the rule that a revocation is recorded once held on
both of them.

Deleting the access token instead keeps the refresh token without that second record, but it is the
one revocation nothing records, and introspection could no longer tell it from a token this server
never issued.

What it costs is twofold. The rare client that revoked an access token to discard it while keeping
its refresh token loses both, and signs the person in again. And an access token is shown to every
resource server it is used against, where a refresh token is shown to nobody: for a public client,
which the endpoint authenticates by its identifier alone and asks no DPoP proof of, whoever sees one
of its access tokens can revoke the grant it stands under — a resource server, a log, or a thief
holding a token bound to a key nothing else honours — and sign the person out of that client.
Nothing throttles the endpoint either, as
[security](security.md#what-this-document-does-not-settle) has of every surface.

That second cost is accepted. The holder reads nothing and acts as nobody, so what is lost is a
sign-in, which a refresh token of the same client in the same hands could already cost the person;
RFC 7009 §2.1 asks no more of a public client than that the token was issued to it, which is the
check the endpoint makes; and revoking one access token for a public client while revoking the
grant for a confidential one would be two revocations to read a token's liveness from, where the
rationale above wants one.

---

### Is a revoked grant kept?

**Decision:** No. The job collecting expired tokens deletes a revoked grant, with its tokens, on
the next run after the revocation — once no stored authorization code names it.

**Options considered:**

- **Delete it on the next run** — a grant gone is refused as surely as a revoked one.
- **Keep it for a retention period** — a configuration key saying how long who revoked a grant, and
  when, is kept after it is written.

**Rationale:**

Who revoked a grant and when is read only while the grant is stored: an administrator sees it on the
grant until the next run, which is long enough to see that a revocation took. A token whose grant is
gone is a token this server does not know, which every surface refuses already, so keeping the row
longer changes nothing a caller's tokens observe. A retention period would be a configuration key
and a second rule for a record kept after it stopped mattering to anything it governs: an audit
trail, which [security](security.md#what-this-document-does-not-settle) leaves to a feature of its
own.

The code is the exception, and not for the record's sake: a grant a stored code names is kept so
that a later replay of the code reaches it, and is collected once the code is.

---

### Why is the record a token is issued under called a grant?

**Decision:** It is a grant: `Grant` in the code and `grants` in the schema.

**Options considered:**

- **A grant** — what node-oidc-provider calls the same record, and what the OpenID Grant Management
  draft puts on the wire as `grant_id`.
- **An authorization** — what OpenIddict and Spring Authorization Server call it.

**Rationale:**

"Authorization" is already the flow's word, the code's and the server's: the authorize endpoint,
the authorization code, the OAuth2 authorization an interactive flow session serves. A
`client_credentials` grant is also nobody's authorization — no person was asked anything. A grant is
what a client was allowed, whichever grant type opened it, and the name leaves room for exposing it
to clients through Grant Management without renaming anything.

---

### Does acting as a person need their consent?

**Decision:** No. A token exchange is allowed or refused by the act-as rules alone and reads no
consent, so a client may act as a person who has never signed in to its audience.

**Options considered:**

- **Require an active consent for the acting client's audience** — the exchange refused where
  the person has none, so that revoking a consent stops every client acting for them there.
- **The act-as rules alone** — who may be acted as is the deployment's to decide, and a consent
  stays what a person agreed to in a flow.

**Rationale:**

Acting as a person who has never signed in is a case the exchange is meant to serve, and that person
has no consent to give: a consent is recorded by a flow, and they have been through none. Requiring
one would refuse exactly that case.

What it costs is that revoking a consent does not stop a client acting as the person. Nothing an
act-as token carries rests on the consent — it holds no scope the person consented to — so revoking
one leaves every act-as token in service, and a client free to exchange for another.

---

### Can an act-as token be issued for another audience than the acting client's?

**Decision:** No. The exchange issues an act-as token for the audience the acting client belongs to,
and refuses an `audience` naming any other with `invalid_target`.

**Options considered:**

- **Issue it for any configured audience the exchange names** — what the exchange first did.
- **Issue it for any audience, and leave out the claims restricted to it.**
- **Let a client exchange another client's token, so that a client of the named audience is the one
  acting.**
- **Issue it for the acting client's audience alone** — what was chosen.

**Rationale:**

A client belongs to exactly one audience, and an audience is the set of applications entitled to its
claims. An act-as token for another audience is held by a client outside it, which reads the claims
restricted to that audience off the token it was handed, and the rule deciding the exchange would be
keyed on claims of an audience the client has no part in. Leaving those claims out does not settle
it: the token's `aud` still tells that audience's resource servers that a client outside it is one
of theirs.

Exchanging another client's token breaks [the client proving it is the actor with a token of its
own](security.md#acting-as-a-person): the rule would be decided for the client that authenticated
while the token named the other, and holding a bearer token of a client would be enough to act as
people under its name.

What it costs is crossing audiences, which the on-behalf-of exchange is meant to do. It will have to
say who acts in the audience it crosses into before an act-as token may name one.

---

### Does the discovery document list RS256, as OpenID Connect Discovery requires?

**Decision:** No. `id_token_signing_alg_values_supported` lists the one algorithm id tokens are
signed with, `advanced.jwt.public-alg`, which is ES256 unless a deployment chooses otherwise.

**Options considered:**

- **List RS256 anyway** — meet the letter of §3 while signing with something else.
- **Ship RS256 as the default** — conform out of the box, and leave ES256 and PS256 to a deployment
  that departs knowingly.
- **Hold an RS256 key beside the configured one** — list both, and sign with whichever a client's
  configuration names.
- **Always sign with RS256** — and drop `public-alg`.

**Rationale:**

OpenID Connect Discovery 1.0 §3 has every server include RS256, and FAPI 2.0 §5.4.1 allows PS256,
ES256 and EdDSA and nothing else, retiring the PKCS#1 v1.5 signature RS256 is. The two cannot both
be met, and where this server's specifications disagree it takes the newer, stricter text, as it
takes OAuth 2.1 over RFC 6749.

Listing RS256 without signing with it is the failure the discovery document exists to prevent: a
client configuring itself from the document would expect a signature it never receives. Shipping
RS256 as the default meets §3 by stepping back from the profile the server otherwise follows, and
always signing with it does so with no way back. A key per client meets both, at the cost of a
second id-token key in the key set, a choice in every client's configuration and an `at_hash`
computed per algorithm — machinery bought for a client that cannot verify ES256.

**What a deployment sees.** A client verifying an id token with the key the key set publishes under
its `kid` is unaffected. A client registering no algorithm and holding to the RS256 default OpenID
Connect Core §3.1.3.7 gives it rejects the id token, and a deployment serving one sets
`advanced.jwt.public-alg: rs256`, which the document then lists.

---

## Grants on the administration surface

### Where does an administrator read a grant?

**Decision:** In a collection of its own, `/api/v1/admin/grants`, where a person's grants are the
ones filtered on `user_id`.

**Options considered:**

- **A collection of its own** — every grant, filtered on the person, the client or the audience.
- **Nested under the person, `/users/{userId}/grants`, as consents are.**

**Rationale:**

A `client_credentials` grant has no person behind it. Nested under a person, it could never be
shown, and neither could what a client or an audience holds, which are questions an administrator
asks of grants as often as who holds them. A console's page for a person gets that person's grants
by filtering, which costs it nothing.

---

### Does the grant collection list a grant that is no longer live?

**Decision:** Yes. Every grant still stored is listed with its status — `live`, `revoked`,
`expired` or `lapsed` — until the cleanup job collects it.

**Options considered:**

- **Every stored grant, with its status.**
- **Live grants only** — what a revocation could still reach.

**Rationale:**

An administrator who has just revoked a grant would see it vanish from a collection of live grants,
which reads the same as the revocation failing and the grant being deleted. Shown as `revoked`, the
revocation is seen to have taken, and so is what took it — its client, a consent revoked, a replayed
code, an administrator. `status=live` is one criterion away for a console that wants the other
reading.

---

### How does an administrator revoke grants?

**Decision:** `POST /api/v1/admin/grants/revoke`, its body naming the grants by `grant_ids`,
`user_ids`, `client_ids` and `audience_ids`, which narrow together. It answers `200` with how many
grants it revoked. A body sending none of them is refused with `grant.revoke.no_criterion`, and an
empty list leaves no grant to revoke.

**Options considered:**

- **One route on the collection, a body naming the grants.**
- **`POST /api/v1/admin/grants/{grantId}/revoke`, answering the grant** — one grant per call, the
  force logout's paths under the person, `/users/{userId}/logout` and `…/logout/{clientId}`, kept
  beside it.
- **`DELETE /api/v1/admin/grants/{grantId}`, answering `204`** — the shape consents are revoked
  with.
- **Revoking what the collection's criteria keep** — a bulk revocation over a filter.
- **Answering the grants revoked** rather than how many.

**Rationale:**

What an administrator revokes differs only in which grants it reaches: a stolen phone, a person, a
person on one client, a client withdrawn. One route with a set for each is one shape for all of
them. The force logout's two paths were narrower spellings of it, so they are removed rather than
kept as siblings to hold in step; no release had shipped them under the grant scopes, and a console
signing a person out sends their `user_ids`.

The body names identifiers only. The collection's criteria also hold the free text, which matches
identifier claims partially, and ranges of dates, so a revocation over them reaches grants nobody
named. An absent set narrows nothing and an empty one reaches nothing, so a console sending an empty
selection revokes no grant rather than every one; a body sending no set is refused for the same
reason. A client or an audience is not checked against the configuration, because the grants of one
no longer declared are still stored, and revoking them is a reason to call.

A revoked grant is not deleted: it stays readable as `revoked` until the cleanup job collects it.
Under [the API standard](../standards/api-standard.md#verbs-and-status-codes) a `DELETE` says the
row is gone, which this one would not be.

A revocation may reach many grants, so it answers their number, as the force logout did, and the
grant an administrator wants to see is read at `/api/v1/admin/grants/{grantId}`. A grant is revoked
once and keeps the revocation that came first, so a retry is not an error to refuse but a call
answering `0`, and a grant that expired or lapsed is left as it is rather than recorded as revoked
afterwards, which is what every revocation already does.

---

### Are grants read and revoked under the consent scopes?

**Decision:** No. Grants are read under `admin:grants:read` and revoked under `admin:grants:write`.
Revoking a consent stays under `admin:consent:write`.

**Options considered:**

- **Scopes of their own.**
- **`admin:consent:read` and `admin:consent:write`** — what force logout was already gated by.

**Rationale:**

A `client_credentials` grant has nothing to do with a consent, and a grant says more than a consent
does: when a person authenticated, and the claims an authorization requested. An administrator
allowed to manage consents is not thereby one allowed to read that.

Revoking a consent still revokes the grants of its audience, because the scope gates the action an
administrator takes rather than what follows from it — the way a client revoking a token holds no
admin scope at all.

An administrator signed in before the change holds a grant opened without the new scopes, and a
refresh adds none, so they sign in again. No release had shipped, so nothing eases that.

---

### What does the free text of the grant collection match?

**Decision:** The person's identifier claims, the client identifier and the audience identifier,
each partially and ignoring case. The client and the audience are filtered by identifier, and the
capability document shows each under its display name.

**Options considered:**

- **The identifiers, with display names in the capability document.**
- **A name configured on every client and every audience**, to filter and search on.
- **The display names of the client and the audience.**

**Rationale:**

A display name depends on the reader's language, so the same `q` would answer one collection to a
French reader and another to an English one. A name configured on every client and audience would be
a new key everywhere to answer a question the capability document already answers: a console's
filter reads as names while the collection matches on what does not change with the reader.

---

### What does the detail of a grant show?

**Decision:** Everything the grant records — its scope sets and how each was obtained, when the
person authenticated, the claims requested, who revoked it — and nothing of the tokens under it. A
list item is a summary: every field the collection filters on, `scope` aside.

**Options considered:**

- **The grant's own record.**
- **That, with the last refresh and when the last token expires.**
- **A list item carrying the detail whole.**

**Rationale:**

When a grant was last refreshed and when its last token expires are the tokens' own information,
and they get a collection of their own rather than a summary of it squeezed into the grant. The
scope sets and the requested claims are what an administrator reads once they have found the grant,
which is one request away from the list.

---

## Clients

### May a client's default scopes fall outside its allowed scopes?

**Decision:** No. `ClientsConfigValidator` holds a client's `default-scopes` inside its
`allowed-scopes` and refuses the configuration where they are not, so the set a request naming no
scope falls back on is one the same file already permits.

**Options considered:**

- **Refuse the configuration** — take readiness down at boot, naming the client, the scope and
  where the default came from, and let the operator say which of their two lines they meant.
- **Filter at the request** — have `parseRequestedScopes` intersect the defaults with the allowed
  set, so nothing stops booting and the narrower reading is picked for them.

**Rationale:**

The allowed set was applied to every scope a request named and to none of the scopes it fell back
on, so the two lines disagreeing was resolved in favour of the wider one: a client the deployment
forbade `openid` was granted it by asking for nothing, and `profile` with it.

Which line the operator meant cannot be read off either of them. A client allowed `reports` alone
and defaulting to `openid` says two things about `openid`, and picking one is guessing between a
default that should have been narrowed and an allowed set that was written short. This layer answers
a contradiction between two values the same way everywhere else — a value that cannot apply where it
was written is
[refused rather than
ignored](../standards/config-layer-code-standard.md#the-artifacts-of-a-configuration-domain) — and
filtering would have left the file saying something the server does not do, which is what hid this
in the first place.

**What a deployment sees.** A client whose defaults are all allowed is unaffected, and so is one
with no allowed set at all — its own or a template's — which allows every scope and has nothing to
contradict. Everything else reports one error per offending scope at startup, against the client,
because a template one client narrows below is still right for every other client on it.

**What correcting one takes.** The scope joins `allowed-scopes`, or the client names
`default-scopes` of its own. The shipped `templates.clients.default` gives a client that names none
`default-scopes: [openid, profile]` — and `email` beside them under the `mail` environment — so
narrowing `allowed-scopes` without naming defaults is the case this refuses. Either list may have
been inherited, so the error names the key each of the two was written at rather than a line the
operator may not have. That break is the compatibility question
[#453 answered](#is-a-grant-that-did-not-ask-for-openid-owed-an-id-token), answered the same way:
no stable release, and the deployments it reaches are correctable by hand.

**What is not held.** A template's own two lists. A client falls back on each of them
independently, so a template that contradicts itself is a client's problem only where that client
overrides neither — and the pair that decides a grant is the client's resolved one, which is what is
checked.

---

### Is a client that fails to authenticate answered `400` or `401`?

**Decision:** `401`, with `WWW-Authenticate: Basic realm="oauth2"`, wherever no public client can be
the one refused: for Basic credentials anywhere, and for any refusal on introspection and on the
`client_credentials` and token exchange grants. On revocation and on the `authorization_code` and
`refresh_token` grants, a client refused for credentials in the body, or for sending none, is
answered `400`. The `error` is `invalid_client` either way.

**Options considered:**

- **By whether a public client can be the one refused** — the rule above.
- **By where the credentials were sent, alone** — a `401` for the header, which is all RFC 6749
  §5.2 requires.
- **A `401` with the `Basic` challenge, always** — §5.2 allows it on every `invalid_client`, to
  name the schemes a server accepts.
- **A `401` with no challenge, always** — what many servers answer.

**Rationale:**

The last is not available: RFC 9110 §15.5.2 requires a challenge on every `401`. The second breaks
RFC 7662 §2.3, which answers invalid credentials at introspection with a `401` wherever they were
sent.

The third sends a request for a username and password to every public client, and a public client is
usually a page in a browser. The Fetch standard lets a browser answer a `Basic` challenge to a
request from its own origin by prompting the person, who can do nothing with it when the cause is a
client identifier the deployment mistyped. Only what a public client may call is exposed to that, so
only it is spared the challenge. A client branching on the failure loses nothing to the split,
because the code it branches on is `invalid_client` either way.

---

## Claims

### Is an administrator held to an invitation's audience?

**Decision:** Yes. `InvitationManager.validateAndCleanClaims` refuses a pre-assigned claim
restricted to an audience other than the invitation's own, and it asks that of every caller — the
client API, the admin API and the bootstrap alike. A bootstrap invitation is refused at startup
instead, by `BootstrapInvitationsConfigValidator`, so the operator is told by the same report as
every other configuration error.

**Options considered:**

- **Refuse only a client** — the literal reading of the rule
  [#454 stated](claims.md#writing-a-claim), beside the ACL check, which is already a
  client-only question.
- **Refuse every caller** — the audience asked apart from the ACL, of whoever names it.
- **Refuse a client, and warn an administrator** — the capability kept, with the mistake reported.

**Rationale:**

The administration surface reads across every audience, so excepting it here would have been
consistent with the one exception [the claims document](claims.md#reading-a-persons-claims) already
grants it. It is not the same question. Reading across audiences is an administrator answering for
the deployment; an invitation is an instrument of exactly one audience, consumed by a client of that
audience and by no other. A claim restricted to another is therefore a value the flow applying it
can never read back, chosen for an audience nobody asked — a mistake whether an operator or a client
makes it, and one the admin API names its audience as deliberately as a client does.

What it costs is a capability, and it is removed rather than moved. Nothing else writes a claim on
the administration surface — `AdminUserClaimController` reads and nothing more — so an operator who
was pre-seeding another audience's claim through an invitation has nowhere left to do it. That is
accepted here because the invitation was never the right instrument for it: it wrote a value the
sign-up applying it could not read back, and an operator would have had no way to see the result.
A warning would have kept the capability, and with it the silent success this entry is about — a
caller told its invitation was created, holding a token that will not do what the request said.

### What form does a claim's value take once it leaves this server?

**Decision:** The type the deployment declared. `ClaimDataType.typeClass` is the type a validated
value is held in, the type a stored one is read back as, and the JSON type a published one takes,
and the id token switches on `Claim.dataType` exhaustively rather than on the type the value is
carrying. A `boolean` claim is therefore the JSON `true`, not the string `"true"` it used to be.

A claim's `<claim>_verified` companion goes with that: it is claimed only beside a value, where it
used to be claimed whether or not one was published. `foo_verified: true` with no `foo` is this
server asserting it verified something it did not send, and a client reading the companion alone was
reading an assertion about nothing.

**Options considered:**

- **The runtime type of the value** — publish a `String` as a string, and log whatever else arrives.
- **The declared type, with `boolean` left as a string** — the `number` half fixed, the wire form
  of a `boolean` claim left as every deployment already reads it.
- **The declared type, exhaustively** — one `when` per publisher over `ClaimDataType`, with no
  `else`.

**Rationale:**

The runtime type is an artifact of how a value round-tripped through `ObjectMapper`, so reading the
wire form off it makes an id token's contents a consequence of a mapper's behaviour rather than of a
decision. That is how `number` — then the only type whose value was not a `String` — came to be
absent from every id token ever issued, whatever its ACL and whatever the person consented to, with
nothing to notice it but an error line per claim per token. An `else` arm is what swallowed it, and
an exhaustive `when` is what makes the next type answer for itself instead of inheriting that
silence.

Leaving `boolean` as a string was the cheaper half, and it was already ruled out here: [the API
standard](../standards/api-standard.md#json) says a boolean is a boolean. A claim published as
`"false"` is truthy in every language that tests it without comparing, which is the failure a client
writes once and never sees. And it was not even one form consistently — `/userinfo` answered
`"email_verified": "true"` where the id token answered `true` for the same account, against OpenID
Connect Core, which makes the same value two shapes depending on which endpoint a client asked.

What it costs is a wire change for a deployment holding a `boolean` claim, and it is taken now
because pre-1.0 is the cheapest this gets: the value is `true` on the id token, the client API and
the admin API alike, and a client comparing against `"true"` stops matching. Nothing migrates the
rows, because which claim a row belongs to is configuration rather than a column and no migration
could find them — the mapper reads a stored `"true"` back as a `Boolean` instead, which is what
carries the existing ones across.

The address is the one place the declared type does not decide, and it is not an exception to the
rule so much as the specification answering first: every member of the `address` object is a string
under OpenID Connect Core §5.1.1, so a component is rendered rather than published as its own type —
and rendered rather than dropped, which is what a `postal_code` configured as a number used to be.

**`/userinfo` is not held to this yet, and that is a limitation rather than a decision.**
`UserInfoResource` is a fixed data class whose scalar fields are `String?`, so
`UserInfoResourceMapper` reads each one with `value as? String` and a standard claim a deployment
retyped — `gender` as a `number`, say — is published in the id token and silently absent there. What
the rule would need is a resource able to carry a claim's own type, which is the same question as
`/userinfo` publishing custom claims at all. The address and the two `_verified` companions are held
to it, because those it could express.

### Does a granting rule see claims of every audience?

**Decision:** No. Each caller reads the claims of the audience its decision lands in, through
`CollectedClaimManager.findByUserIdAndAudience`: `prepareTerminalEffect` reads the flow's audience
before granting, and the token exchange reads the audience the act-as token is being issued for.
Consent is still not applied — what a rule may branch on and what a person agreed to disclose
remain different questions.

**Options considered:**

- **The whole person** — every claim, as before, on the grounds that a deployment's own rules answer
  for the deployment the way an administrator does.
- **The audience's claims** — the flow's audience deciding what its own grant may be keyed on.
- **The audience's for the webhook, the whole person for the rules** — the value filtered only where
  it actually leaves the server.
- **Narrowed inside the managers that own the rules** — `grantScopes` and `isActAsAllowed` filtering
  what their callers hand them.

**Rationale:**

A rule's value does not leave the server, but what it decides does, and a scope granted in a
`default` grant because of a `billing` claim publishes that claim's content one indirection away.
The authorization webhook settles it: it is handed the same list and posts it to an endpoint
configured on the client, so under the first option a restricted value leaves this server outright,
to an audience it was restricted from. Splitting the two would have left one list filtered and one
not — a distinction the next caller of `grantScopes` has to know about and the type does not carry.

The act-as rules are a second rule engine, reached from the token exchange rather than the flow, and
they answer the same question for a different caller: the acting client, whose audience nothing the
flow would have resolved. A manager narrowing on its
callers' behalf would have had to guess which, so the read is the caller's — the caller is what
knows where its decision lands. And it is a read rather than a filter, because loading a value a
rule may not see and dropping it afterwards is one refactor away from not dropping it.

What it costs is real and silent: a deployment whose rule branches on a claim restricted to
another audience stops granting that scope, with no error to read. Nothing can tell that rule apart
from one whose claim is merely absent for this person. The alternative is a server where the
audience restriction holds everywhere except the one place a value is posted to a third party, and a
rule about who may know a claim is worth less than the weakest path to it.

### Is the identifier lookup indexed over the claims a deployment identifies by?

**Decision:** No. `collected_claims__claim_folded_equality_hash__where_committed` covers every
committed row carrying a value, whatever claim it belongs to: its predicate is `session_id IS NULL
AND folded_equality_hash IS NOT NULL`, and it names no claim.
`collected_claims.folded_equality_hash` is likewise written for every claim carrying a value,
whatever `auth.identifier-claims` names.

**Options considered:**

- **Enumerate the identifier claims in the index** — what the file held, `claim =
  'preferred_username' OR claim = 'email' OR claim = 'phone_number'`, beside the state predicate.
- **Index every committed row carrying a value** — the whole of what a migration can say about
  this table without reading the configuration.
- **Write the hash for the configured claims only**, index `WHERE folded_equality_hash IS NOT NULL`,
  and rewrite the rows on a job lease when the set changes.
- **A `user_identifiers` projection** — a row per identifier an account holds, written beside the
  claim and rebuilt from the hash when the set changes.

**Rationale:**

The enumeration was a guess about configuration made in a file that cannot read it.
`ClaimDataType.canIdentify` admits `email`, `phone_number`, `number` and `string`, and a custom
claim has whatever id a deployment gave it, so no migration can name the set: one identifying by an
`employee_number` had no index for it, and every sign-in, uniqueness check, promotion and provider
link scanned `collected_claims` — answering correctly, getting slower with each account, and
reporting nothing. The H2 twin carried no predicate at all, so the two files disagreed about which
rows were indexed, and the dialect without the index was the one a production deployment runs. What
generalises out of it is a rule rather than this entry, and [the database
standard](../standards/database-standard.md#one-schema-spelled-per-dialect) states it.

The two narrow options are the ones worth arguing about, and both make a row depend on the
configuration it was written under. Writing the hash for the configured claims alone is the smallest
index of the four and strands every account created before a claim joined the set: unreachable by
the identifier it was told it had, until a pass over the table rewrites its rows. That trades a size
cost for an availability one. The projection answers that objection — the hash stays on every row,
config-independent, so the second table is a pure function of the first and a rebuild can never lose
an account — and it is the only shape here that is both general and minimal. It costs a table, a
write inside every transaction that writes a claim, the provisional and committed distinction
spelled a second time, and a window after a configuration change where the new identifier does not
resolve yet.

What the chosen option costs is index keys for claims nobody signs in with, and the second half of
the predicate is what keeps that from being every row of the table. A claim submitted blank is
stored rather than skipped — `ClaimValueValidator` answers `Optional.empty()`, which
`CollectedClaimUpdate` defines as a value replaced by null, and only a caller passing no optional at
all deletes the row — so a sign-up posting every declared field writes a row for each of them,
carrying no value and no hash. `folded_equality_hash IS NOT NULL` leaves all of those out, and it
costs the read nothing: every branch of `findCommittedClaimsMatching` is an equality on the hash,
which is strict, so the planner has the implication it needs to use the index.

The name goes on saying only `__where_committed`. It is 61 bytes with that half alone and does not
fit the other one, and which half a short name keeps was settled when the index was renamed: the one
that makes a lookup correct over the one that only makes the index small.

Leading with the hash rather than the claim was dropped with the enumeration. It plans identically
for an exact match on both columns, and would only pay off if `findCommittedClaimsMatching` were
rewritten as one `IN` over the hashes — its own change, and one that would want its own measurement.
The projection is what to build when the index that remains is measured and found to matter.

---

### Why is a key written on a generated claim refused rather than applied or ignored?

**Decision:** `claims.sub` and `claims.updated_at` accept no key at all. `ClaimsConfigValidator`
records one error per key a file wrote under either, whatever value it was written with and whether
or not any property binds it — so `claims.sub.enabled: true` is refused although it is what `sub`
already is, `claims.sub.template` is refused rather than resolved, and `claims.sub.enabled:` with
nothing under it is refused too. `GeneratedOpenIdConnectClaim` is the whole of what a generated
claim is, its ACL included.

**Options considered:**

- **Refuse in the parser**, where the generated claim is already assembled from the enum.
- **Refuse in the validator**, handed what the file wrote by its factory.
- **Refuse the values that bound**, reading each property a claim declares off
  `ClaimConfigurationProperties` and reporting the ones that are not null.
- **Refuse every key but `template` and `acl.readable-with-client-scopes-unconditionally`**, the two
  the parser reads.
- **Let the properties apply**, making a generated claim disableable, typed, restricted to an
  audience and published where a file says.
- **Warn in the log and carry on.**
- **Refuse only the keys with a visible effect** — `enabled`, `audience` — and go on ignoring the
  rest.

**Rationale:**

Nothing here fails to convert. `type: string` on `updated_at` is a perfectly good enum value with
nowhere to go, and deciding that it has nowhere to go is a decision rather than a conversion — so it
belongs with the other decisions, in the validator, and not in the class the standard says only
converts. Refusing in the parser costs ten lines and no signature, and it would leave a reader
looking for why a key was refused with two places to open and the next inapplicable value landing in
whichever one its author happened to be in.

The refusal is on the key rather than on the value under it, which is not the same rule written
twice. A properties class cannot answer for a key: `claims.sub.enabled:` with nothing under it binds
to null, exactly as a key nobody wrote does, so a rule reading the bound values accepts in silence
the very file this exists to refuse. Reading the keys instead also names them as the operator spelt
them — `claims.updated-at.type` rather than the `updated_at` Micronaut normalises to — and needs no
list of what a claim accepts, so a property added to a claim tomorrow is refused on a generated one
without anyone classifying it, which is exactly the step `published-in` skipped.

Sparing the two keys the parser reads was the first shape of this, and it does not survive reading
what becomes of them. `acl.readable-with-client-scopes-unconditionally` reached what is now the
client's read door, and a generated claim's door is the one its own declaration opens rather than
one a file writes, because its value is computed rather than collected from a person. Every place
that publishes one computes it instead: the id token through `IdTokenGenerator`, `/userinfo` and the
client claims endpoint through `GeneratedClaimsManager`, the last of them through the client read
door the generated claim declares rather than anything in the file. `template` was then read for
that same list and nothing else, so it decided nothing either. Keeping a key whose only effect is
that a misspelt scope under it takes readiness down is the defect this entry is about, not an
exception to it.

Making the properties apply is a feature per property rather than one change, and most of them are
incoherent on their face: OpenID Connect Core §2 requires `sub` in every id token, and a `type` on a
value this server computes is a promise the server would then have to keep against its own
computation. A log line reaches whoever is already looking, and the deployment this protects is the
one that wrote a key, restarted, and saw a ready server.

Refusing only the keys with a visible effect draws a line that is invisible from the file an
operator is holding, and it would have to be argued again for every key added afterwards. The same
reasoning refuses a value equal to the one the server would have used — comparing them would make
the refusal depend on the value rather than on the key, and a rule that fires only sometimes is one
nobody can predict from their own file.

**What a deployment sees.** A deployment with no `claims.sub` or `claims.updated_at` section is
unaffected, which is every file the server ships. One that wrote a key under either reports one
error per key at startup and serves nothing until the keys are gone, where the previous release
started and ignored them. A key reaching those sections from `System.env` or `System.properties` is
not read here, the same surface [the key-binding rule](../standards/config-layer-code-standard.md)
answers for.

**What correcting one takes.** Deleting the key, and nothing else: none of them decided anything, so
no behaviour follows the deletion. A `templates.claims` template carrying keys a generated claim
cannot use is untouched — a generated claim names no template, so nothing a template holds reaches
one, and the template goes on serving the claims that do name it. That break reaches no
stable release, and the deployments it does reach are correctable by hand.

### How does a client get a say in where a claim is delivered?

**Decision:** A claim may name a second list, `claims.<id>.published-in-when-requested`, and a
channel there carries the value only for a request whose `claims` parameter named the claim. The
request adds and never removes, it reaches the two OpenID channels and no other place, and a channel
named in both lists is refused at startup. The parameter is stored as a claim id per channel on the
flow session's OAuth2 row and copied onto every token row the grant produces.

**Options considered:**

- **A second list, `published-in-when-requested`** — the deployment opens a channel to a request,
  and the request chooses among the channels it opened.
- **Let the request publish past `published-in`** — no second list and no validator: a client asking
  for a claim in the id token gets it there if the ACL allows it at all.
- **Read §5.5 as a narrowing filter**, letting a client ask for a smaller id token out of what the
  scopes already deliver.
- **A single list with a per-entry modifier** — `published-in: [ userinfo, id-token-on-request ]`.
- **Store the raw `claims` JSON on the row** and parse it on each read.
- **Hold the request on the flow session only**, where the parameter arrives.
- **Go on implementing none of it**, leaving `claims_parameter_supported` absent.

**Rationale:**

Letting the request publish past `published-in` is the smallest change and it hands the decision to
the caller. A deployment that deliberately kept a claim out of the id token would find it there
because a client asked, with nothing in its file saying so — which is the fail-open shape [the
publication rule](claims.md#where-a-claim-is-published) was written to prevent. The second list
costs a key and a validator and keeps the destination the deployment's, which is what makes a
request safe to honour at all: with nothing opened, every request is answered exactly as before.

Reading §5.5 as a narrowing filter is the reading this server's model makes easy and the one the
specification forbids. §5.5 says the listed claims "are being requested to be added to any Claims
that are being requested using scope values", so a client library written against any other provider
would get the opposite of what it asked for. There is no half-measure available either: a parameter
that sometimes narrows and sometimes adds is one no client can predict.

The per-entry modifier saves a key at the cost of a value that is a channel and a condition glued
together — `id-token-on-request` — which nothing else in this configuration does and which
`ClaimPublicationPlace` would then have to carry as a second entry per channel. Two keys read as the
rule they are: published in `userinfo`, and published in the id token when requested. It is also the
spelling `claims.<id>.acl` already uses, with the condition last.

Storing the raw JSON costs a parse per id token and per `/userinfo` call, and puts a row in the
database that can fail to read back — which the business mapper would then have to refuse as this
server's own failure, on a value a client sent. Sanitizing once at the endpoint is what the
consented scopes beside it already do, and it leaves the row holding claim ids that cannot fail to
parse.

Holding the request on the session alone is the cheapest and is wrong in a way that is hard to see:
the code exchange honours it and every refresh afterwards does not, so a client's id token changes
shape the first time its token is renewed. That is the bug a refresh already had once for the id
token itself, and the fix is the same one — carry it on the token rows, where the consented scopes
live.

Implementing none of it leaves the discovery document telling a client it may not ask, which was
accurate and is the thing worth changing: a deployment with a claim too large for an id token and
wanted in `/userinfo` had no way to offer both, and a client that knows which it wants had no way to
say.

**What a deployment sees.** Nothing, unless it writes the new key: a claim naming no
`published-in-when-requested` is published where it always was, for every request, and a `claims`
parameter against such a deployment changes no response. `claims_parameter_supported` becomes `true`
in the discovery document whatever any claim's file says, which is accurate — a client may send the
parameter, and a deployment that opened no channel answers it by changing nothing.

### Is a person's profile one value for every audience, or one per audience?

**Decision:** One value, declared as the person's. A claim carries a kind, `personal` or
`application`, and a personal claim every audience has grants no client write — it is written by the
person, in the flow or through their own token — while an application claim stays the client's
wherever the deployment put it. #507 stated it, and [the
claims](claims.md#who-may-read-and-write-a-claim) hold the rule.

**Options considered:**

- **A population of accounts per audience** — Keycloak's realm: a person signing up to two audiences
  is two accounts, and nothing one audience's client writes reaches the other.
- **A value per audience for a shared claim** — one account, and a row per audience for `name`, the
  person confirming it once per audience from a suggestion.
- **Every shared claim the person's** — a client writes only a claim restricted to its audience,
  whatever the claim is.
- **The kind inferred from the consent scope** — a claim carrying one is the person's, a claim
  carrying none an application's.
- **A declared kind** — what was chosen.

**Rationale:**

The pain was a client of one audience writing `name` for every other, and full isolation is the
largest fix for it. It pays with everything an identity is made of — identifier uniqueness,
password, second factor and provider links per audience, an administrator holding a second account,
an act-as token whose subject is another population's row — and it does not remove the concern,
only shrinks it, since a client still writes what its siblings in the audience read. A value per
audience keeps identity whole and makes the person maintain their profile once per audience. Its
precedent, Okta's per-application profile, exists for provisioning into SaaS schemas and is not read
into OIDC claims, and no product ships a per-application `name`.

What the market agrees on is narrower than a shared profile: the person's profile is written by the
person, through the sign-in pages or their own token, and a client secret writes application data —
Auth0's `user_metadata` against `app_metadata`, Cognito's write permission per attribute per app
client. Making every shared claim the person's took that too far, and took the application's claims
from the client that owns them: a credit score would have needed an audience to be writable at all,
every bare custom claim with it. Inferring the kind from the consent scope missed in both
directions, because consent gates disclosure and not ownership — a person's claim may carry no
scope, and an application's may carry one where the person has to agree before a client is told
their score. So the kind is declared, a template carries the default, and the validator holds the
ACL to it.

**What it costs.** Every claim a deployment declared says whose its value is or names a template
that does, which is one line per claim or one per template, and a claim of the person's that a
client could write is refused until the deployment restricts it to an audience or drops the grant.
And a person's name is one value: a deployment wanting a different display name per audience
declares a personal claim restricted to each.

### Is a claim's ACL a set of flags, or an expression per door?

**Decision:** An expression per door. A claim keeps one key per party and direction —
`collected-in-flow`, `readable-by-person`, `writable-by-person`, `readable-by-client`,
`writable-by-client` — and each holds a rule expression in the language the scope granting and
act-as rules use, combining consent to a scope the door names, a scope a rule granted, the client,
the authentication's age and the token's binding. A write the person's door refuses is answered with
a challenge for every condition of its expression the credential did not meet. [The
claims](claims.md#who-may-read-and-write-a-claim) hold the rule.

**Options considered:**

- **Keep the booleans and add a key per condition** — what the ACL was: a consent flag per door, one
  `consent-scope` for the claim, two lists of client scopes, and `write-max-authentication-age`
  qualifying one door, with a validator rule saying which door it may be written on.
-  **Make only the grant an expression, and keep the conditions that carry a challenge as keys** —
  each key knows its challenge by construction.
- **An expression per door, every condition a term of it** — what was chosen.
- **Report only the first false term** of a refused write, rather than every one.
- **Keep `consent-scope` on the claim**, read by a `SCOPE_IS_CONSENTED()` taking no argument, or
  overridden by one that takes it.
- **Name a client's scope by the client** — `CLIENT_HAS_SCOPE`.
- **A shipped template per scope** — `profile`, `email`, `address`, `phone`, each opening the three
  doors, so a claim stays at one line.
- **Wall-clock functions**, so a claim is writable during business hours.
- **One function set for every door**, the flow answering false to the token's functions.

**Rationale:**

The flags made every condition a key of its own, written once per direction, each with a rule about
which doors it may sit on and which keys it may meet, and `A || B` could not be written at all. The
one such rule already shipped was for the authentication's age, and a key binding or a particular
client would each have been the next. One scope for the whole claim also meant every door a claim
opened by consent opened on the same scope, so the only way to let a person write their name was to
open it to every client already reading it — not something a person can be asked to agree to
separately.

Keeping the challenge-bearing conditions as keys solves the challenge by construction and keeps the
key explosion for exactly the conditions a deployment most wants to combine. Deriving the challenge
from the terms instead costs over-reporting under `||`: both operands refused are both challenged,
and a client meeting either would have been answered. Reporting the first false term only
under-reports the other way — a client holding an unbound token with a fresh authentication would be
told to re-authenticate when binding its token would have done — and sending every challenge lets
the client pick.

A `consent-scope` the argument-less function reads is two spellings for one condition, and the
template's `SCOPE_IS_CONSENTED()` silently binding to whatever scope the claim names is the
one-scope-per-claim coupling a scope per door exists to remove; as a default the argument overrides,
it means something different on each door it reaches. A function named for the client answers a
`client_credentials` token alone and leaves a scope a rule granted a person with nothing to ask it,
so the scope functions are named by how the scope reached the credential, which is the split
[security](security.md#scopes) already draws. A template per scope cannot be had without repeating
the kind and the places in each, since [a template takes no template](claims.md#templates), and a
deployment adding a scope would add a template to carry it; three lines on the claim say the same
where the claim is.

A door answering differently at 17:01 is a rule that fires only sometimes, and there is no challenge
to send a client refused by the clock. And one function set for every door would accept
`TOKEN_IS_BOUND()` on the flow's door and shut it for every person forever, in silence — the flow's
set is its own so that is a startup refusal.

**What a deployment sees.** The keys a deployment wrote under `acl` before are refused at startup as
keys nothing binds. Each flag set `"true"` becomes `SCOPE_IS_CONSENTED("<its consent-scope>")`, or
`"true"` where it named no scope; each list of client scopes becomes `SCOPE_IS_GRANTED(...)` terms
joined by `||` to the consent term; the age becomes `&& AUTHENTICATION_IS_YOUNGER_THAN(...)` on
`writable-by-person`. The shipped `personal` template opens no door, so a deployment's own personal
claim writes three lines where it wrote one.

### How does a person's own access token write their claims?

**Decision:** Through a surface of its own, `/api/v1/user`, gated on the person's access token, with
the person's write door of each claim deciding which of them it writes. The door is shut unless a
deployment opens it, so no claim is writable this way on upgrade.
[Security](security.md#the-user-surface) holds the rule.

**Options considered:**

- **Honour the flow's door** — the person is the person either way.
- **A surface of its own, opened per claim by the ACL** — what was chosen.
- **The same surface behind a switch of its own**, as `admin.enabled` turns the admin surface on.
- **A grantable scope gating the surface**, beside the ACL.
- **A claim write on the admin surface**, for the console a person administers from.
- **Routes under `/api/v1/client`**, reached by a person's token as well as a client's.

**Rationale:**

An access token says a client was authorized to act for the person, for as long as a refresh token
lets it, and nothing about the person being there; the flow is the person on this server's own
pages having just authenticated. One permission over both is opened for the near caller and read by
the far one, and the shipped claims open the flow's door, so honouring it would have made every
profile claim writable through an API the day a deployment upgraded. A written claim also leaves
this server, into the id tokens of every client of the audience, so a client able to write one
chooses what other applications are told about a person.

A switch is all-or-nothing where the ACL is per claim: a deployment wanting one claim writable would
open the surface for every claim whose door it ever opened. A grantable scope is a rule evaluated
against the person and the client, which is not the question asked here — the question is which
claim, and the door answers it per claim. The admin surface would have the console write its own
operator's profile with a scope that rewrites every account's, and would do nothing for any other
client's page. And a prefix with two gates is two surfaces under one name, since [a surface is a
prefix with one audience and one gate](architecture.md#surfaces).

### Does an act-as token reach the user surface?

**Decision:** Yes, and each claim's doors decide what it reads and writes, in the acting client's
audience. [Security](security.md#the-user-surface) holds the rule.

**Options considered:**

- **Let it in, the doors deciding** — what was chosen.
- **Refuse it with `forbidden`**, as `/userinfo` refuses a `client_credentials` token.

**Rationale:**

The door is where a deployment already says who may read and write a claim, and a door asking for
the person's consent or a recent authentication of theirs refuses an act-as token by itself, since
neither stands behind one. Refusing the token at the surface would have been a second answer to the
same question, overriding a door a deployment opened on purpose.

---

## Where a request came from

### Should the places a session is driven from share the table the fold reads?

**Decision:** One table. `interactive_flow_session_security_context` holds a row per distinct place
a session was driven from, and a `proven_date` — set only where a credential verified and resolved
that session's user — is what the fold reads. Every other row is invisible to it.

**Options considered:**

- **One table, a proven column** — one shape, one fingerprint, and the fold's gate left at the
  five call sites that prove a credential.
- **A second table for the places no proof wrote** — the fold could not read them if it wanted to.
- **Two columns on `interactive_flow_sessions`** — where the session started, written once, and no
  writer on the flow path at all.

**Rationale:**

The second option is the structurally safest and was rejected on what it duplicates: the two tables
would carry the same ten columns and the same fingerprint, computed by the same
`SecurityContextKey` for the same reason, and the copy that drifts is the one nothing reads until
an operator is looking at a stalled flow. A column the fold filters on keeps the gate where the
safety already comes from — the call sites that know a credential verified — and a row anybody
holding the state wrote is outside the fold by construction rather than by a filter somebody has to
remember.

The third answers only *where it started*. A session driven from a second place would look
identical to one that was not, and the change of place is the whole signal; it also puts
request-shaped state on a row [the interactive flow](interactive-flow.md) keeps flow-generic.

**What it costs.** A table a caller can write to feeds, at one remove, a record kept for months —
which is why the number of distinct places one session may hold is bounded, why the place rolled
out to make room is the one seen least recently rather than the one just observed, and why a proven
place is the last to go. [The security context](security-context.md) holds them.

---

## Logging

### Is an ERROR written per failure, or per change of state?

**Decision:** Per change of state. A dependency is a condition; its first failure degrades it and
writes one ERROR, the failures after it are counted, and it recovers — one line at INFO — on a
success followed by a quiet period without a failure.

**Options considered:**

- **An ERROR per failure** — what the code did, and the simplest thing to grep for.
- **An ERROR per failure, rate-limited** — a line at most every so often per logger or per message.
- **An ERROR per change of state, with a quiet period before recovery.**

**Rationale:**

An ERROR is written synchronously, so its cost is paid by the caller, and per failure that cost
scales with the traffic an outage meets: a provider down under a thousand requests a second is a
thousand synchronous writes a second, at the moment the server is least able to afford them.
Rate-limiting bounds the cost but keeps the shape wrong — an operator still reads one outage as a
stream of lines and is never told it ended. A condition says both ends once, and the count it
carries says what the stream would have.

Without the quiet period, a dependency failing one call in two flips on every call and writes the
per-failure line again by another route.

The survey behind this found no authorization server that deduplicates a repeated upstream error;
Keycloak's definition of ERROR, "a significant error or problem leading to the inability to process
requests", is the nearest to the meaning this one gives it.

### Is an ERROR written through the log queue?

**Decision:** No. Every level below ERROR goes through the bounded queue in front of the console;
an ERROR is written to the console directly, on the caller's thread.

**Options considered:**

- **Everything through the queue** — one path, and nothing ever writes on a request's thread.
- **Everything through the queue, ERROR never dropped** — the queue blocks for an ERROR when full.
- **ERROR written directly, the rest queued.**

**Rationale:**

The queue is full exactly when the server is degraded, which is when an ERROR is written; through
the queue, the one line saying the server is in trouble is the one most likely to be dropped, and
one that is queued is lost with the process if a crash follows it. Blocking for it instead makes the
caller wait on the queue and on stdout both. Written directly, it waits on stdout alone, and only a
condition changing state takes that path — which is what makes a synchronous write affordable.

**What it costs.** An ERROR may appear before a line queued ahead of it, and a stdout that stops
being read blocks the caller writing one. [Logging](logging.md) names both.

### Does a full log queue make its caller wait?

**Decision:** No. INFO and below are dropped once the queue is four fifths full, a WARN once it is
full, and every drop is counted and reported by the console's condition.

**Options considered:**

- **Block when full** — what Keycloak's asynchronous logging does, and Quarkus's default.
- **Drop anything when full** — what the OpenTelemetry SDK's batch processors do.
- **Drop the lower levels first, and count.**

**Rationale:**

Blocking hands stdout's backpressure back to the request the queue took it off, on threads the
server cannot afford to stall. Dropping is safe here only because nothing critical is queued: an
ERROR never is. Dropping the lower levels first keeps room for a WARN through a burst of INFO, and
counting is what turns a silent gap in the log into backpressure an operator is told about — the
one thing none of the servers surveyed does.

### Does a degraded dependency take readiness down?

**Decision:** No. A degraded condition makes `/health` and readiness report `DEGRADED`, and both
still answer `200`; liveness does not report it.

**Options considered:**

- **Readiness down while a condition is degraded.**
- **Liveness down while a condition is degraded.**
- **`/health` degraded, readiness and liveness untouched** — which takes replacing the framework's
  health endpoint, since it builds readiness from every indicator not marked for liveness.
- **`/health` and readiness degraded and answering `200`, liveness untouched.**

**Rationale:**

An external dependency fails for every instance at once. Readiness taken down by it pulls every
instance out of the load balancer, turning one broken sign-in method into an outage; liveness taken
down restarts every instance into the same outage. Spring Boot's own guidance keeps external systems
out of liveness for the same reason. Only the configuration takes readiness down, because a
corrected file and a restart is what fixes that, and nothing a restart fixes is reported by a
condition.

Keeping the condition out of readiness altogether would mean owning a copy of a framework class for
a difference no probe reading the status code can see: a degraded readiness still answers `200`.

## Telemetry

### Why the OpenTelemetry SDK rather than Micrometer?

**Decision:** Traces, metrics and log records all come from the OpenTelemetry SDK that Micronaut's
tracing module builds, exported over OTLP through one exporter.

**Options considered:**

- **Micrometer with its OTLP registry, beside the tracing SDK** — Micronaut's usual metrics stack.
- **A Prometheus scrape endpoint** — what most self-hosted servers expose.
- **The OpenTelemetry Java agent** — instrumentation attached to the JVM at startup.
- **The SDK alone**, its HTTP request durations recorded by the same instrumenters as the spans.

**Rationale:**

The tracing module's instrumenters already record the request durations, on the same resource and
under the same attributes as the spans. Micrometer beside them would put a second OTLP client in the
process, exporting a second resource, configured under a second prefix, to say the same thing about
one request in another attribute vocabulary. A scrape endpoint is a new surface, with an audience
and a gate of its own that a push needs none of. The agent attaches to a JVM, and the production
artifact is a native image: there is no JVM to attach it to.

### Is a query string redacted parameter by parameter?

**Decision:** No. A query is replaced whole with `REDACTED`, in `url.query` and in `url.full`, in
the attributes a request's span and its metrics are both recorded from.

**Options considered:**

- **The semantic conventions' default redaction** — the values of a short list of parameters,
  which are cloud signatures and none of this server's.
- **A list of this server's credential parameters, redacted by value.**
- **A list of the parameters safe to keep, the rest redacted.**
- **The whole query, replaced.**
- **Redacting spans on their way to the exporter.**

**Rationale:**

The default list names `sig` and `X-Amz-Signature`, not `code`, `state` or `invitation_token`. A
list of this server's own goes stale the next time a specification adds a parameter, and a list of
safe ones the next time a route adds one; either way the failure is a credential exported in
silence. The whole query costs nothing that matters, since the route names the endpoint and the path
says what was asked for.

Redacting spans at the exporter leaves the query in the metrics: a histogram's exemplar carries the
request's attributes its series is not keyed by. The attributes are redacted where they are gathered
so that no signal sees the query at all, and they are overwritten rather than removed because the
builder the instrumenter hands an extractor silently ignores a removal.

### Is telemetry turned off by leaving the exporters unset?

**Decision:** No. `application.yml` ships `micronaut.otel.enabled: false` beside every signal's
exporter set to OTLP, and a deployment turns telemetry on with the flag and an endpoint.

**Options considered:**

- **The exporters left unset**, each defaulting to `none` until a deployment names it.
- **The SDK's own switch, `otel.sdk.disabled`.**
- **Micronaut's flag, `micronaut.otel.enabled`.**

**Rationale:**

With no exporter, the integration is still built: every request opens a span and records its
duration for nothing to read, and turning it on takes a key per signal. The SDK's switch builds an
SDK that does nothing, under filters and an appender that still run. Micronaut's flag removes the
integration whole, so a server with telemetry off does no telemetry work at all, and every setting
that is the same for every deployment can ship already set, leaving a deployment two keys to write.

### Are traces shipped on, and what turns them off?

**Decision:** Off. `application.yml` ships `otel.traces.sampler: always_off` beside the trace
exporter, and a developer turns traces on with `otel.traces.sampler: parentbased_always_on`.

**Options considered:**

- **On, under the SDK's default sampler**, which samples every trace without a parent.
- **A key of the server's own, gating only the spans the server opens itself.**
- **`otel.traces.exporter: none`.**
- **`micronaut.otel.enabled: false`.**
- **`parentbased_always_off`**, which traces only a request whose caller sampled its trace.
- **An exclusion per route, the way `/health` is excluded.**
- **On, and filtered at the collector.**

**Rationale:**

A span says what one request waited on and in what order, and that is a question the people
developing the server ask. An operator runs it from aggregates, which the metrics and the log
records already are, and under the SDK's default every one of their servers built, queued and
exported a span per request, per call and per query for nobody to read.

The sampler is the one switch that stops a span being built. A key of the server's own would turn
off the few spans the server opens and leave the request, call and query spans, which are most of a
trace. With no exporter, the SDK still records every span and drops it only once it ends, and
turning traces on would take two keys. The integration's own flag takes the metrics and the log
records down with the spans. Following a sampled parent would let a header anybody can send decide
what an operator's server records. An exclusion removes a route whole, and every route would have
to be listed and kept listed. A filter at the collector is written by every deployment, after the
server has paid for every span.

### Are the runtime metrics read through OpenTelemetry's runtime instrumentation?

**Decision:** No. The server reads the heap pools, the threads, the CPU and each garbage collector
from the platform's management beans itself.

**Options considered:**

- **The runtime instrumentation, everything it registers.**
- **The runtime instrumentation, narrowed by views on the meter provider.**
- **The readings a native image fills, read by the server.**

**Rationale:**

A native image leaves most of the instrumentation's readings empty, and exporting a metric that is
always zero states something false about the server. A view can drop an instrument but not a
series, and the non-heap pool that is always zero is a series of the same instruments as the heap
pools; the instrumentation registers all of its readings or none of them. Read by the server, the
set is the same calls, written out once, and it is the one an operator gets on the JVM and on the
native image alike. The instrumentation's GC duration is built from notifications a native image
never sends, which is why the collectors' own count and time are read in its place.

### Is a technical metric named by this server or by an existing convention?

**Decision:** By an existing convention, with its unit and attributes: OpenTelemetry's semantic
conventions first, then OpenTelemetry's own instrumentation and JMX metrics, then a major library's,
such as Micrometer's. `sympauthy.` names the business metrics and those about this server's own
notions — a condition, a flow, a scheduled job.

**Options considered:**

- **`sympauthy.` for everything the semantic conventions do not name.**
- **The most used existing convention, falling back to `sympauthy.`.**

**Rationale:**

An operator who has read Netty's allocator or an executor's active threads before reads them here
with what they already know, and a dashboard built for the convention works unchanged. A name of
this server's own would be one more vocabulary for a reading that already has one. A business
metric has no convention to take, and an operator expects this server's own notions under its own
prefix.

### Is each kind of event a metric of its own, or one metric with an `event` attribute?

**Decision:** A metric per kind of event — token requests, access-token checks, flow sessions,
authentication attempts — each with the attributes that event has.

**Options considered:**

- **One counter for every event, with an `event` attribute**, as Keycloak counts them.
- **A metric per kind of event.**

**Rationale:**

A token request carries a grant type, a sign-in a method and a session a purpose. In one metric,
each of those attributes would mean nothing for most of its series, and a duration could not be
recorded at all, since a counter and a histogram are different instruments.

### Does a metric count its failures beside its successes, or under an attribute?

**Decision:** Under an attribute. `outcome` is `success`, `refused` or `error`, and `error.type` is
the error code the caller was answered with.

**Options considered:**

- **Paired counters**, one for the events and one for their errors, as the community Keycloak
  extension and Janssen count them.
- **Reasons of the metric's own**, such as an unknown account apart from a wrong password.
- **`outcome` and `error.type` on one metric.**

**Rationale:**

Summing either of two paired counters says nothing about the total, where the semantic conventions
ask that summing a metric over its attributes count every event. `error.type` is the semantic
conventions' own attribute, so a backend groups by it unprompted, and the error codes are a closed
set the bundle test already holds closed. Reasons that are not error codes would be a second
vocabulary for one metric, and the one place they were wanted — the refusals of a password sign-in
— is where the server answers alike on purpose.

### Is `client_id` an attribute every deployment gets?

**Decision:** Yes, with no key to turn it off and no cap. It is recorded once the client has been
found in the configuration, and left out otherwise.

**Options considered:**

- **Off by default and capped**, as Keycloak keeps it, because a realm's clients can be created
  through its API.
- **Always recorded, the id a request sent included.**
- **Always recorded, once the client has been found.**

**Rationale:**

Here every client is declared in the configuration file, so the set is as large as the deployment
wrote it and changes only when the file does; a cap guards against a growth this server cannot
have. The id a request sent is chosen by whoever sent it, and recording it would let a scan create
a series per guess — what Ory Hydra and Zitadel each found when they let a request's path become
an attribute, and reverted.

### Is a use case counted by its controller or by its manager?

**Decision:** By its manager. The token endpoint's work past the HTTP layer moved into
`TokenRequestManager` so that a token request is counted there.

**Options considered:**

- **The controller**, which sees the whole request, the refusals of the HTTP layer included.
- **The manager running the use case.**

**Rationale:**

A manager is callable without an HTTP request, and a use case counted there is counted whichever
caller runs it; a controller is routing and status codes, and a metric recorded in it is one more
thing it does. What the manager never sees — a client that fails to authenticate, a refused DPoP
proof, a grant type the server does not support, a malformed authorization request — is answered by
the HTTP layer, and `http.server.request.duration` already counts it by route and status. What is
lost is the grant type and the client on those refusals, and neither is known when the request is
refused for not naming them.

### Are a condition and the calls reported to it one metric?

**Decision:** Two. `sympauthy.condition.degraded` publishes every condition, and
`sympauthy.remote_call.duration` times the calls made outside the process, each carrying the
`condition` it reported to.

**Options considered:**

- **One `sympauthy.dependency.*` family**, the gauge and the call histogram under one name.
- **A condition's metric and a remote call's metric.**

**Rationale:**

A condition is not always a dependency reached by a call: the console's queue is this server's own,
and degrades by dropping lines. Under one name, the queue reads as a dependency that is called and
never is, and the calls read as covering every condition when they cover only the remote ones. Both
key on `condition`, so a failing call and the condition it degraded are still read under one name.

### Is a query traced as a span, or read from the database's statistics?

**Decision:** A span per statement, a child of the span of the request it was made for.

**Options considered:**

- **No database spans, and `pg_stat_statements` for query latency.**
- **A span per repository call**, named in the server's own vocabulary.
- **A span per statement sent.**

**Rationale:**

`pg_stat_statements` says which statement is slow across the whole database and never which request
was waiting on it, so the operator matches the two by timestamp; H2 offers no equivalent. A span per
repository call names the call the way the code does, but a call sending several statements hides
which one was slow, and it is a span the semantic conventions do not describe. A span per statement
is what the conventions name, and a collector reads it with what it already knows.

### Are database spans OpenTelemetry's R2DBC instrumentation, or the server's own?

**Decision:** The server's own instrumenter, built on OpenTelemetry's instrumentation API and
called back by r2dbc-proxy, with the statement replaced by OpenTelemetry's SQL analyzer.

**Options considered:**

- **Micronaut's R2DBC tracing module.**
- **OpenTelemetry's R2DBC library, wired by the server.**
- **An instrumenter of the server's own over r2dbc-proxy**, the library OpenTelemetry's builds on.

**Rationale:**

Micronaut's module is conditioned on its own key alone, so with telemetry off it still wraps every
connection, around the no-op `OpenTelemetry`; and it takes no extractor. OpenTelemetry's library
describes a query by the conventions' current names only under a process-wide opt-in, read from a
system property once, when its class loads. Under that opt-in it leaves a bound statement as it was
sent, so a literal written into one leaves with it; it reads every double-quoted fragment as a
literal, so a generated statement reads `SELECT user_.? FROM ?`; it registers
`db.client.operation.duration`, a metric this server has not decided to export; and it opens a root
span for every query a job makes. Each could be worked around, by a system property set before the
framework starts, an extractor, a view and a sampler; the instrumenter that needs none of them is
smaller than the four.

### Is a query made outside a request traced?

**Decision:** No. A query opens a span only under a span already open.

**Options considered:**

- **Each scheduled job's queries as a trace of its own.**
- **A span only under a request.**

**Rationale:**

A job's own trace shows its queries at the cost of a trace per run, per instance, for the life of
the deployment, which would make the cleanup jobs the most-traced thing at the collector. Their
metrics already say what each run did and how long it took. The work at startup has no request to
belong to either, and the migration runs over a connection the tracing does not wrap.

### Is a statement exported with a query span?

**Decision:** Yes, with every value in it replaced by `?`, the values written into it as well as the
ones it binds.

**Options considered:**

- **The statement left out.**
- **The statement as sent**, since every value the server sends is bound.
- **The statement with every value replaced.**

**Rationale:**

Leaving it out removes the question of what a statement may carry, and with it the one attribute
that says which query a slow span was. Sent as it is, a statement carries whatever literal its
query writes, and nothing guarantees that every literal the next query writes is harmless — a
token's hash, an email address and a claim's value are what a query compares against. Replaced, it
says nothing the server's source does not, and still says which query it was.

---

← [How the system works](index.md)
