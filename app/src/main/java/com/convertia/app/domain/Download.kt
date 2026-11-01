package com.convertia.app.domain

/** Vídeo ou só áudio. */
enum class MediaKind { VIDEO, AUDIO }

/** Selo ao lado de um formato na lista. */
enum class FormatBadge { RECOMMENDED, BEST }

/** Uma opção de download listada pelo inspetor (V e A no mockup). */
data class DownloadFormat(
    val id: String,
    val kind: MediaKind,
    /** "1080p", "160 kbps". */
    val quality: String,
    val badge: FormatBadge? = null,
    /** "H.264 + AAC", "Opus". */
    val codec: String,
    /** Vídeo: quadros por segundo ("60"); áudio: taxa de amostragem ("48 kHz"). */
    val detail: String,
    /** Contêiner de origem, minúsculas ("mp4", "webm"). */
    val container: String,
    val sizeMb: Double,
    /** "1920×1080", "320 kbps". */
    val resolution: String,
)

/** Metadados de um link inspecionado. */
data class MediaInfo(
    val url: String,
    val title: String,
    val channel: String,
    /** Domínio sem "www." ("youtube.com"). */
    val site: String,
    /** "18:42". */
    val duration: String,
    /** Licença, se conhecida ("CC BY 4.0"). */
    val license: String?,
    val chapterCount: Int,
    /** Idiomas de legenda ("EN", "DE", "JA"). */
    val subtitleLanguages: List<String>,
    val videoFormats: List<DownloadFormat>,
    val audioFormats: List<DownloadFormat>,
)

/** Contêineres de saída oferecidos em "Salvar como". */
object SaveContainers {
    val video = listOf("mp4", "mkv", "webm")
    val audio = listOf("mp3", "m4a", "opus", "wav")

    fun forKind(kind: MediaKind) = if (kind == MediaKind.VIDEO) video else audio
    fun defaultFor(kind: MediaKind) = forKind(kind).first()
}

/** Pedido de download montado pela tela Baixar ou pelo menu Compartilhar. */
data class DownloadRequest(
    val url: String,
    val title: String,
    val kind: MediaKind,
    val formatId: String,
    /** Contêiner final, minúsculas. */
    val container: String,
    val quality: String,
    val resolution: String,
    val site: String,
    val sizeMb: Double,
    val duration: String,
) {
    /** "1080p · MP4" para vídeo, "MP3 · 320 kbps" para áudio. */
    val formatLabel: String
        get() = if (kind == MediaKind.VIDEO) "$quality · ${container.uppercase()}" else "${container.uppercase()} · $quality"
}

enum class DownloadStatus { DOWNLOADING, QUEUED, PAUSED, FAILED, DONE }

/** Um download na fila. */
data class DownloadJob(
    val id: Long,
    val request: DownloadRequest,
    val status: DownloadStatus,
    /** 0–100. */
    val progress: Double = 0.0,
    /** MB/s; zero quando parado. */
    val speedMbps: Double = 0.0,
    val error: String? = null,
    /** Momento da conclusão, em epoch millis. */
    val finishedAt: Long? = null,
) {
    val isActive: Boolean get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED
}

/** Arquivo salvo na Biblioteca. */
data class LibraryItem(
    val id: String,
    val title: String,
    /** "MP4", "M4A". */
    val format: String,
    /** "1920×1080", "256 kbps". */
    val detail: String,
    val sizeBytes: Long,
    val duration: String,
    val addedAt: Long,
    val kind: MediaKind,
) {
    /** Nome com extensão, para mandar ao Converter. */
    val fileName: String
        get() = if (Regex("\\.\\w{2,4}$").containsMatchIn(title)) title else "$title.${format.lowercase()}"
}

/** Conversão concluída, mostrada em Biblioteca › Histórico. */
data class HistoryEntry(
    val id: String,
    val name: String,
    val fromExt: String,
    val toExt: String,
    val inputBytes: Long,
    val outputBytes: Long,
    val at: Long,
)

/** Link inspecionado recentemente, listado no estado vazio do Baixar. */
data class RecentLink(val url: String, val title: String, val site: String, val at: Long)

/** Domínio de uma URL sem "www.": "https://www.youtube.com/x" → "youtube.com". */
fun siteOf(url: String): String? =
    Regex("//(?:www\\.)?([^/]+)").find(url)?.groupValues?.get(1)

/** Primeira URL http(s) dentro de um texto (o menu Compartilhar costuma mandar título + link). */
fun firstUrlIn(text: String): String? =
    Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(text)?.value?.trimEnd('.', ',', ')', ']', '"', '\'')
