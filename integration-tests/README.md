# integration-tests

End-to-end integration tests for SympAuthy. Each test boots the **SympAuthy native image as a Docker
container** — via the [`testcontainers-sympauthy`](https://github.com/sympauthy/testcontainers-sympauthy)
library — and drives it over real HTTP, against **each supported database (H2 and PostgreSQL)**.

Unlike the unit tests in `:server` (JVM, mocked collaborators), these exercise the actual compiled
binary end to end: native-image reflection/resource config, Flyway migrations, token signing, and the
R2DBC dialect differences between H2 and PostgreSQL. They are therefore slower and require Docker, so
they run **only** via the `integrationTest` task — never as part of `build`, `check` or `test`.

## Layout

```
src/integrationTest/kotlin/com/sympauthy/it/
├── SympauthyImage.kt       # resolves the image under test (system property → env var → nightly default)
├── Database.kt             # database matrix: H2 and a PostgreSQL companion container
├── AbstractSympauthyIT.kt  # shared config, container lifecycle, HTTP/JWKS/PKCE helpers
├── feature/                # one class per feature (happy + rejection paths), tagged "feature"
│   └── AuthorizationCodeFeatureIT.kt         # authorization-code + PKCE flow yields signed tokens
└── security/               # one class per risk scenario, tagged "security"
    ├── Authorize303SeeOtherIT.kt             # authorize redirects with 303, never 307
    └── TokenEndpointRejectsUnknownCodeIT.kt  # unknown authorization code is rejected with invalid_grant
```

**One class per feature or risk.** Each `*IT` class covers a single feature or security risk and carries
a class-level KDoc of *what* it exercises and, for security, *why it matters*. A feature's happy path and
its rejection/negative paths (bad input, missing scope, a disabled feature, a cross-client token, …) live
**together** in that one class as separate methods — they exercise the same endpoint, so keeping them in
one place keeps the feature's behaviour together; reserve a *new* class for a genuinely distinct feature
or risk. Every test is a `@ParameterizedTest` over `@EnumSource(Database::class)`, so it runs once per
database, and is tagged `feature` / `security`.

**Comment convention.** The one-line descriptions in this tree are terse labels — a **lowercase,
present-tense phrase with no trailing period** — stating a file's responsibility or the behaviour a
scenario verifies (for security, the risk). Fuller prose (with normal capitalisation and punctuation)
belongs in each class's KDoc, not the tree.

## Requirements

- **Docker** (or a compatible runtime: Podman, Colima, Rancher Desktop). Issuer/discovery URLs are
  pinned to `http://localhost:<port>`, so a host-reachable daemon is assumed.
- **JDK 25** (matches `:server`).
- **A GitHub token with `read:packages`.** `testcontainers-sympauthy` is published to GitHub Packages,
  which requires authentication even for public packages. Provide credentials via env vars
  (`GITHUB_ACTOR` + `GITHUB_TOKEN`) or Gradle properties (`gpr.user` + `gpr.token`). The `gh` CLI token
  works: `export GITHUB_TOKEN=$(gh auth token)`.

## Running

```bash
export GITHUB_ACTOR=$(gh api user --jq .login)
export GITHUB_TOKEN=$(gh auth token)

# Runs feature + security scenarios against H2 and PostgreSQL.
./gradlew :integration-tests:integrationTest
```

By default the tests run against the **published nightly image**
(`ghcr.io/sympauthy/sympauthy-nightly:latest`), which lets you run the harness without a GraalVM
toolchain. Run a single scenario class with Gradle's test filter:

```bash
./gradlew :integration-tests:integrationTest --tests '*OAuth2SecurityIT'
```

### Testing a specific image

Point the tests at any SympAuthy image with `-Dsympauthy.image=<ref>` (or the `SYMPAUTHY_IMAGE` env
var). To validate the current commit locally, build a **JVM image** from the working tree — it needs no
GraalVM toolchain and builds in seconds:

```bash
# 1. Build a JVM Docker image from the current code (Micronaut tags it `server:latest`).
./gradlew :server:dockerBuild

# 2. Authenticate to GitHub Packages so the testcontainers-sympauthy library resolves.
export GITHUB_ACTOR=$(gh api user --jq .login)
export GITHUB_TOKEN=$(gh auth token)

# 3. Run the suite against it.
./gradlew :integration-tests:integrationTest -Dsympauthy.image=server:latest
```

> **JVM image vs. native image — mind the gap.** The default nightly image, and the `sympauthy:it`
> image CI builds from each commit, are **GraalVM native images**; the `server:latest` image above runs
> the same code on the **JVM**. They share all the application logic, so the JVM image is the fast way to
> iterate on an integration test locally — but they are *not* the same runtime, and a scenario can pass
> on one while failing on the other. Native compilation is closed-world: it strips anything not provably
> reachable, so reflection, resource loading, dynamic proxies and serialization only work when declared
> in the native metadata (`reflect-config.json` / `resource-config.json` — e.g. every MapStruct `*Impl`).
> Missing metadata throws only at native runtime; on the JVM, which reflects freely, the same code just
> works. Treat a green JVM run as necessary but not sufficient — CI's native run is the source of truth.

## Adding a scenario

1. Create a new `*IT` class per scenario, extending `AbstractSympauthyIT`, with a class-level KDoc
   stating the feature (or, for security, the risk being tested) and citing its **source** — the RFC
   section, specification, or GitHub issue it comes from. Tag it `@Tag("feature")` or
   `@Tag("security")` and make the test a `@ParameterizedTest @EnumSource(Database::class)`.
2. Use `withContainer(database) { sympauthy, registry -> … }` to get a started container plus the mock
   flow frontend; it tears everything down and dumps container logs on failure. Use `withCustomContainer`
   when a scenario must configure the container itself (a confidential client, a feature toggle, a
   non-default claim set), or `withStartedContainer` for setups needing more than the single registry it
   manages (e.g. a second client with its own mock frontend).
3. Drive interactive OAuth flows with `registry.newFlow()…run().exchange()`. To call an endpoint, prefer
   the **generated OpenAPI client** over raw HTTP:
   `withApiClient(sympauthy, token) { ctx -> ctx.getBean(SomeApi::class.java).someOperation(…).block() }`
   drives the server through the typed `@Client` beans generated from its contract (the same source of
   truth as the request/response models). For a rejected call, read the status/body from the thrown
   `HttpClientResponseException` **inside** the `withApiClient` block — the response buffer is released
   once the context closes. Reach for the raw `httpGet` / `httpPostForm` helpers only when a scenario must
   send something the typed client cannot express (a malformed/forged request, or an assertion on
   redirect/`Location` or header behaviour). `discovery` / `verifyIdTokenSignature` remain available.

On failure, the SympAuthy container's logs are printed to stderr to make CI diagnostics actionable.
