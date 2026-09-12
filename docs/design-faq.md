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
stable release, so the deployments a break reaches are ones that can still be fixed by adding
`openid` to the request — which is what a client wanting an id token was always meant to send. The
same change made after a 1.0 would cost every one of them a migration.

**What a deployment sees.** A client requesting `openid` is unaffected, and the scope is
auto-granted when requested, so nothing else has to change for it. A client that does not request it
stops receiving an `id_token` in either response, and reads the person's claims from `/userinfo`,
which is the endpoint for exactly that.

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

← [Design documentation](index.md)
