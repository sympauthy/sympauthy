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
├── feature/                # one class per user-facing flow, tagged "feature"
│   └── AuthorizationCodeFeatureIT.kt         # authorization-code + PKCE flow yields signed tokens
└── security/               # one class per risk scenario, tagged "security"
    ├── Authorize303SeeOtherIT.kt             # authorize redirects with 303, never 307
    └── TokenEndpointRejectsUnknownCodeIT.kt  # unknown authorization code is rejected with invalid_grant
```

**One scenario per class.** Each `*IT` class covers a single feature or risk and carries a class-level
KDoc of *what* it exercises and, for security, *why it matters*. The test is a `@ParameterizedTest`
over `@EnumSource(Database::class)`, so it runs once per database, and is tagged `feature` / `security`.

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
export GITHUB_ACTOR=<your-github-username>
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
var). This is how CI validates the current commit: it builds the native binary, packages it as
`sympauthy:it`, and runs the suite against that image. To reproduce locally (requires a GraalVM
toolchain able to run `nativeCompile`):

```bash
# 1. Native build needs the frontend resource dirs to exist (empty is fine — the flow driver hits the
#    JSON API, not the UI).
mkdir -p server/src/main/resources/sympauthy-flow server/src/main/resources/sympauthy-admin

# 2. Build the native binary and package it as a Docker image using the release Dockerfile.
./gradlew :server:nativeCompile
cp server/build/native/nativeCompile/server .github/docker/sympauthy
docker build -t sympauthy:it .github/docker

# 3. Run the suite against it.
./gradlew :integration-tests:integrationTest -Dsympauthy.image=sympauthy:it
```

## Adding a scenario

1. Create a new `*IT` class per scenario, extending `AbstractSympauthyIT`, with a class-level KDoc
   stating the feature (or, for security, the risk being tested) and citing its **source** — the RFC
   section, specification, or GitHub issue it comes from. Tag it `@Tag("feature")` or
   `@Tag("security")` and make the test a `@ParameterizedTest @EnumSource(Database::class)`.
2. Use `withContainer(database) { sympauthy, registry -> … }` to get a started container plus the mock
   flow frontend; it tears everything down and dumps container logs on failure.
3. Drive OAuth flows with `registry.newFlow()…run().exchange()`, or hit endpoints directly with the
   `httpGet` / `httpPostForm` / `discovery` / `verifyIdTokenSignature` helpers.

On failure, the SympAuthy container's logs are printed to stderr to make CI diagnostics actionable.
