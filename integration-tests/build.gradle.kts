import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// End-to-end integration tests: boot the SympAuthy native image (as a Docker container, via the
// `testcontainers-sympauthy` library) and exercise it over real HTTP against each supported database
// (H2 and PostgreSQL). These tests require Docker and a published/built SympAuthy image, so they live
// in a dedicated `integrationTest` source set and run only via `./gradlew :integration-tests:integrationTest`
// — never as part of the default `build`/`check`/`test` lifecycle.

plugins {
    alias(libs.plugins.kotlin.jvm)
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

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(platform(libs.testcontainers.bom))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.sympauthy)
    // Parse the OIDC discovery document and verify id_token signatures against the server's JWKS.
    testImplementation(libs.nimbus.jose.jwt)

    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.slf4j.simple)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
}

tasks {
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
