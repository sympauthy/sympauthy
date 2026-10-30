import java.io.OutputStream

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.micronaut.application) apply false
    alias(libs.plugins.micronaut.minimal.library) apply false
    alias(libs.plugins.micronaut.openapi) apply false
}

allprojects {
    apply<JavaPlugin>()

    version = "0.5.0"
    group = "com.sympauthy"

    repositories {
        mavenCentral()
    }
}

// --- Static analysis ----------------------------------------------------------------------------

// The analysis belongs to the root project because what it reads is every module's sources: the
// server's, and the integration tests' with the helpers they share. One rule set holds them all, and
// one report names a finding in either.
//
// detekt runs as a forked JavaExec against its CLI rather than through its Gradle plugin. The plugin
// analyses inside the Gradle daemon's own JVM, and detekt 1.23.8 refuses this build's JDK: it first
// derives --jvm-target=25, which its embedded Kotlin 2.0.21 does not accept (the ceiling is 22), and
// then — with the target pinned lower — fails in detekt-core's EnvironmentFacade reading the running
// JVM's version string. Its Detekt task exposes no javaLauncher, so the analysis cannot be pointed at
// an older JDK. The CLI defaults its target instead of interrogating the JDK, so a forked process
// analyses the same sources against the same rule set without any of that.
//
// Only the hand-written sources are analysed. The KSP output and the client generated from the
// OpenAPI specification, both under build/generated, are deliberately out of scope: no rule here is
// actionable against generated code. Reading sources compiles nothing, so the integration tests are
// analysed without being built or run.
val detektCli = configurations.create("detektCli")

// The formatting plugin carries ktlint's rules, of which detekt.yml turns on only the import order.
dependencies {
    detektCli(libs.detekt.cli)
    detektCli(libs.detekt.formatting)
}

val detektSources = listOf(
    "server/src/main/kotlin",
    "server/src/test/kotlin",
    "integration-tests/src/main/kotlin",
    "integration-tests/src/integrationTest/kotlin"
)

// What the analysis and its correcting twin both run: the rule set in detekt.yml over the same
// sources, with any further configuration layered over it.
fun JavaExec.runDetekt(vararg extraArgs: String, layeredConfigs: List<File> = emptyList()) {
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args(
        "--config", (listOf(file("detekt.yml")) + layeredConfigs)
            .joinToString(",") { it.absolutePath },
        "--build-upon-default-config",
        "--input", detektSources.joinToString(",") { file(it).absolutePath },
        // Without this the reports name every finding by absolute path, which on a CI runner is a
        // checkout directory repeated ahead of each one.
        "--base-path", projectDir.absolutePath,
        *extraArgs
    )
}

// Two renderings of the one analysis. The HTML report is what a developer opens locally; the
// Markdown one is what CI appends to the run summary, which renders Markdown and would show an HTML
// document as its own source.
val detektHtmlReport = layout.buildDirectory.file("reports/detekt/detekt.html")
val detektMarkdownReport = layout.buildDirectory.file("reports/detekt/detekt.md")

tasks.register<JavaExec>("detekt") {
    description = "Analyses the Kotlin sources against the rule set in detekt.yml."
    group = "verification"
    runDetekt(
        "--report", "html:${detektHtmlReport.get().asFile}",
        "--report", "md:${detektMarkdownReport.get().asFile}"
    )
    // Declared so the analysis is skipped when neither the sources nor the rule set have changed.
    detektSources.forEach { inputs.dir(it) }
    inputs.file(file("detekt.yml"))
    outputs.file(detektHtmlReport)
    outputs.file(detektMarkdownReport)
}

// Rewrites the sources to clear what the analysis can correct on its own, which today is the import
// order. What it writes is the sources it reads, so it is never up to date: it runs every time it is
// asked.
//
// detekt writes no correction from a run that fails over its findings, so one finding it cannot
// correct would keep every file it can correct as it was. The correcting run is told never to fail
// over a finding, by one setting layered over detekt.yml, and its own listing is dropped, since it
// still names what it has just corrected. The analysis then runs again and gives the verdict on what
// is left.
val detektNeverFailConfig = layout.buildDirectory.file("detekt/never-fail.yml")

tasks.register<JavaExec>("detektAutoCorrect") {
    description = "Corrects in place what the rule set in detekt.yml can correct, then analyses the rest."
    group = "verification"
    runDetekt("--auto-correct", layeredConfigs = listOf(detektNeverFailConfig.get().asFile))
    standardOutput = OutputStream.nullOutputStream()
    outputs.upToDateWhen { false }
    doFirst {
        detektNeverFailConfig.get().asFile.apply {
            parentFile.mkdirs()
            writeText("build:\n  maxIssues: -1\n")
        }
    }
    finalizedBy("detekt")
}

tasks.named("check") {
    dependsOn("detekt")
}
