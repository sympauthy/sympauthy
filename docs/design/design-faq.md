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
an `id_token` beside the access token, holding the subject, the audience and the session of the
original authentication, with a fresh issue date, expiry, claim set and `at_hash`.

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
`CollectedClaimManager.findByUserIdAndAudience`: `applyTerminalEffect` reads the flow's audience
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
they answer the same question for a different audience: the one the exchange names, which may be
neither the acting client's nor anything the flow would have resolved. A manager narrowing on its
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
what becomes of them. `acl.readable-with-client-scopes-unconditionally` reaches
`UnconditionalAcl.readableWithClientScopes`, whose only reader is `Claim.canBeReadByClient`, whose
only callers filter the claims *collected* from a person — and a generated claim is never one of
those, because its value is computed. Every place that publishes one computes it instead: the id
token through `IdTokenGenerator`, `/userinfo` and the client claims endpoint through
`GeneratedClaimsManager`, the last of them gated by the scope on the endpoint rather than by
anything in the file. `template` was then read for that same list and nothing else, so it decided
nothing either. Keeping a key whose only effect is that a misspelt scope under it takes readiness
down is the defect this entry is about, not an exception to it.

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

**What it costs.** Every claim a deployment declared says whose its value is or names a template that
does, which is one line per claim or one per template, and a claim of the person's that a client
could write is refused until the deployment restricts it to an audience or drops the grant. And a
person's name is one value: a deployment wanting a different display name per audience declares a
personal claim restricted to each.

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

← [How the system works](index.md)
