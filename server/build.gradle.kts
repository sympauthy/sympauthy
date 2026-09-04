import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
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

    // Object mapping
    api(libs.mapstruct)
    kapt(libs.mapstruct.processor)

    // Serialization/Deserialization
    ksp("io.micronaut.serde:micronaut-serde-processor")
    implementation("io.micronaut.serde:micronaut-serde-jackson")

    // Logging
    runtimeOnly("ch.qos.logback:logback-classic")

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
    // is a container, which is why the default `test` task requires Docker. See docs/testing-standard.md.
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

kapt {
    arguments {
        // Configuration for Swagger
        // https://micronaut-projects.github.io/micronaut-openapi/snapshot/guide/#swaggerui
        arg(
            "micronaut.openapi.views.spec",
            "swagger-ui.enabled=true,swagger-ui.theme=material,swagger-ui.spec.url=openapi.yml,swagger-ui.oauth2.usePkceWithAuthorizationCodeGrant=true"
        )
    }
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
        filesMatching("application.yml") {
            expand(project.properties)
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
val syncOpenApiSpec by tasks.registering(Sync::class) {
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
val openApiSpec: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(openApiSpec.name, layout.buildDirectory.file("openapi/openapi.yml")) {
        builtBy(syncOpenApiSpec)
    }
}

// --- Static analysis ----------------------------------------------------------------------------
// detekt runs as a forked JavaExec against its CLI rather than through its Gradle plugin. The plugin
// analyses inside the Gradle daemon's own JVM, and detekt 1.23.8 refuses this build's JDK: it first
// derives --jvm-target=25, which its embedded Kotlin 2.0.21 does not accept (the ceiling is 22), and
// then — with the target pinned lower — fails in detekt-core's EnvironmentFacade reading the running
// JVM's version string. Its Detekt task exposes no javaLauncher, so the analysis cannot be pointed at
// an older JDK. The CLI defaults its target instead of interrogating the JDK, so a forked process
// analyses the same sources against the same rule set without any of that.
//
// Only the hand-written sources are analysed. The KSP and kapt output under build/generated is
// deliberately out of scope: no rule here is actionable against generated code.
val detektCli = configurations.create("detektCli")

dependencies {
    detektCli(libs.detekt.cli)
}

val detektReport = layout.buildDirectory.file("reports/detekt/detekt.html")

tasks.register<JavaExec>("detekt") {
    description = "Analyses the Kotlin sources against the rule set in detekt.yml."
    group = "verification"
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args(
        "--config", rootProject.file("detekt.yml").absolutePath,
        "--build-upon-default-config",
        "--input", listOf("src/main/kotlin", "src/test/kotlin")
            .joinToString(",") { file(it).absolutePath },
        "--report", "html:${detektReport.get().asFile}"
    )
    // Declared so the analysis is skipped when neither the sources nor the rule set have changed.
    inputs.dir("src/main/kotlin")
    inputs.dir("src/test/kotlin")
    inputs.file(rootProject.file("detekt.yml"))
    outputs.file(detektReport)
}

tasks.named("check") {
    dependsOn("detekt")
}
