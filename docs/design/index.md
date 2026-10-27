# How the system works

What the server is and how each of its parts works, a document per part. This is the design
documentation, written the way [the design documentation
standard](../standards/docs-design-standard.md) asks: what the part does, what a reader has to open
to change it, and why it was settled that way. The rules a change is held to are the other half —
[how the code is written](../standards/index.md).

- **[Architecture](architecture.md)** — what makes something its own API surface and which of them
  carry a version, the layers and what cuts across them, and the project layout on disk.
- **[Technology](technology.md)** — the frameworks and runtime the server is built on, and why each
  was picked.
- **[The interactive flow](interactive-flow.md)** — the purposes a person is put through, the
  session an engine sequences them over, and how a purpose or a step is added.
- **[The OAuth2 authorization](oauth2-authorization.md)** — the session a client's authorization
  request starts: what the request becomes, what a person goes through for it, how it hands them
  back with a code given out once, and what that code is good for.
- **[The provisional user](provisional-user.md)** — the account a sign-up has not finished creating:
  the rows it owns, what makes them count, and what collects them when nothing ever does.
- **[The claims](claims.md)** — what this server knows about a person: whose a claim is, who may
  read and write one and through which credential, which audience has it, and which places this
  server publishes it in.
- **[The identifier claims](identifier-claims.md)** — what a deployment identifies a person by:
  what it may declare, what makes a value belong to one account across the set rather than within a
  claim, what a sign-up has to collect, and how a value resolves to an account — when two values
  somebody typed are one value, how a row is found by one, and which of three reads a caller wants.
- **[The grants](grants.md)** — what a client was allowed, which every token is issued under: what
  opens one and what it records, how a revocation and a lifetime reach it rather than one token, and
  what collects it once it is finished.
- **[Security](security.md)** — what each surface's gate does and does not protect, what a scope is
  allowed to mean, how a credential becomes an authentication, and what a token carries and how it
  is checked.
- **[The security context](security-context.md)** — the address, the user agent and the location a
  request is believed to carry: which proxy a deployment names, what naming one promises and what it
  does not, and how long a place somebody signs in from is kept.
- **[Logging](logging.md)** — what the server says about itself while it runs: what an ERROR line
  means, the dependencies `/health` reports degraded, and why a log line never makes its caller
  wait.
- **[Telemetry](telemetry.md)** — what the server exports to an OpenTelemetry collector: the
  signals that leave and the query string that never does, the runtime metrics a native image can
  fill, and what the collector failing costs.

## Neither a design document nor a rule

Two documents here describe no part of the server, and [the design documentation
standard](../standards/docs-design-standard.md) governs neither. Each says what shapes it
instead.

- **[Design FAQ](design-faq.md)** — decisions taken once, with the options that lost. Its own
  preamble says what belongs in it and what an entry carries.
- **[Running locally](running-locally.md)** — setting the project up, running it on the JVM and as a
  native image, and running both test suites.

---

← [Documentation](../index.md)
