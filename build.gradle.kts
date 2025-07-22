buildscript {
    val kotlinVersion = project.findProperty("kotlinVersion")
    val micronautPluginVersion = project.findProperty("micronautPluginVersion")

    repositories {
        gradlePluginPortal()
    }

    dependencies {
        classpath("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin.kapt:org.jetbrains.kotlin.kapt.gradle.plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin.plugin.allopen:org.jetbrains.kotlin.plugin.allopen.gradle.plugin:$kotlinVersion")
        // Unable to move to KSP 2 as of 2.0.2
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:$kotlinVersion-1.0.32")
        classpath("io.micronaut.gradle:micronaut-gradle-plugin:$micronautPluginVersion")
    }
}

allprojects {
    apply<JavaPlugin>()

    version = "0.1"
    group = "com.sympauthy"

    extra.apply {
        set("kotlinVersion", project.findProperty("kotlinVersion"))
        set("kotlinCoroutinesVersion", "1.10.2")
        set("mapStructVersion", "1.6.3")
        set("javaJwtVersion", "4.5.0")
        set("jsonPathVersion", "2.9.0")
        set("bouncyCastleVersion", "1.81")
        set("freemarkerVersion", "2.3.34")

        // Test dependencies
        set("junitJupiterVersion", "5.13.4")
        set("mockkVersion", "1.14.5")
    }

    repositories {
        mavenCentral()
    }
}
