# SympAuthy

An open-source, self-hosted OAuth2 and OpenID Connect authorization server. It owns the accounts a
set of applications share, issues the tokens those applications trust, and serves the flow a person
signs in through.

Kotlin and Micronaut, non-blocking end to end, compiled to a GraalVM native image, against
PostgreSQL or H2.

## Using SympAuthy

Start with the [Getting Started guide](https://sympauthy.github.io/getting-started/). How to
configure and integrate with a running server is the
[public documentation](https://sympauthy.github.io).

## Working on SympAuthy

**[`docs/`](docs/index.md) is the authority on how this server is built**, in two halves:
[`design/`](docs/design/index.md) for how each part works and the decisions behind it, and
[`standards/`](docs/standards/index.md) for the rules every layer holds to. Read the document
governing a change before the code it governs.

To set the project up and run it, see [Running locally](docs/design/running-locally.md).
