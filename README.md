# SympAuthy

An open-source, self-hosted authorization server.

## Setting up the application for local development

### Requirements

- JDK 21

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
- **Name**: Application
- **Main class**: com.sympauthy.Application
- **Classpath**: sympauthy.server.main
- **Working directory**: $ProjectFileDir$
- **Environment variables**:
  - **MICRONAUT_ENVIRONMENTS**: default
  - **MICRONAUT_CONFIG_FILES**: config/application.yml

### Test the application

Open the following URL in your browser to access the authorization flow:

```
http://localhost:8080/api/oauth2/authorize?client_id=dev&redirect_uri=https://example.com&response_type=code
```
