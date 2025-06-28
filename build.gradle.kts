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
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:$kotlinVersion-1.0.27")
        classpath("io.micronaut.gradle:micronaut-gradle-plugin:$micronautPluginVersion")
    }
}

allprojects {
    apply<JavaPlugin>()

    version = "0.1"
    group = "com.sympauthy"

    extra.apply {
        set("kotlinVersion", project.findProperty("kotlinVersion"))
        set("kotlinCoroutinesVersion", "1.9.0")
        set("mapStructVersion", "1.6.3")
        set("javaJwtVersion", "4.4.0")
        set("jsonPathVersion", "2.9.0")
        set("bouncyCastleVersion", "1.79")
        set("freemarkerVersion", "2.3.33")

        // Test dependencies
        set("junitJupiterVersion", "5.13.2")
        set("mockkVersion", "1.14.4")
    }

    repositories {
        mavenCentral()
    }
}
