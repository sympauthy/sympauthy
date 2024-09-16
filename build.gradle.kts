buildscript {
    val kotlinVersion = project.findProperty("kotlinVersion")
    val micronautVersion = project.findProperty("micronautVersion")

    repositories {
        gradlePluginPortal()
    }

    dependencies {
        classpath("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin.kapt:org.jetbrains.kotlin.kapt.gradle.plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin.plugin.allopen:org.jetbrains.kotlin.plugin.allopen.gradle.plugin:$kotlinVersion")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:$kotlinVersion-1.0.21")
        classpath("io.micronaut.gradle:micronaut-gradle-plugin:$micronautVersion")
    }
}

allprojects {
    apply<JavaPlugin>()

    version = "0.1"
    group = "com.sympauthy"

    extra.apply {
        set("kotlinVersion", project.findProperty("kotlinVersion"))
        set("kotlinCoroutinesVersion", "1.8.1")
        set("mapStructVersion", "1.6.2")
        set("javaJwtVersion", "4.4.0")
        set("jsonPathVersion", "2.9.0")
        set("bouncyCastleVersion", "1.78.1")
        set("freemarkerVersion", "2.3.33")

        // Test dependencies
        set("junitJupiterVersion", "5.10.3")
        set("mockkVersion", "1.13.11")
    }

    repositories {
        mavenCentral()
    }
}
