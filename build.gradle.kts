buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // O AGP 9 já inclui o Kotlin (built-in Kotlin), mas depende de uma versão mais
        // antiga do KGP. Este é o jeito documentado de subir a versão:
        // https://developer.android.com/build/releases/agp-9-0-0-release-notes
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
