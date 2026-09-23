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

SympAuthy is built around a single user pool shared by every client. Per-client MFA control would
produce a confusing experience — the same person challenged on one application and not on another —
and a real bypass, since a client could opt out of a policy the deployment enforces globally.

The deeper reason is that enrolment is a property of the person, not of the client: someone has one
authenticator regardless of which application sent them here. A policy attached to the client would
be describing something the client does not own.

Audiences group clients for the purposes of consent, but they do not create separate populations of
users. Per-audience MFA policy is the version of this that could be reconsidered later; per-client
is not.

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
[refused rather than ignored](config-layer-code-standard.md#the-artifacts-of-a-configuration-domain)
— and filtering would have left the file saying something the server does not do, which is what hid
this in the first place.

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
  [#454 stated](security.md#claims-and-audiences), beside the ACL check, which is already a
  client-only question.
- **Refuse every caller** — the audience asked apart from the ACL, of whoever names it.
- **Refuse a client, and warn an administrator** — the capability kept, with the mistake reported.

**Rationale:**

The administration surface reads across every audience, so excepting it here would have been
consistent with the one exception [the security document](security.md#claims-and-audiences) already
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

**Decision:** The type the deployment declared. `ClaimDataType.typeClass` is the type a validated value
is held in, the type a stored one is read back as, and the JSON type a published one takes, and the id
token switches on `Claim.dataType` exhaustively rather than on the type the value is carrying. A
`boolean` claim is therefore the JSON `true`, not the string `"true"` it used to be.

A claim's `<claim>_verified` companion goes with that: it is claimed only beside a value, where it used
to be claimed whether or not one was published. `foo_verified: true` with no `foo` is this server
asserting it verified something it did not send, and a client reading the companion alone was reading
an assertion about nothing.

**Options considered:**

- **The runtime type of the value** — publish a `String` as a string, and log whatever else arrives.
- **The declared type, with `boolean` left as a string** — the `number` half fixed, the wire form of a
  `boolean` claim left as every deployment already reads it.
- **The declared type, exhaustively** — one `when` per publisher over `ClaimDataType`, with no `else`.

**Rationale:**

The runtime type is an artifact of how a value round-tripped through `ObjectMapper`, so reading the wire
form off it makes an id token's contents a consequence of a mapper's behaviour rather than of a decision.
That is how `number` — then the only type whose value was not a `String` — came to be absent from every
id token ever issued, whatever its ACL and whatever the person consented to, with nothing to notice it
but an error line per claim per token. An `else` arm is what swallowed it, and an exhaustive `when` is
what makes the next type answer for itself instead of inheriting that silence.

Leaving `boolean` as a string was the cheaper half, and it was already ruled out here:
[the API standard](api-standard.md#json) says a boolean is a boolean. A claim published as `"false"` is
truthy in every language that tests it without comparing, which is the failure a client writes once and
never sees. And it was not even one form consistently — `/userinfo` answered `"email_verified": "true"`
where the id token answered `true` for the same account, against OpenID Connect Core, which makes the
same value two shapes depending on which endpoint a client asked.

What it costs is a wire change for a deployment holding a `boolean` claim, and it is taken now because
pre-1.0 is the cheapest this gets: the value is `true` on the id token, the client API and the admin API
alike, and a client comparing against `"true"` stops matching. Nothing migrates the rows, because which
claim a row belongs to is configuration rather than a column and no migration could find them — the
mapper reads a stored `"true"` back as a `Boolean` instead, which is what carries the existing ones
across.

The address is the one place the declared type does not decide, and it is not an exception to the rule so
much as the specification answering first: every member of the `address` object is a string under OpenID
Connect Core §5.1.1, so a component is rendered rather than published as its own type — and rendered
rather than dropped, which is what a `postal_code` configured as a number used to be.

**`/userinfo` is not held to this yet, and that is a limitation rather than a decision.**
`UserInfoResource` is a fixed data class whose scalar fields are `String?`, so `UserInfoResourceMapper`
reads each one with `value as? String` and a standard claim a deployment retyped — `gender` as a
`number`, say — is published in the id token and silently absent there. What the rule would need is a
resource able to carry a claim's own type, which is the same question as `/userinfo` publishing custom
claims at all. The address and the two `_verified` companions are held to it, because those it could
express.

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

← [Design documentation](index.md)
