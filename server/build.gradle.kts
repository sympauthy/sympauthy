import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("com.google.devtools.ksp")
    id("io.micronaut.application")
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect:${project.extra["kotlinVersion"]}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${project.extra["kotlinVersion"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${project.extra["kotlinCoroutinesVersion"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx3:${project.extra["kotlinCoroutinesVersion"]}")
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
    api("jakarta.persistence:jakarta.persistence-api:3.2.0")
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
    ksp("io.micronaut.security:micronaut-security-annotations")
    implementation("io.micronaut.security:micronaut-security")
    implementation("io.micronaut.security:micronaut-security-oauth2")
    implementation("io.micronaut.security:micronaut-security-jwt")
    implementation("com.auth0:java-jwt:${project.extra["javaJwtVersion"]}")
    implementation("org.bouncycastle:bcprov-jdk18on:${project.extra["bouncyCastleVersion"]}")

    // Reactive programming
    implementation("io.micronaut.rxjava3:micronaut-rxjava3")
    implementation("io.micronaut.rxjava3:micronaut-rxjava3-http-client")

    // Views rendering
    implementation("io.micronaut.views:micronaut-views-freemarker")

    // Mail templating
    implementation("io.micronaut.email:micronaut-email-template")
    runtimeOnly("org.freemarker:freemarker:${project.extra["freemarkerVersion"]}")

    // Mail sending (SMTP)
    implementation("io.micronaut.email:micronaut-email-javamail")
    runtimeOnly("org.eclipse.angus:angus-mail")

    // Object mapping
    api("org.mapstruct:mapstruct:${project.extra["mapStructVersion"]}")
    kapt("org.mapstruct:mapstruct-processor:${project.extra["mapStructVersion"]}")

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
    implementation("com.jayway.jsonpath:json-path:${project.extra["jsonPathVersion"]}")

    // Health & Liveness endpoints
    implementation("io.micronaut:micronaut-management")

    // Expression evaluation: for scope granting rules
    implementation("com.ezylang:EvalEx:${project.extra["evalExVersion"]}")

    // Testing
    kspTest("io.micronaut:micronaut-inject-java")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:${project.extra["junitJupiterVersion"]}")
    testImplementation("io.mockk:mockk:${project.extra["mockkVersion"]}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${project.extra["kotlinCoroutinesVersion"]}")
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
