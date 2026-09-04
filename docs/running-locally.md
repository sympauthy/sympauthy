# Running locally

Setting the project up, running the server on the JVM and as a native image, and running both test
suites. What the server *is* is [the index](index.md); how to configure a real deployment is the
[public documentation](https://sympauthy.github.io/technical/configuration/).

## Requirements

- **Oracle GraalVM 25** — the JDK. A standard JDK 25 runs everything except the native build.
- **Docker**. The repository tests start a PostgreSQL container, so `./gradlew test` needs it too.
- **A GitHub Packages token with `read:packages`**, for the integration tests only. The
  Testcontainers helper library they use is published there and needs authentication even to read.

## Configuration

The server reads a YAML file named by `MICRONAUT_CONFIG_FILES`, and layers it over the defaults
selected by `MICRONAUT_ENVIRONMENTS`. For development, put yours in `config/application.yml` — the
directory is git-ignored apart from a sample.

A development configuration needs four things: a datasource, an issuer, at least one client, and the
page addresses of a flow. This is enough to sign in end to end:

```yaml
r2dbc:
  datasources:
    default:
      url: r2dbc:h2:file://localhost/./sympauthy

auth:
  issuer: http://localhost:8080

urls:
  root: http://localhost:8080

templates:
  clients:
    default:
      authorization-flow: local

clients:
  dev:
    public: false
    secret: dev
    allowed-grant-types: [ authorization_code, refresh_token, client_credentials ]
    allowed-redirect-uris: [ https://example.com ]
    default-scopes: [ openid, profile ]
    allowed-scopes: [ profile, phone, users:read, users:claims:read, users:claims:write ]

flows:
  local:
    type: web
    sign-in: http://localhost:5173/sign-in
    sign-up: http://localhost:5173/sign-up
    collect-claims: http://localhost:5173/claims/edit
    validate-claims: http://localhost:5173/claims/validate
    error: http://localhost:5173/error
```

**The flow addresses point at the sign-in pages, which are a separate application.** They do not
have to be running for the server to start, but a flow cannot be completed without them.

**A configuration error takes readiness down rather than stopping the process**, so a server that
starts and then reports itself unhealthy is telling you to read the startup log. Every error in the
file is reported at once — see [the `config` layer standard](config-layer-code-standard.md).

### Choosing a database

H2 in a local file, which is the default above and survives restarts:

```yaml
url: r2dbc:h2:file://localhost/./sympauthy
```

H2 in memory, which is empty on every start:

```yaml
url: r2dbc:h2:mem://localhost/sympauthy
```

PostgreSQL, which is what a deployment runs:

```yaml
url: r2dbc:postgresql://localhost:5432/sympauthy
```

The schema is migrated at startup against whichever is configured. A migration whose version is
unreleased may be [edited in place](database-standard.md#migrations), so pulling a change to one
means recreating the database rather than migrating it.

## Running the server

```sh
MICRONAUT_CONFIG_FILES=$(pwd)/config/application.yml \
MICRONAUT_ENVIRONMENTS=default,admin \
./gradlew :server:run
```

In IntelliJ, a **Micronaut** run configuration on `com.sympauthy.Application`, with the classpath of
`sympauthy.server.main`, the working directory at the project root, and those two variables in the
environment.

To sign in end to end, open the authorize endpoint with a client from the configuration:

```
http://localhost:8080/api/oauth2/authorize
  ?client_id=dev
  &redirect_uri=https://example.com
  &response_type=code
  &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
  &code_challenge_method=S256
```

That challenge is the S256 hash of the verifier `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk`; use
the verifier when exchanging the code at the token endpoint.

**A Bruno collection in `bruno/` drives the API by hand.** Select the **Local** environment, set
`clientId`, and set `login` and `password` as secret variables — Bruno keeps secret values out of
the collection files, so nothing is committed.

## The native image

```sh
./gradlew nativeCompile

MICRONAUT_CONFIG_FILES=$(pwd)/config/application.yml \
MICRONAUT_ENVIRONMENTS=default,admin \
./server/build/native/nativeCompile/server
```

Compilation is slow — minutes, not seconds — so in IntelliJ it is worth two configurations: one that
compiles and runs, and one that only runs the binary already built.

**This is the artifact a deployment runs, and it is the only way to exercise
[the native-image rules](native-image-standard.md).** A change touching generated mappers,
reflection or resource loading is not finished until this has run.

## Tests

```sh
./gradlew test                          # unit tests; needs Docker for the repository tests
./gradlew test --tests 'com.sympauthy.business.manager.ScopeManagerTest'
./gradlew compileKotlin                 # compile only
./gradlew build                         # compile, test, package
```

### Integration tests

They live in their own module, need Docker, and **never run as part of `build`, `check` or `test`**.
They boot the server as a container and drive it over HTTP against both databases.

By default they pull a published nightly image, which does not contain your working tree. To test
what you have actually changed, build an image from it first:

```sh
# 1. Build a JVM image from the working tree. Seconds, and no GraalVM needed.
./gradlew :server:dockerBuild

# 2. Authenticate to GitHub Packages so the Testcontainers helper resolves.
export GITHUB_ACTOR=$(gh api user --jq .login)
export GITHUB_TOKEN=$(gh auth token)

# 3. Run the suite against it.
./gradlew :integration-tests:integrationTest -Dsympauthy.image=server:latest
```

**Rebuild the image after every code change.** The tests point at a tag, not at your source, and a
stale image fails or passes for reasons that have nothing to do with what you just wrote.

One scenario:

```sh
./gradlew :integration-tests:integrationTest -Dsympauthy.image=server:latest \
  --tests '*AuthorizationCodeFeatureIT'
```

**A JVM image is not the image CI runs.** It exercises the code but none of the closed-world
constraints, so a green local run is necessary and not sufficient. What the tests are expected to
prove is [the testing standard](testing-standard.md).

### Changing a flow-configuration response

The Testcontainers helper library parses the flow configuration the server returns, so changing the
shape of one of those responses breaks the integration tests in a way that looks unrelated to the
change. Those resources are a wire format shared with another repository, and altering one means
releasing that library and bumping it here in the same change.

---

← [Design documentation](index.md)
