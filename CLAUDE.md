# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SympAuthy is an open-source, self-hosted OAuth2/OpenID Connect authorization server built with Micronaut 4 and Kotlin (coroutines). It supports GraalVM native image compilation.

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
- **`data/`** — R2DBC entities (`model/`), reactive repositories (`repository/`), database-specific repos (`postgresql/`, `h2/`)
- **`config/`** — Configuration properties (`properties/`), sealed config models (`model/`), config factories (`factory/`)
- **`security/`** — Authentication/authorization (token validation, user/state authentication)

### Key Conventions

- **Managers never return entities** — only `business.model` types are exposed to controllers
- **Nullable methods use `OrNull` suffix** — e.g., `findByCodeOrNull()` returns `T?`
- **All async operations use `suspend` functions** — no callbacks or reactive streams in business logic
- **Config sealed class pattern** — `EnabledXxxConfig` / `DisabledXxxConfig` with `orThrow()` extension for required configs, `as? EnabledXxxConfig` for optional feature checks
- **Exception factory methods** — `businessExceptionOf()`, `recoverableBusinessExceptionOf()` (user-retryable), `internalBusinessExceptionOf()` (server errors). Error messages in `error_messages.properties`
- **Repository update methods** — `suspend fun updateXxx(@Id id: UUID, xxx: T)`. Never use `And` in update method names. `delete()` returns `Int` in Micronaut Data 4.x
- **MapStruct mappers** — Compile-time generation. New `*Impl` classes must be registered in `META-INF/native-image/.../reflect-config.json` for native image support

### Scope Type Hierarchy

Scopes use a sealed class hierarchy (`Scope` → `ConsentableUserScope`, `GrantableUserScope`, `ClientScope`). Consentable scopes come from user consent, grantable scopes from rules/auto-grant, client scopes are for `client_credentials` flows only.

## Database

- **PostgreSQL** (production) and **H2** (development) via R2DBC
- **Flyway migrations** in `server/src/main/resources/databases/postgresql/` and `databases/h2/` (both must be kept in sync)
- Migration naming: `V{N}__{description}.sql`

## Configuration

- External config via `MICRONAUT_CONFIG_FILES` env var pointing to a YAML file (typically `config/application.yml`)
- Environment profiles via `MICRONAUT_ENVIRONMENTS` (e.g., `default,admin,mail,discord`)
- `server/src/main/resources/application-default.yml` contains default values; environment-specific files (`application-admin.yml`, etc.) overlay them

## Testing

- **JUnit 5 + MockK** with `@ExtendWith(MockKExtension::class)`
- `@MockK` for dependencies, `@InjectMockKs` for auto-wiring the class under test
- `runTest { }` for suspend function tests, `coEvery { }` / `coVerify { }` for suspend mocks
- Tests mirror main package structure in `server/src/test/kotlin/`

## Documentation

- **Functional documentation**: https://sympauthy.github.io/documentation/functional/
- **Contributing guidelines**: https://sympauthy.github.io/documentation/contributing/
