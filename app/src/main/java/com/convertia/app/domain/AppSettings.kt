package com.convertia.app.domain

/** O que fazer quando o arquivo de saída já existe. */
enum class ConflictPolicy { RENAME, REPLACE, SKIP }

/** Ajustes do app. Os padrões são os do mockup. */
data class AppSettings(
    val onboarded: Boolean = false,
    // Geral
    val detectClipboardLinks: Boolean = true,
    val notifications: Boolean = true,
    // Downloads
    val concurrentDownloads: Int = 2,
    val wifiOnly: Boolean = true,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val subtitles: Boolean = false,
    val splitChapters: Boolean = false,
    // Conversão
    val conflictPolicy: ConflictPolicy = ConflictPolicy.RENAME,
    val parallelConversions: Int = 2,
    val hardwareAcceleration: Boolean = true,
    val keepOriginals: Boolean = true,
) {
    /** Opções de "Mais opções" para um pedido de download. */
    val downloadOptions: DownloadOptions
        get() = DownloadOptions(embedThumbnail, embedMetadata, subtitles, splitChapters)

    companion object {
        val concurrentDownloadChoices = listOf(1, 2, 3, 4)
        val parallelConversionChoices = listOf(1, 2, 4)
    }
}
