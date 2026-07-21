import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.application)
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
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mock.webserver)
    testImplementation(kotlin("test"))
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
