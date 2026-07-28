# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SympAuthy is an open-source, self-hosted OAuth2/OpenID Connect authorization server built with Micronaut 4 and Kotlin (
coroutines). It supports GraalVM native image compilation.

## Build & Run Commands

```bash
# Build
./gradlew build

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests com.sympauthy.business.manager.auth.ScopeGrantingManagerTest

# Compile check only (no tests)
./gradlew compileKotlin

# Run application (JVM)
MICRONAUT_CONFIG_FILES=$(pwd)/config/application.yml MICRONAUT_ENVIRONMENTS=default,admin ./gradlew :server:run

# Build native image
./gradlew nativeCompile

# Run native image
MICRONAUT_CONFIG_FILES=$(pwd)/config/application.yml MICRONAUT_ENVIRONMENTS=default,admin ./server/build/native/nativeCompile/server
```

## Architecture

Multi-module Gradle project (root + `server`). All source code is in `server/src/main/kotlin/com/sympauthy/`.

### Layer Structure

- **`api/`** — HTTP controllers, DTOs (`resource/`), request/response mappers, error handlers, filters
- **`business/`** — Core logic in managers (`manager/`), domain models (`model/`), entity-to-model mappers (`mapper/`)
- **`data/`** — R2DBC entities (`model/`), reactive repositories (`repository/`), database-specific repos (
  `postgresql/`, `h2/`)
- **`config/`** — Configuration properties (`properties/`), sealed config models (`model/`), parsers (`parsing/`),
  validators (`validation/`), config factories (`factory/`)
- **`security/`** — Authentication/authorization (token validation, user/state authentication)

### Key Conventions

#### API (com.sympauthy.api)

- **No HTTP 307 redirects** — OAuth
  2.1 [forbids 307 redirects](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1#name-http-307-redirect)
  because they cause the browser to resubmit the POST body (including credentials) to the redirect target. Always use *
  *303 See Other** (`HttpResponse.seeOther()`) which forces a GET on the redirect target.

#### Config (com.sympauthy.config)

- **Config sealed class pattern** — `EnabledXxxConfig` / `DisabledXxxConfig` with `orThrow()` extension for required
  configs, `as? EnabledXxxConfig` for optional feature checks
- **Config three-layer architecture** — Each config domain is split across three layers:
  - **Parser** (`config/parsing/XxxConfigParser.kt`): `@Singleton` bean. Only does type conversion (
    `ctx.parse { parser.getXxxOrThrow(...) }`) and template resolution. Returns a parsed intermediate data class with
    nullable fields. Never validates values, never references other config domains.
  - **Validator** (`config/validation/XxxConfigValidator.kt`): `@Singleton` bean. Handles intra-domain validation (
    value ranges, consistency checks) and cross-domain validation (audience exists, scope exists). Returns final
    business models.
  - **Factory** (`config/factory/XxxConfigFactory.kt`): `@Factory` bean. Thin orchestration: creates
    `ConfigParsingContext`, calls parser, calls validator, assembles `EnabledXxxConfig` or `DisabledXxxConfig`.
- **ConfigParsingContext** — All parsers and validators use `ConfigParsingContext` for error accumulation. Use
  `ctx.parse { }` to catch `ConfigurationException` automatically, `ctx.addError()` for explicit validation errors,
  `ctx.child()` + `ctx.merge()` for sub-sections.
- **Config vs Manager separation** — Config factories validate YAML input only (no HTTP calls, no external
  interactions). Runtime operations (e.g. OpenID Connect discovery) belong in the manager layer. Error message keys must
  reflect where they occur (`config.*` for validation errors, `provider.*` for runtime errors).

#### Business (com.sympauthy.business)

- **Business manager guidelines**: https://sympauthy.github.io/contributing/backend/how-to-write-a-business-manager.html
- **Managers never return entities** — only `business.model` types are exposed to controllers
- **Exception factory methods** — `businessExceptionOf()`, `recoverableBusinessExceptionOf()` (user-retryable),
  `internalBusinessExceptionOf()` (server errors). Error messages in `error_messages.properties`
- **OAuth2 managers may throw `OAuth2Exception`** — managers under `business.manager.auth.oauth2` (e.g. `TokenManager`,
  `TokenExchangeManager`) are an intentional exception to the "managers throw business exceptions" rule: they may throw
  `OAuth2Exception` directly (via `oauth2ExceptionOf(...)`) so the standardized OAuth2 error code (`invalid_grant`,
  `invalid_request`, `invalid_target`, `access_denied`, …) is emitted per the OAuth2 / RFC 8693 specification rather
  than being flattened to a single code at the controller boundary.
- **Error message placeholders** — Never use `'` (single quote) before `{` in `error_messages.properties` because the
  MessageSource interprets `'{...}'` as a literal string and does not perform placeholder replacement. Write `{scope}`
  directly, not `'{scope}'`.
- **Collected claim list naming** — Use `identifierClaims` for claims that identify the user (fetched via
  `findIdentifierByUserId`), `consentedClaims` for claims filtered by consented scopes, and `allClaims` only when all
  claims are present without consent filtering (e.g. admin code).

#### Data (com.sympauthy.data)

- **Repository update methods** — `suspend fun updateXxx(@Id id: UUID, xxx: T)`. Never use `And` in update method names.
  `delete()` returns `Int` in Micronaut Data 4.x
- **DB-specific repository implementations** — Each repository interface in `data/repository/` must have a PostgreSQL
  and H2 implementation in `data/postgresql/repository/` and `data/h2/repository/`. These are empty interfaces extending
  the base repository, annotated with `@R2dbcRepository(dialect = ...)` and
  `@Requires(condition = DefaultDataSourceIsPostgreSQL/H2::class)`.

#### Others

- **Naming conventions for protocols** — Use `OAuth2` (not `Oauth2`) and `OpenIdConnect` (not `Oidc` or `OpenId`) in
  class names, method names, and packages. Examples: `ProviderOAuth2Config`,
  `InteractiveAuthFlowSessionOAuth2ProviderManager`, `OpenIdConnectDiscoveryClient`, `ProviderOpenIdConnectConfig`. The
  YAML config key `oidc` is kept as shorthand for user-facing configuration.
- **Nullable methods use `OrNull` suffix** — e.g., `findByCodeOrNull()` returns `T?`
- **All async operations prefer `suspend` functions** — no callbacks or reactive streams. Wrap blocking third-party
  calls (e.g. Nimbus `JWKSourceBuilder`) in `withContext(Dispatchers.IO)`.
- **Prefer DB storage over JWT embedding for transient flow state** — Store nonces, provider IDs, verifiers in the
  database (e.g. the `interactive_flow_session_oauth2` / `interactive_flow_session_provider` tables). Keep only the
  minimal identifying data (e.g. a UUID / jti) and reconstruct the full value at runtime when needed.
- **MapStruct mappers** — See [Libraries](#libraries). New `*Impl` classes must be registered in
  `META-INF/native-image/.../reflect-config.json` for native image support

### Scope Type Hierarchy

Scopes use a sealed class hierarchy (`Scope` → `ConsentableUserScope`, `GrantableUserScope`, `ClientScope`). Consentable
scopes come from user consent, grantable scopes from rules/auto-grant, client scopes are for `client_credentials` flows
only.

### Interactive Flow Session

The interactive end-user flow is a **purpose-agnostic engine** built around the sealed `InteractiveFlowSession`
primitive (`business/model/flow/`, subtypes `OnGoing`/`Completed`/`Failed`, `Expirable`). This replaced the old
`AuthorizeAttempt`. The session carries only **flow-generic state** (id, `type`, `flowId`, `sessionDate`,
`expirationDate`, `userId`, MFA, terminal status). Each concern-specific piece of state lives in its **own record keyed
by `session_id`**, fetched on demand via its manager — never carried on the session:

- **`InteractiveFlowSessionOAuth2`** — the client's OAuth2 request context (clientId, redirectUri, requestedScopes,
  PKCE, nonce, invitation) + consent/grant. Fetched via `InteractiveFlowSessionOAuth2Manager.fetchOAuth2(session)`.
- **`InteractiveFlowSessionProvider`** — third-party-provider authorization state (providerId + OIDC nonce jti).
  Fetched via `InteractiveFlowSessionProviderManager.fetchProviderOrNull(session)`.

**Three managers, split by concern** (`business/manager/flow/`):

- `InteractiveFlowSessionManager` — session lifecycle + the signed `state` JWT
  (`encodeState` / `verifyEncodedInternalState`, subject = session id). Owns `VerifyEncodedStateResult`.
- `InteractiveFlowSessionOAuth2Manager` — `@Transactional open suspend startOAuth2Session` creates the session + its
  OAuth2 record in one transaction; also fetch / consent / grant / replay checks.
- `InteractiveFlowSessionProviderManager` — `setProvider` upsert, fetch, nonce reconstruction.

**Package split (engine vs consumer):**

- `business.manager.flow` — the **generic engine**: the `InteractiveFlowSession*` managers above +
  `InteractiveFlowSessionCleaner` + the generic `AuthorizationFlowManager`.
- `business.manager.flow.mfa` — **generic flow steps** (MFA is a step of the session, not auth-specific):
  `InteractiveFlowSessionMfaManager`, `InteractiveFlowSession{TotpChallenge,TotpEnrollment}Manager`.
- `business.manager.flow.auth` — the **OAuth2 / web-authorization consumer** of the engine, named
  `InteractiveAuthFlowSession*`: `Manager`, `RedirectUriBuilder`, `PasswordManager`, `OAuth2ProviderManager`,
  `ClaimValidationManager`; its controller helper `InteractiveAuthFlowSessionControllerUtil` lives in
  `api.controller.flow.auth`.

**Model naming** (`business/model/flow/`): `InteractiveFlow` / `InteractiveFlowStatus` are the generic interactive-flow
definition (step URIs) + status — an interactive flow may host authorization, reset-password, or other feature steps, so
they are kept generic. Distinct from the sealed base `AuthorizationFlow` (+ `NonInteractiveAuthorizationFlow`).

**Conventions to preserve:**

- Every table that references a session uses the `session_id` column / `sessionId` field — `authentication_tokens`
  (not a DB FK), `authorization_codes`, `validation_codes`, plus the two attached-record tables.
- A **failed** session never fetches an OAuth2 record — the failed path uses only `id` + `flowId` (→ error page); OAuth2
  is read only on the ongoing/completed path, where the record always exists.
- OAuth2 fields (clientId, scopes, consent, grant, PKCE) and provider fields are **fetched via the managers**, never
  read off the session object.

## Libraries

- **JWT / JWK**: Nimbus JOSE JWT (`com.nimbusds:nimbus-jose-jwt`, transitive via `micronaut-security-jwt`). Use for all
  JWT signing, verification, JWKS serialization, and JWK operations. Do not introduce other JWT libraries (e.g. Auth0
  java-jwt, jose4j, jjwt).
- **Cryptographic primitives**: BouncyCastle (`org.bouncycastle:bcprov-jdk18on`) and standard JCA (`java.security`).
- **Object mapping**: MapStruct (compile-time code generation). New `*Impl` classes must be registered in
  `reflect-config.json` for native image support.
- **Database**: Micronaut Data R2DBC with Flyway migrations.
- **Testing**: JUnit 5 + MockK.

## Database

- **PostgreSQL** (production) and **H2** (development) via R2DBC
- **Flyway migrations** in `server/src/main/resources/databases/postgresql/` and `databases/h2/` (both must be kept in
  sync)
- Migration naming: `V{major}_{minor}_{patch}_{sequence}__{table_name}_{new|edit}.sql`
  - Version reflects the SympAuthy version from `build.gradle.kts` (e.g., `0_5_0` for version `0.5.0`)
  - `_new` suffix: full CREATE TABLE + indexes (one file per table, always reflects the complete current state)
  - `_edit` suffix: ALTER TABLE changes for future incremental modifications
  - Example: `V0_5_0_1__users_new.sql`, `V0_6_0_1__users_edit.sql`

## Configuration

- External config via `MICRONAUT_CONFIG_FILES` env var pointing to a YAML file (typically `config/application.yml`)
- Environment profiles via `MICRONAUT_ENVIRONMENTS` (e.g., `default,admin,mail,discord`)
- `server/src/main/resources/application-default.yml` contains default values; environment-specific files (
  `application-admin.yml`, etc.) overlay them

## Testing

- **JUnit 5 + MockK** with `@ExtendWith(MockKExtension::class)`
- `@MockK` for dependencies, `@InjectMockKs` for auto-wiring the class under test
- `runTest { }` for suspend function tests, `coEvery { }` / `coVerify { }` for suspend mocks
- Tests mirror main package structure in `server/src/test/kotlin/`
- **No redundant `verify(exactly = 1)` for stubbed methods** — when a method is stubbed with `every` / `coEvery` and its
  behavior drives the asserted outcome (e.g. the stub throws the caught exception, or its return value is asserted), do
  not add a `verify(exactly = 1)` / `coVerify(exactly = 1)` for that same call. Reaching the assertion already proves
  the call happened, and the MockK extension's unnecessary-stub check flags an unused stub. Keep `exactly = 0`
  verifications, which assert a method was *not* called — no stub covers that.

## Code Documentation

- **KDoc standard** — Use KDoc for code documentation. Place property documentation above each property, not as
  `@property` tags in the class-level KDoc.

## Documentation

- **Functional documentation**: https://sympauthy.github.io/documentation/functional/
- **Contributing guidelines**: https://sympauthy.github.io/documentation/contributing/
