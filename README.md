# SympAuthy

An open-source, self-hosted authorization server.

## Getting started

To get started with SympAuthy, please refer to
the [Getting Started guide](https://sympauthy.github.io/getting-started/).

## Setting up the application for local development

### Requirements

- **JDK**: Oracle GraalVM 25

### Create the application configuration

For development purpose, we will create an OAuth2 client that has access to all APIs to be able to test all
the features of SympAuthy:

- **Client ID**: dev
- **Client Secret**: dev
- **Allowed Scopes**:
    - profile: Authorize the end-user(you) to get their personal user info.
    - phone: Authorize the end-user(you) to get their phone number.
    - users:*: Authorize the end-user(you) to access User APIs of SympAuthy.
    - invitations:*: Authorize the end-user(you) to manage invitations.

As SympAuthy is fully configurable using a text file, we can create a file **application.yml** in the **config**
directory
of this project and copy the following content:

```yaml
r2dbc:
  datasources:
    default:
      url: r2dbc:h2:file://localhost/./sympauthy

auth:
  issuer: http://localhost:8080

templates:
  clients:
    default:
      authorization-flow: local

clients:
  admin:
    allowed-redirect-uris:
      - http://localhost:5174/callback # Allow local instance of sympauthy-admin
  dev:
    public: false
    secret: dev
    allowed-grant-types:
      - authorization_code
      - refresh_token
      - client_credentials
    allowed-redirect-uris:
      - https://example.com
    default-scopes:
      - openid
      - profile
      - phone
    allowed-scopes:
      - profile
      - phone
      - users:read
      - users:claims:read
      - users:claims:write
      - invitations:read
      - invitations:write

flows:
  local:
    type: web
    sign-in: http://localhost:5173/sign-in
    sign-up: http://localhost:5173/sign-up
    collect-claims: http://localhost:5173/claims/edit
    validate-claims: http://localhost:5173/claims/validate
    error: http://localhost:5173/error
    mfa: http://localhost:5173/mfa
    mfa-totp-challenge: http://localhost:5173/mfa/totp
    mfa-totp-enroll: http://localhost:5173/mfa/totp/enroll

invitations:
  first-admin:
    url-template: "http://localhost:5174/register?invitation_token={token}"

urls:
  root: http://localhost:8080
```

You can refer to the [configuration documentation](https://sympauthy.github.io/technical/configuration/) to learn more about all available configuration options.

#### Configure a database

- [PostgreSQL](https://www.postgresql.org)
- [H2](https://www.h2database.com)

##### [PostgreSQL](https://sympauthy.github.io/technical/configuration/database.html#postgresql)

```yaml
r2dbc:
  datasources:
    default:
      url: r2dbc:postgresql://localhost:5432/sympauthy
```

##### [H2](https://sympauthy.github.io/technical/configuration/database.html#h2-in-memory-for-development-only)

- Stored in a local **sympauthy.mv.db** file:

```yaml
r2dbc:
  datasources:
    default:
      url: r2dbc:h2:file://localhost/./sympauthy
```

- In memory:

```yaml
r2dbc:
  datasources:
    default:
      url: r2dbc:h2:mem://localhost/sympauthy
```

### Launch the server locally

This project is a simple Java application built using [Gradle](https://gradle.org/).
You can launch it with any IDE supporting Gradle or directly using Gradle in the command line.

#### Gradle

```bash
MICRONAUT_CONFIG_FILES=$(pwd)/config/application.yml MICRONAUT_ENVIRONMENTS=default,admin ./gradlew :core:run
```

#### IntelliJ

Add a new **Micronaut** configuration:

- **Name**: JVM - Application
- **Main class**: com.sympauthy.Application
- **Classpath**: sympauthy.server.main
- **Working directory**: $ProjectFileDir$
- **Environment variables**:
    - **MICRONAUT_ENVIRONMENTS**: default,admin
    - **MICRONAUT_CONFIG_FILES**: config/application.yml

### Build and run the native image locally

#### Gradle

```bash
./gradlew nativeCompile
MICRONAUT_CONFIG_FILES=$(pwd)/config/application.yml MICRONAUT_ENVIRONMENTS=default,admin ./server/build/native/nativeCompile/server
```

#### IntelliJ

Open the **Gradle** window and double-click on **sympauthy > server > Tasks > build > nativeCompile**.
It should create a new configuration that you can rename into: Native - Compile.

Then add a new **Shell script** configuration:

- **Name**: Native - Application
- **Execute**: Script Text
- **Script text**: MICRONAUT_CONFIG_FILES=config/application.yml MICRONAUT_ENVIRONMENTS=default,admin
  ./server/build/native/nativeCompile/server
- **Working directory**: $ProjectFileDir$
- **Execute in Terminal**: Unchecked

Then click on **Add** button in the **Before launch** section:

- Select a **Run another configuration**
- Select the Gradle configuration we have created.

> Native compilation is slow. You may create an additional configuration without the Before launch to only run the
> previous build.

### Test the application

Open the following URL in your browser to access the authorization flow:

```
http://localhost:8080/api/oauth2/authorize?client_id=dev&redirect_uri=https://example.com&response_type=code&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256
```

> The `code_challenge` above is the S256 hash of the verifier `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk`.
> Use this verifier when exchanging the authorization code for tokens at the token endpoint.

#### Bruno collection

A [Bruno](https://www.usebruno.com/) collection is available in the `bruno/` folder to test the API endpoints.

Open the collection in Bruno, then configure the **Local** environment:

1. Open the **Environments** panel and select **Local**
2. Set `clientId` to the ID of the OAuth2 client you want to test with (defaults to `admin` as configured above)
3. Set the following secret variables:
    - `login`: the login of the test user
    - `password`: the password of the test user

Bruno stores secret variable values locally and never writes them back to the collection files, so credentials are never
committed to the repository.
