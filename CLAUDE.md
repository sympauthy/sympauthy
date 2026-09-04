# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## What this is

SympAuthy is a self-hosted OAuth2 and OpenID Connect authorization server: it owns the accounts a
set of applications share, issues the tokens they trust, and serves the interactive flow a person
signs in through. Kotlin and Micronaut, coroutines throughout, R2DBC against PostgreSQL **and** H2,
compiled to a GraalVM native image.

**`docs/` is the authority** (start at `docs/index.md`). Read the document governing a change before
the code it governs, and put a new design decision there before or alongside its code. The standards
are symlinked into `.claude/rules/`, each loaded when a file it governs is read, so a rule one of
them states is not restated here — it would be a second copy, and the copy that drifts.

**Rules, not inventory.** What exists is answered by the package tree, `git log` and `docs/`. What
belongs here is what no symlinked standard carries: how the project is run, and the shape of the
system the descriptions in `docs/` hold. Never a status report.

**Treat an absence as a decision, not an oversight.** Every standard ends with what it deliberately
does not cover. If you end a deferral, say there why.

## Commands

```sh
./gradlew test                   # unit tests; the repository ones start a PostgreSQL container
./gradlew compileKotlin          # compile only
./gradlew build                  # compile, test, package
./gradlew nativeCompile          # the production artifact; slow

MICRONAUT_CONFIG_FILES=$(pwd)/config/application.yml \
  MICRONAUT_ENVIRONMENTS=default,admin ./gradlew :server:run

./gradlew test --tests 'com.sympauthy.business.manager.ScopeManagerTest'
```

Integration tests never run in `build`/`check`/`test`. They point at an image tag, not at your
source, so **rebuild the image after every code change**:

```sh
./gradlew :server:dockerBuild
export GITHUB_ACTOR=$(gh api user --jq .login) GITHUB_TOKEN=$(gh auth token)
./gradlew :integration-tests:integrationTest -Dsympauthy.image=server:latest
```

Full setup is `docs/running-locally.md`.

## Architecture — `docs/architecture.md`, `docs/general-code-standard.md`

- **`api`** — HTTP boundary. Controllers, resources, mappers, filters, error handlers.
- **`business`** — managers (use cases + transaction boundary), models, entity mappers.
- **`data`** — R2DBC entities and repositories, with a PostgreSQL and an H2 twin for each.
- **`config`** — properties → parser → validator → factory → model.
- **`security`** — the authentications and the token validator.

**Every API surface is gated differently**: `/api/oauth2` and `/api/openid` by the protocol's own
rules, `/api/v1/flow` by `ROLE_STATE`, `/api/v1/client` by a client scope, `/api/v1/admin` by an
admin scope. A surface carries a version only where the contract is ours to break — the protocol
prefixes are named by their specifications and are not versioned.

**A model per layer, translated at every boundary.** A manager never returns an entity.

**Dependency rules**: `api` never imports `data`; `business` never imports `api` *except* the OAuth2
exception carve-out; `data` imports neither.
