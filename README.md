# SympAuthy

An open-source, self-hosted authorization server.

## Setting up the application for local development

### Requirements

- **JDK**: Oracle GraalVM 25

### Create the application configuration

For development purpose, we will create an OAuth2 client that has access to Admin APIs to be able to test all
the features of SympAuthy:

- **Client ID**: dev
- **Client Secret**: my-secret
- **Allowed Scopes**:
  - openid: Required to access to the authorization flow.
  - profile: Authorize us to get our personal user info.
  - http://localhost:8090/admin: Authorizes us to access Admin APIs of SympAuthy.

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
  audience: sympauthy

clients:
  default:
    authorization-flow: local
  dev:
    secret: my-secret
    flow: local
    allowed-redirect-urls:
      - https://example.com
    allowed-scopes:
      - openid
      - profile
      - http://localhost:8080/admin

flows:
  local:
    type: web
    sign-in: http://localhost:5173/sign-in
    collect-claims: http://localhost:5173/claims/edit
    validate-claims: http://localhost:5173/claims/validate
    error: http://localhost:5173/error

urls:
  root: http://localhost:8080
```

You can refer to the [wiki]() to

#### Configure a database

- [PostgreSQL](https://www.postgresql.org)
- [H2](https://www.h2database.com)

##### PostgreSQL

**FIXME**

##### H2

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
MICRONAUT_CONFIG_FILES=$(pwd)/config/application.yml MICRONAUT_ENVIRONMENTS=default ./gradlew :core:run
```

#### IntelliJ

Add a new **Micronaut** configuration:
- **Name**: JVM - Application
- **Main class**: com.sympauthy.Application
- **Classpath**: sympauthy.server.main
- **Working directory**: $ProjectFileDir$
- **Environment variables**:
  - **MICRONAUT_ENVIRONMENTS**: default
  - **MICRONAUT_CONFIG_FILES**: config/application.yml

### Build and run the native image locally

#### Requirements

- GraalVM 25

#### Gradle

```bash
./gradlew nativeCompile
MICRONAUT_CONFIG_FILES=$(pwd)/config/application.yml MICRONAUT_ENVIRONMENTS=default ./server/build/native/nativeCompile/server
```

#### IntelliJ

Open the **Gradle** window and double-click on **sympauthy > server > Tasks > build > nativeCompile**.
It should create a new configuration that you can rename into: Native - Compile.

Then add a new **Shell script** configuration:
- **Name**: Native - Application
- **Execute**: Script Text
- **Script text**: MICRONAUT_CONFIG_FILES=config/application.yml MICRONAUT_ENVIRONMENTS=default ./server/build/native/nativeCompile/server
- **Working directory**: $ProjectFileDir$
- **Execute in Terminal**: Unchecked

Then click on **Add** button in the **Before launch** section:
- Select a **Run another configuration**
- Select the Gradle configuration we have created.

> Native compilation is slow. You may create an additional configuration without the Before launch to only run the previous build.

### Test the application

Open the following URL in your browser to access the authorization flow:

```
http://localhost:8080/api/oauth2/authorize?client_id=dev&redirect_uri=https://example.com&response_type=code
```

#### Bruno collection

A [Bruno](https://www.usebruno.com/) collection is available in the `bruno/` folder to test the API endpoints.

Open the collection in Bruno, then configure the **Local** environment:

1. Open the **Environments** panel and select **Local**
2. Set `clientId` to the ID of the OAuth2 client you want to test with (defaults to `dev` as configured above)
3. Set the following secret variables:
   - `clientSecret`: the secret of the OAuth2 client (defaults to `my-secret`)
   - `login`: the login of the test user
   - `password`: the password of the test user

Bruno stores secret variable values locally and never writes them back to the collection files, so credentials are never committed to the repository.
