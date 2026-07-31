import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// End-to-end integration tests: boot the SympAuthy native image (as a Docker container, via the
// `testcontainers-sympauthy` library) and exercise it over real HTTP against each supported database
// (H2 and PostgreSQL). These tests require Docker and a published/built SympAuthy image, so they live
// in a dedicated `integrationTest` source set and run only via `./gradlew :integration-tests:integrationTest`
// — never as part of the default `build`/`check`/`test` lifecycle.
//
// The tests call the server through a typed HTTP client generated from the server's OpenAPI contract:
// the Micronaut OpenAPI plugin turns `server`'s published spec into declarative Micronaut @Client
// interfaces + Serde models (see the `micronaut { openapi { client(...) } }` block). This is why the
// module applies the Micronaut plugins and pulls in an HTTP-client runtime — unlike a plain black-box
// tester, it hosts a Micronaut ApplicationContext to drive the generated client against the container.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.minimal.library)
    alias(libs.plugins.micronaut.openapi)
}

repositories {
    mavenCentral()
    // testcontainers-sympauthy is published to GitHub Packages. Even for public packages, GitHub
    // requires an authenticated token with `read:packages`. Locally, export GITHUB_ACTOR + GITHUB_TOKEN
    // (a PAT, e.g. `GITHUB_TOKEN=$(gh auth token)`); in CI the workflow's GITHUB_TOKEN is used.
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/sympauthy/testcontainers-sympauthy")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: (findProperty("gpr.user") as String?)
            password = System.getenv("GITHUB_TOKEN") ?: (findProperty("gpr.token") as String?)
        }
    }
}

// Container-starting tests live in their own source set so they never run under the default `test`
// task (and therefore never under `check`/`build`), keeping Docker off the regular build path.
val integrationTest: SourceSet = sourceSets.create("integrationTest")

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// The Micronaut OpenAPI plugin emits the generated client into the (otherwise empty) `main` source set.
// Expose main's compiled output — the @Client beans and models — to the integrationTest source set so the
// scenarios can call the generated client.
integrationTest.compileClasspath += sourceSets["main"].output
integrationTest.runtimeClasspath += sourceSets["main"].output

dependencies {
    // Micronaut Platform BOM governs testcontainers, nimbus-jose-jwt and slf4j so they match the server.
    testImplementation(platform(libs.micronaut.platform))
    testImplementation(platform(libs.junit.bom))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.sympauthy)
    // Parse the OIDC discovery document and verify id_token signatures against the server's JWKS.
    testImplementation(libs.nimbus.jose.jwt)

    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.slf4j.simple)

    // Runtime for the generated OpenAPI client (compiled into `main`): a declarative Micronaut @Client with
    // plain Jackson models (see serializationFramework=JACKSON above). The tests instantiate a Micronaut
    // ApplicationContext to obtain and drive these beans. micronaut-serde is deliberately not used — its
    // Kotlin KSP introspection generator mis-generates the models' BeanIntrospection (ClassFormatError).
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut:micronaut-jackson-databind")
}

micronaut {
    openapi {
        // Generate a typed Kotlin client from the server's published OpenAPI contract — one source of
        // truth for the request/response models and the endpoint paths. Live-wired below so the client is
        // regenerated whenever the server's contract changes.
        client(file("${rootProject.projectDir}/server/build/openapi/openapi.yml")) {
            apiPackageName = "com.sympauthy.api.client.api"
            modelPackageName = "com.sympauthy.api.client.model"
            lang.set("kotlin")
            clientId = "sympauthy"
            // Generate plain Jackson models instead of @Serdeable ones. The Micronaut Serde Kotlin (KSP)
            // introspection generator mis-generates these data classes' BeanIntrospection — a duplicate
            // "$property$keys$metadata" method that fails to load (ClassFormatError) when the client's
            // ApplicationContext starts. Jackson-databind (de)serialization needs no compile-time
            // introspection, so it sidesteps that generator bug while keeping a real Micronaut @Client.
            serializationFramework.set("JACKSON")
            // The models are deserialization targets only — no bean validation needed (also keeps
            // jakarta.validation off the classpath).
            useBeanValidation = false
        }
    }
}

// Live-wire the generated client to the server's contract: regeneration depends on the server
// republishing its spec, so a wire-format change forces the client to regenerate and fails compilation
// on drift instead of silently going stale.
tasks.matching { it.name.startsWith("generateClientOpenApi") }.configureEach {
    dependsOn(":server:syncOpenApiSpec")
}

// Post-process the generated model sources for two things the generator's own configuration does not
// cover, both needed to (de)serialize the server's responses on the JVM via Jackson-databind. This runs as
// a doLast on the generator task itself, so the rewritten files become that task's recorded output — which
// keeps Gradle's incremental tracking correct (downstream compilation recompiles when they change) without
// touching the source-set wiring.
//
//  1. Remove @Introspected. The models (de)serialize through Jackson-databind (serializationFramework =
//     JACKSON above), which needs no Micronaut BeanIntrospection — but the generator stamps @Introspected
//     on every model regardless. One model, SignUpInputResource, extends HashMap because its schema
//     declares additionalProperties: true (the server's @JsonAnySetter claims map), and Micronaut's Kotlin
//     KSP introspection generator mis-generates the BeanIntrospection for that @Introspected Map subclass —
//     a duplicate "$property$keys$metadata" method that fails to load (ClassFormatError) the moment the
//     client's ApplicationContext starts. Dropping the unused annotation stops any introspection being
//     generated for the models, sidestepping the bug while leaving Jackson (de)serialization intact.
//
//  2. Retype ZonedDateTime -> LocalDateTime. The generator hardcodes the date-time format to ZonedDateTime
//     (no configuration hook), but the server serializes its LocalDateTime timestamps without a zone or
//     offset (e.g. "2026-07-31T13:16:29.061704"), which Jackson cannot parse into a ZonedDateTime. The
//     server uses LocalDateTime throughout, so retyping the generated fields matches the wire format.
//
// Both rewrites are idempotent, so re-running on an already-processed tree is a no-op.
tasks.matching { it.name == "generateClientOpenApiModels" }.configureEach {
    val modelsDir = layout.buildDirectory.dir("generated/openapi/generateClientOpenApiModels/src/main/kotlin")
    doLast {
        modelsDir.get().asFile.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val original = file.readText()
            val rewritten = original
                .lineSequence()
                .filterNot {
                    it.trim() == "@Introspected" ||
                        it.trim() == "import io.micronaut.core.annotation.Introspected"
                }
                .joinToString("\n")
                .replace("ZonedDateTime", "LocalDateTime")
            if (rewritten != original) file.writeText(rewritten.trimEnd('\n') + "\n")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
}

tasks {
    compileKotlin {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
    }
    compileTestKotlin {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
    }
    named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileIntegrationTestKotlin") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
    }
}

// The integration-test task. Requires Docker. Point it at a specific image with
// `-Dsympauthy.image=<ref>` (default: the published nightly image — see SympauthyImage).
tasks.register<Test>("integrationTest") {
    description = "Runs end-to-end integration tests that start the SympAuthy container (requires Docker)."
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    // Forward the image override to the test JVM so `-Dsympauthy.image=…` on the Gradle command line works.
    systemProperty("sympauthy.image", System.getProperty("sympauthy.image", ""))
    shouldRunAfter("test")
    testLogging {
        // Container logs are dumped explicitly on failure (see AbstractSympauthyIT), so keep the
        // default test output focused on outcomes rather than streaming every container log line.
        events(PASSED, SKIPPED, FAILED)
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
