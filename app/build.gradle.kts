plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Assinatura do release vinda de variáveis de ambiente (no GitHub Actions, das secrets do
 * repositório). Sem elas, o release sai sem assinatura — útil para testar o build local.
 */
val releaseKeystore: String? = System.getenv("CONVERTIA_KEYSTORE_FILE")

/** Versão: no GitHub Actions vem da tag (v0.2.0 → 0.2.0); localmente, o valor padrão. */
val appVersionName: String = System.getenv("CONVERTIA_VERSION_NAME")?.removePrefix("v") ?: "0.2.0"

/** 1.2.3 → 10203: cresce a cada versão, como o Android exige para atualizar por cima. */
val appVersionCode: Int = appVersionName.substringBefore('-').split('.')
    .map { it.toIntOrNull() ?: 0 }
    .let { (it + listOf(0, 0, 0)).take(3) }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

android {
    namespace = "com.convertia.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.convertia.app"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("CONVERTIA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CONVERTIA_KEY_ALIAS")
                keyPassword = System.getenv("CONVERTIA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // O yt-dlp roda o Python e o ffmpeg como processos a partir da pasta de bibliotecas
            // nativas, então elas precisam ser extraídas na instalação.
            useLegacyPackaging = true
        }
    }

    splits {
        abi {
            // Um APK por arquitetura: o Python e o ffmpeg pesam ~50 MB cada uma.
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}

// APKs nomeados como "Convertia-0.2.0-arm64-v8a-release.apk".
base {
    archivesName = "Convertia-$appVersionName"
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.youtubedl.android)
    implementation(libs.youtubedl.android.ffmpeg)

    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(platform(libs.kotlinx.coroutines.bom))
    testImplementation(libs.kotlinx.coroutines.test)
}
