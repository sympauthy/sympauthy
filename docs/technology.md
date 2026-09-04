# Technology

What this server is built on, and why each piece was picked. The rules that follow from these
choices live in [the code standards](general-code-standard.md); this is the list and the reasoning.

- **[Micronaut](https://micronaut.io)** — the application framework. Dependency injection and
  configuration are resolved at compile time rather than by scanning at startup, which is what makes
  a native image both feasible and small: there is little left to reflect over.
- **Kotlin [coroutines](https://kotlinlang.org/docs/coroutines-overview.html)** — the concurrency
  model throughout. Controllers, managers and repositories are all `suspend`, so no request occupies
  a thread while waiting on a database or on a third-party provider.
- **[Micronaut Data
  R2DBC](https://micronaut-projects.github.io/micronaut-data/latest/guide/#r2dbc)** over
  **PostgreSQL** and **H2** — reactive, non-blocking data access, which is what lets a repository
  method be `suspend` rather than a blocking call wrapped in a dispatcher. Two databases because a
  deployment should be able to try this server with no database to install; [what that
  costs](database-standard.md#one-schema-spelled-per-dialect) is paid in the schema.
- **[Flyway](https://documentation.red-gate.com/flyway)** — schema migration, run at startup against
  whichever dialect is configured.
- **[GraalVM native image](https://www.graalvm.org/reference-manual/native-image/)** — the
  production artifact is compiled ahead of time, for a startup measured in milliseconds and a memory
  footprint a small deployment can afford. A JVM run is what development uses, and the difference
  between the two is a standard of its own: [native image](native-image-standard.md).
- **[MapStruct](https://mapstruct.org)** — mapping between the layers' models, generated at compile
  time. Generated rather than reflective so that a mapping is a method a debugger can step into, and
  so that a field nothing maps to fails the build.
- **[Nimbus JOSE + JWT](https://connect2id.com/products/nimbus-jose-jwt)** — every JWT and JWK
  operation: signing, verification, key-set serialization. **This is the only JWT library, and a
  second one is not introduced.** Two libraries would mean two answers to which algorithms are
  acceptable and two places a signature is verified, and the one an attacker cares about is
  whichever is more permissive.
- **[Bouncy Castle](https://www.bouncycastle.org)** and the JDK's own cryptography — the primitives
  underneath: hashing, key generation, the algorithms the configuration allows.
- **[EvalEx](https://github.com/ezylang/EvalEx)** — the expression language a deployment writes
  scope granting and act-as rules in. An expression evaluator rather than a plugin interface because
  these rules are configuration, and configuration should not need a build.
- **[FreeMarker](https://freemarker.apache.org)** — mail templates. Their structure only; every
  string in them comes from [a message bundle](i18n-standard.md).
- **JUnit 5 and [MockK](https://mockk.io)** — unit testing, with
  [Testcontainers](https://testcontainers.com) driving the container-based [integration
  tests](testing-standard.md).

**Two annotation processors run over this source, and that is deliberate rather than accidental.**
Most processing is done by the newer one; one generator still requires the older. The cost shows up
in the [API standard](api-standard.md#openapi): both produce an OpenAPI document, and the two do not
agree.

**The build pins its own tooling for reasons that are written down where they are pinned.** A build
tool version this project cannot move past, a dependency excluded to avoid a clash: each carries its
reason as a comment beside the constraint, because a version range with no explanation is one
somebody eventually widens.

---

← [Design documentation](index.md)
