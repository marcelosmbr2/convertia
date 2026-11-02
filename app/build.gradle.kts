import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

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

/** Versão: no GitHub Actions vem da tag (v0.3.0 → 0.3.0); localmente, o valor padrão. */
val appVersionName: String = System.getenv("CONVERTIA_VERSION_NAME")?.removePrefix("v") ?: "0.3.0"

/** 1.2.3 → 10203: cresce a cada versão, como o Android exige para atualizar por cima. */
val appVersionCode: Int = appVersionName.substringBefore('-').split('.')
    .map { it.toIntOrNull() ?: 0 }
    .let { (it + listOf(0, 0, 0)).take(3) }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

/**
 * LibreOffice embutido: o build baixa os APKs do LibreOffice Viewer publicados pelo F-Droid
 * (versão e SHA-256 em gradle/libreoffice.properties), confere e extrai as bibliotecas
 * nativas e os assets. `-Pconvertia.office=false` desliga (para compilar sem acesso ao
 * f-droid.org); o app então não oferece conversões de documentos.
 */
val officeEnabled: Boolean = providers.gradleProperty("convertia.office").map { it.toBoolean() }.getOrElse(true)
val officeProperties = Properties().apply { rootProject.file("gradle/libreoffice.properties").inputStream().use(::load) }
val officeVersion: String = officeProperties.getProperty("libreoffice.version")
val officeAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

@DisableCachingByDefault(because = "Baixa ~250 MB; não vale guardar no build cache")
abstract class PrepareLibreOffice : DefaultTask() {
    @get:Input abstract val version: Property<String>
    @get:Input abstract val urls: MapProperty<String, String>
    @get:Input abstract val checksums: MapProperty<String, String>
    @get:Internal abstract val downloadCache: DirectoryProperty
    @get:OutputDirectory abstract val jniLibsDir: DirectoryProperty
    @get:OutputDirectory abstract val assetsDir: DirectoryProperty

    @TaskAction
    fun prepare() {
        val jni = jniLibsDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val assets = assetsDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        var assetsDone = false
        for ((abi, url) in urls.get()) {
            val expected = checksums.get().getValue(abi)
            val apk = File(downloadCache.get().asFile, "libreoffice-${version.get()}-$abi.apk")
            if (!apk.isFile || sha256(apk) != expected) {
                download(url, apk)
                val actual = sha256(apk)
                check(actual == expected) { "SHA-256 inesperado para $url: $actual (esperado $expected)" }
            }
            ZipFile(apk).use { zip ->
                for (entry in zip.entries()) {
                    val name = entry.name
                    val target = when {
                        name.startsWith("lib/$abi/") && name.endsWith(".so") -> File(jni, "$abi/${name.substringAfterLast('/')}")
                        !assetsDone && ASSET_DIRS.any { name.startsWith("assets/$it/") } && !entry.isDirectory ->
                            File(assets, name.removePrefix("assets/"))
                        else -> null
                    } ?: continue
                    target.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                }
            }
            assetsDone = true
        }
    }

    private fun download(url: String, target: File) {
        target.parentFile.mkdirs()
        val temp = File(target.path + ".part")
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                logger.lifecycle("LibreOffice: baixando $url" + if (attempt > 0) " (tentativa ${attempt + 1})" else "")
                val connection = URI(url).toURL().openConnection().apply { connectTimeout = 30_000; readTimeout = 120_000 }
                connection.getInputStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
                check(temp.renameTo(target) || (target.delete() && temp.renameTo(target)))
                return
            } catch (e: Exception) {
                lastError = e
                temp.delete()
            }
        }
        throw GradleException("Não foi possível baixar $url", lastError)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val ASSET_DIRS = listOf("program", "share", "unpack")
    }
}

if (officeEnabled) {
    val prepareLibreOffice = tasks.register<PrepareLibreOffice>("prepareLibreOffice") {
        version.set(officeVersion)
        urls.set(officeAbis.associateWith { officeProperties.getProperty("libreoffice.$it.url") })
        checksums.set(officeAbis.associateWith { officeProperties.getProperty("libreoffice.$it.sha256") })
        downloadCache.set(File(gradle.gradleUserHomeDir, "caches/convertia-libreoffice"))
    }
    androidComponents {
        onVariants { variant ->
            variant.sources.jniLibs?.addGeneratedSourceDirectory(prepareLibreOffice, PrepareLibreOffice::jniLibsDir)
            variant.sources.assets?.addGeneratedSourceDirectory(prepareLibreOffice, PrepareLibreOffice::assetsDir)
        }
    }
}

android {
    namespace = "com.convertia.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.convertia.app"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Vazio quando o build não embute o LibreOffice.
        buildConfigField("String", "LIBREOFFICE_VERSION", "\"${if (officeEnabled) officeVersion else ""}\"")
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

// APKs nomeados como "Convertia-0.3.0-arm64-v8a-release.apk".
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

    implementation(libs.androidx.heifwriter)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidsvg)

    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.libarchive)

    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.jsoup)

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
    testImplementation(libs.pdfbox)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.kotlinx.coroutines.bom))
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
