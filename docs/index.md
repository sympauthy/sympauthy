# SympAuthy — documentation

SympAuthy is a self-hosted OAuth2 and OpenID Connect authorization server. It owns the accounts a
set of applications share, issues the tokens those applications trust, and serves the interactive
flow a person signs in through.

It is a Kotlin [Micronaut](https://micronaut.io) application, non-blocking end to end and compiled
to a GraalVM native image; [Technology](design/technology.md) says why each of those was picked. A
deployment is a YAML file and a database, and nothing else.

These documents are the authority on how the server is built. They are read before the code they
govern, and a new decision is written here before or alongside the change that implements it. What
they are *not* is a user manual: how to configure and integrate with a running SympAuthy is the
[public documentation](https://sympauthy.github.io).

## The two halves

They come in two kinds, and the folder is which kind a document is. The two are read at different
moments — a design document by somebody about to change the part it describes, a rule by somebody
about to break it — and each kind has a standard of its own saying how one is written, over [the one
that holds of both](standards/docs-standard.md).

- **[How the system works](design/index.md)**, in `design/` — a document per part of the server:
  what it does, what implements it, and why it was settled that way. [The design
  FAQ](design/design-faq.md) and [running locally](design/running-locally.md) live there too, held
  to the mechanics and to nothing else.
- **[How the code is written](standards/index.md)**, in `standards/` — the rules a change is held
  to, one subject per document, each named `<subject>-standard.md`. A standard reaches an agent
  when a file it governs is read, through the symlink in `.claude/rules/`.

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
