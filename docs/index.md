# SympAuthy — design

SympAuthy is a self-hosted OAuth2 and OpenID Connect authorization server. It owns the accounts a
set of applications share, issues the tokens those applications trust, and serves the interactive
flow a person signs in through.

It is a Kotlin [Micronaut](https://micronaut.io) application, non-blocking end to end and compiled
to a GraalVM native image; [Technology](technology.md) says why each of those was picked. A
deployment is a YAML file and a database, and nothing else.

These documents are the authority on how the server is built. They are read before the code they
govern, and a new decision is written here before or alongside the change that implements it. What
they are *not* is a user manual: how to configure and integrate with a running SympAuthy is the
[public documentation](https://sympauthy.github.io).

## Contents

- **[Architecture](architecture.md)** — the layers and what cuts across them, what makes something
  its own API surface and which of them carry a version, and the project layout on disk.
- **[General code standard](general-code-standard.md)** — the components a feature is made of, what
  each layer may import from another, and the naming that holds everywhere. Each layer then has its
  own: [`api`](api-layer-code-standard.md), [`business`](business-layer-code-standard.md),
  [`data`](data-layer-code-standard.md), [`config`](config-layer-code-standard.md).
- **[Exception standard](exception-code-standard.md)** — which exception each layer may throw, how a
  code names both its technical message and the one an end-user reads, and the one place the OAuth2
  specification overrides the rule.
- **[API standard](api-standard.md)** — what a client sees: how a route is spelled, what the JSON
  looks like, how a collection is paged, the body a failure returns, and why no redirect is a 307.
- **[Database standard](database-standard.md)** — how a table and a migration are written, and what
  keeps the PostgreSQL and H2 schemas from drifting apart.
- **[Internationalization standard](i18n-standard.md)** — why there is a bundle per audience, how a
  key is named, and how it reaches the reader in their own language.
- **[Comment standard](comment-standard.md)** — what a KDoc carries, and where the rationale that
  does not belong in one goes instead.
- **[Testing standard](testing-standard.md)** — what each kind of subject is tested with, where its
  test lives, how it is named, and what an integration test is expected to prove.
- **[Native image standard](native-image-standard.md)** — the closed-world rules that compile
  cleanly, pass every test, and then fail in production.
- **[The interactive flow](interactive-flow.md)** — the session, the purposes an engine sequences
  over it, and how a purpose or a step is added.
- **[Security](security.md)** — how a credential becomes an authentication, what a scope is allowed
  to mean, and what each surface's gate does and does not protect.
- **[Technology](technology.md)** — the frameworks and runtime the server is built on, and why each
  was picked.
- **[Documentation standard](docs-standard.md)** — how a standard here is written, and what it
  states in place of the code that happens to follow it.
- **[Design FAQ](design-faq.md)** — decisions taken once, with the options that lost.
- **[Running locally](running-locally.md)** — setting the project up, running it on the JVM and as a
  native image, and running both test suites.

## Goals

- **Own the accounts once, for every application.** A person has one identity across a set of
  products, and no product stores a credential.
- **Be a standards server, not a bespoke login.** OAuth 2.1, OpenID Connect and the RFCs around them
  are the contract, so any conforming client library already works and nothing has to be written
  against SympAuthy specifically.
- **Keep the sign-in pages replaceable.** The interactive flow is an API driven by the server, and
  the pages are a separate application any deployment may rewrite.
- **Make configuration the product surface.** What a deployment can change is a YAML file, validated
  in full at startup, and a server whose configuration is wrong refuses to report itself ready.
- **Be cheap to self-host.** A native image that starts in milliseconds, against PostgreSQL in
  production or H2 with no database to install at all.
