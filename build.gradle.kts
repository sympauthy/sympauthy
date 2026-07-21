plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.micronaut.application) apply false
}

allprojects {
    apply<JavaPlugin>()

    version = "0.5.0"
    group = "com.sympauthy"

    repositories {
        mavenCentral()
    }
}
