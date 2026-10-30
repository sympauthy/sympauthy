import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.application)
    alias(libs.plugins.kover)
}

dependencies {
    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.rx3)
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.kotlin:micronaut-kotlin-extension-functions") {
        // micronaut-kotlin-extension-functions transitively pulls in micronaut-jackson-databind, which ships a
        // GraalVM JacksonDatabindFeature referencing PropertyNamingStrategy$UpperCamelCaseStrategy — an inner class
        // removed in Jackson 2.21. Since this project uses micronaut-serde-jackson, micronaut-jackson-databind is
        // not needed and must be excluded to allow the native image build to succeed.
        exclude(group = "io.micronaut", module = "micronaut-jackson-databind")
    }

    // Micronaut
    implementation("io.micronaut:micronaut-runtime")
    ksp("io.micronaut:micronaut-inject-java")

    // R2DBC Database
    api("io.micronaut.data:micronaut-data-r2dbc")
    api(libs.jakarta.persistence.api)
    ksp("io.micronaut.data:micronaut-data-processor")

    // Database migration
    api("io.micronaut.flyway:micronaut-flyway")

    // H2: R2DBC + JDBC for migration
    api("io.r2dbc:r2dbc-h2")

    // PostgreSQL: R2DBC + JDBC for migration
    api("org.postgresql:r2dbc-postgresql")
    api("org.flywaydb:flyway-database-postgresql")
    api("org.postgresql:postgresql")

    // HTTP server
    ksp("io.micronaut.jaxrs:micronaut-jaxrs-processor")
    implementation("io.micronaut:micronaut-http-client")

    // HTTP client
    implementation("io.micronaut:micronaut-http-client")

    // Validation
    ksp("io.micronaut:micronaut-http-validation")
    implementation("io.micronaut.validation:micronaut-validation")

    // Security
    ksp("io.micronaut.security:micronaut-security-processor")
    implementation("io.micronaut.security:micronaut-security")
    implementation("io.micronaut.security:micronaut-security-oauth2")
    implementation("io.micronaut.security:micronaut-security-jwt")
    implementation(libs.bouncycastle.bcprov)

    // Reactive programming
    implementation("io.micronaut.rxjava3:micronaut-rxjava3")
    implementation("io.micronaut.rxjava3:micronaut-rxjava3-http-client")

    // Views rendering
    implementation("io.micronaut.views:micronaut-views-freemarker")

    // Mail templating
    implementation("io.micronaut.email:micronaut-email-template")
    runtimeOnly(libs.freemarker)

    // Mail sending (SMTP)
    implementation("io.micronaut.email:micronaut-email-javamail")
    runtimeOnly("org.eclipse.angus:angus-mail")

    // Serialization/Deserialization
    ksp("io.micronaut.serde:micronaut-serde-processor")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    // The type the HTTP server registers a handler for, so that ExceptionHandlerFactory can replace it.
    compileOnly("tools.jackson.core:jackson-core")

    // Logging
    // Compiled against, because the appender in front of the console is the server's own subclass.
    implementation("ch.qos.logback:logback-classic")

    // API documentation
    // Must be above 6.3.0 to fix KSP issue: https://github.com/micronaut-projects/micronaut-openapi/issues/1154
    ksp("io.micronaut.openapi:micronaut-openapi")
    // Must be implementation (not compileOnly) so annotation classes are available at runtime.
    // OpenApiController reads @OpenAPIDefinition via reflection to derive the generated spec filename.
    implementation("io.micronaut.openapi:micronaut-openapi-annotations")

    // YAML: for configuration
    runtimeOnly("org.yaml:snakeyaml")

    // JsonPath: for user info extraction
    implementation(libs.json.path)

    // Health & Liveness endpoints
    implementation("io.micronaut:micronaut-management")

    // Telemetry: traces, metrics and logs from one OpenTelemetry SDK, over one OTLP exporter.
    implementation("io.micronaut.tracing:micronaut-tracing-opentelemetry-http")
    runtimeOnly("io.opentelemetry:opentelemetry-exporter-otlp") {
        // The default sender is OkHttp, a second HTTP stack and a second Kotlin runtime for one POST that
        // java.net.http already makes. The sender is chosen by what is on the classpath, so excluding this one
        // and adding the JDK's is the whole switch.
        exclude(group = "io.opentelemetry", module = "opentelemetry-exporter-sender-okhttp")
    }
    runtimeOnly("io.opentelemetry:opentelemetry-exporter-sender-jdk")
    // Named by logback.xml, which places it beside the console's queue rather than behind it, and detached by
    // the server when telemetry is off.
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0")
    // A query's span, opened from r2dbc-proxy's callbacks by an instrumenter of the server's own, and its
    // statement sanitized by the instrumentation's own analyzer.
    implementation(libs.r2dbc.proxy)
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-incubator")
    implementation("io.opentelemetry.semconv:opentelemetry-semconv")

    // Expression evaluation: for scope granting rules
    implementation(libs.evalex)

    // Testing
    kspTest("io.micronaut:micronaut-inject-java")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    // UtcTimeZoneListener is a launcher service, so the launcher API has to be compiled against.
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mock.webserver)
    testImplementation(kotlin("test"))
    // A repository test runs against a real database of each supported dialect. H2 is embedded; PostgreSQL
    // is a container, which is why the default `test` task requires Docker. See docs/standards/testing-standard.md.
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
}

application {
    mainClass.set("com.sympauthy.Application")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.sympauthy.*")
    }
}

graalvmNative {
    toolchainDetection.set(true)
    metadataRepository {
        enabled = true
    }
    binaries {
        named("main") {
            verbose.set(true)
            // Classpaths for all supported databases must be listed under location and all terminate by the name of the driver: postgresql, h2
            // https://micronaut-projects.github.io/micronaut-flyway/latest/guide/#graalvm
            buildArgs.add("-Dflyway.locations=classpath:databases/postgresql,classpath:databases/h2")
            // Increase per-method compilation timeout from default 300s to 900s.
            // macOS ARM64 CI runners can be slow enough to hit the default limit.
            buildArgs.add("-H:CompilationExpirationPeriod=900")
        }
    }
}

ksp {
    // Configuration for Swagger
    // https://micronaut-projects.github.io/micronaut-openapi/snapshot/guide/#swaggerui
    arg(
        "micronaut.openapi.views.spec",
        "swagger-ui.enabled=true,swagger-ui.theme=material,swagger-ui.spec.url=../openapi.yml,swagger-ui.oauth2.usePkceWithAuthorizationCodeGrant=true"
    )
}

tasks {
    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
        }
    }
    compileTestKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
        }
    }
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(
                PASSED, SKIPPED, FAILED, STANDARD_ERROR, STANDARD_OUT
            )
        }
    }
    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    processResources {
        // Read at configuration time: reaching `project` while the task runs is deprecated.
        val expandedProperties = mapOf("version" to project.version.toString())
        inputs.properties(expandedProperties)
        filesMatching("application.yml") {
            expand(expandedProperties)
        }
    }
}

// --- OpenAPI contract sharing -------------------------------------------------------------------
// micronaut-openapi generates the HTTP contract as part of `kspKotlin`, writing it under
// build/generated/ksp/main/resources/META-INF/swagger/sympauthy-<info.version>.yml (the file name
// tracks the OpenAPI `info.version`, not the project version). Republish it at a stable, versionless
// path (build/openapi/openapi.yml) and expose it as a consumable Gradle artifact so a downstream module
// — the integration-tests OpenAPI client generator — consumes a single source of truth and regenerates
// whenever the contract changes.
val syncOpenApiSpec = tasks.register<Sync>("syncOpenApiSpec") {
    description = "Publishes the generated OpenAPI spec to build/openapi/openapi.yml for downstream consumers."
    group = "openapi"
    dependsOn(tasks.named("kspKotlin"))
    from(layout.buildDirectory.dir("generated/ksp/main/resources/META-INF/swagger")) {
        include("*.yml")
        rename(".+\\.yml", "openapi.yml")
    }
    into(layout.buildDirectory.dir("openapi"))
}

// A consumable-only configuration carrying the published spec, wired to build via syncOpenApiSpec. The
// integration-tests module depends on `project(":server", configuration = "openApiSpec")` to obtain the
// contract with a proper task dependency (no reaching into another project's build directory by path).
val openApiSpec: Configuration = configurations.create("openApiSpec") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(openApiSpec.name, layout.buildDirectory.file("openapi/openapi.yml")) {
        builtBy(syncOpenApiSpec)
    }
}
