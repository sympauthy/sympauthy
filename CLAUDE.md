# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## What this is

SympAuthy is a self-hosted OAuth2 and OpenID Connect authorization server: it owns the accounts a
set of applications share, issues the tokens they trust, and serves the interactive flow a person
signs in through. Kotlin and Micronaut, coroutines throughout, R2DBC against PostgreSQL **and** H2,
compiled to a GraalVM native image.

**`docs/` is the authority** (start at `docs/index.md`). Read the document governing a change before
the code it governs, and put a new design decision there before or alongside its code. This file
carries rules only — each section names the document holding their reasoning. The standards are also
symlinked into `.claude/rules/`.

**Rules, not inventory.** What exists is answered by the package tree, `git log` and `docs/`. Add a
rule here when a decision would otherwise be re-litigated; never a status report.

**Treat an absence as a decision, not an oversight.** Every standard ends with what it deliberately
does not cover. If you end a deferral, say there why.

## Commands

```sh
./gradlew test                   # unit tests — fast, no Docker
./gradlew compileKotlin          # compile only
./gradlew build                  # compile, test, package
./gradlew nativeCompile          # the production artifact; slow

MICRONAUT_CONFIG_FILES=$(pwd)/config/application.yml \
  MICRONAUT_ENVIRONMENTS=default,admin ./gradlew :server:run

./gradlew test --tests 'com.sympauthy.business.manager.ScopeManagerTest'
```

Integration tests need Docker and never run in `build`/`check`/`test`. They point at an image tag,
not at your source, so **rebuild the image after every code change**:

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
- **`security`** — the three authentications and the token validator.

**Five API surfaces**, gated differently: `/api/oauth2` and `/api/openid` (the protocol's own
rules), `/api/v1/flow` (`ROLE_STATE`), `/api/v1/client` (a client scope), `/api/v1/admin` (an admin
scope). Only the last three carry a version — the first two are named by their specifications.

**A model per layer, translated at every boundary.** A manager never returns an entity.

**Dependency rules**: `api` never imports `data`; `business` never imports `api` *except* the OAuth2
exception carve-out; `data` imports neither.

## Rules that compile and then fail

- **`open` on a class and method carrying an AOP annotation** — Kotlin classes are final, the
  annotation needs a proxy, and `all-open` is not applied.
- **A generated mapper must be registered in `BusinessMapperFactory` (or the API equivalent) *and*
  in `reflect-config.json`.** The first fails at startup; the second only in the native image.
  `docs/native-image-standard.md`.
- **Never `'` before `{` in a message bundle** — interpolation silently stops.
  `docs/exception-code-standard.md`.
- **Every repository needs both dialect twins**, or the bean is missing on one database only.
- **Document a parameter on the parameter**, never in `@Operation(parameters = [...])` — the schema
  is dropped and client generation fails two build steps later. `docs/api-layer-code-standard.md`.

## Naming — `docs/general-code-standard.md`

`OAuth2` not `Oauth2`; `OpenIdConnect` not `Oidc` or `OpenId` (the YAML key `oidc` is the one
exception). `…OrNull` for a nullable-returning method, with a throwing twin that delegates to it.
`updateXxx(@Id id, …)` on a repository, never `And` in the name. `identifierClaims` /
`consentedClaims` / `allClaims`, never a bare `claims`.

## The wire — `docs/api-standard.md`, `docs/exception-code-standard.md`

- **303, never 307.** OAuth 2.1 forbids it; a 307 resubmits the credentials.
- snake_case JSON set explicitly; **nulls are omitted, not sent**; path parameters are camelCase.
- No `PUT`; `PATCH` for partial updates; `DELETE` returns 204.
- An error code *is* a message-bundle key, and `description.<code>` is its end-user twin. Renaming
  one is a breaking API change.
- `businessExceptionOf` / `recoverableBusinessExceptionOf` / `internalBusinessExceptionOf` — the
  question is whether the caller could send something else.

## Database — `docs/database-standard.md`

Every migration exists **twice**, same name, one per dialect folder; only the dialect's spelling
differs. A constraint one dialect cannot express holds only on the other, so the schema is never the
whole argument — the rule also lives in the manager. Before 1.0, edit a `_new` file in place rather
than adding an `_edit`.

## Testing — `docs/testing-standard.md`

JUnit 5 + MockK, `runTest`, tests named `` `method - case` ``. Do not verify a call the assertion
already proves. Integration tests: one class per feature or risk with happy and negative paths
together, driven through the generated client, parameterized over both databases.

## Writing — `docs/comment-standard.md`, `docs/docs-standard.md`

A comment carries only what the reader cannot get from the code, the framework's documentation, or a
standard. Never paraphrase an annotation; never restate a convention. KDoc on declarations, `//`
only inside a body, property docs above the property.

**In `docs/`, a description names real code and a standard does not.** A standard states its rule as
a shape — `…Manager`, `find…OrNull()` — because an example lifted from the codebase rots without
anything failing. A new document is added to `docs/index.md` and, if it is a standard, symlinked
into `.claude/rules/`.
