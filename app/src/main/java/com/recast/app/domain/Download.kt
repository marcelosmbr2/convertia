package com.recast.app.domain

import kotlinx.serialization.Serializable

/** Vídeo ou só áudio. */
@Serializable
enum class MediaKind { VIDEO, AUDIO }

/** Selo ao lado de um formato na lista. */
enum class FormatBadge { RECOMMENDED, BEST }

/** Uma opção de download listada pelo inspetor. */
data class DownloadFormat(
    /** Identificador do formato no yt-dlp ("299", "hls-720p"). */
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
    /** Tamanho estimado do download completo (vídeo + áudio); zero se desconhecido. */
    val sizeMb: Double,
    /** "1920×1080", "320 kbps". */
    val resolution: String,
    /** Já traz áudio junto (não precisa juntar com outra faixa). */
    val progressive: Boolean = false,
    /** Família do codec de vídeo ("avc1", "vp9", "av01"…), para decidir se precisa recodificar. */
    val videoCodec: String? = null,
    /** Tamanho de cada faixa baixada, em ordem (vídeo, áudio), para somar o progresso. */
    val streamSizesMb: List<Double> = emptyList(),
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
    /** Licença, se conhecida ("Creative Commons"). */
    val license: String?,
    val chapterCount: Int,
    /** Idiomas de legenda ("EN", "DE", "JA"). */
    val subtitleLanguages: List<String>,
    val videoFormats: List<DownloadFormat>,
    val audioFormats: List<DownloadFormat>,
    val thumbnailUrl: String? = null,
)

/** Contêineres de saída oferecidos em "Salvar como". */
object SaveContainers {
    val video = listOf("mp4", "mkv", "webm")
    val audio = listOf("mp3", "m4a", "opus", "wav")

    fun forKind(kind: MediaKind) = if (kind == MediaKind.VIDEO) video else audio
    fun defaultFor(kind: MediaKind) = forKind(kind).first()
}

/** Opções de "Mais opções" no momento do pedido. */
@Serializable
data class DownloadOptions(
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val subtitles: Boolean = false,
    val splitChapters: Boolean = false,
)

/** Pedido de download montado pela tela Baixar ou pelo menu Compartilhar. */
@Serializable
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
    val progressive: Boolean = false,
    val videoCodec: String? = null,
    /** Contêiner do formato de origem ("mp4", "webm"). */
    val sourceContainer: String = "",
    val streamSizesMb: List<Double> = emptyList(),
    val options: DownloadOptions = DownloadOptions(),
) {
    /** "1080p · MP4" para vídeo, "MP3 · 320 kbps" para áudio. */
    val formatLabel: String
        get() = if (kind == MediaKind.VIDEO) "$quality · ${container.uppercase()}" else "${container.uppercase()} · $quality"
}

@Serializable
enum class DownloadStatus { DOWNLOADING, QUEUED, PAUSED, FAILED, DONE }

/** Um download na fila. */
@Serializable
data class DownloadJob(
    val id: Long,
    val request: DownloadRequest,
    val status: DownloadStatus,
    /** 0–100. */
    val progress: Double = 0.0,
    /** MB/s; zero quando parado. */
    val speedMbps: Double = 0.0,
    /** Tamanho total informado pelo motor durante o download, se diferente da estimativa. */
    val totalMb: Double? = null,
    val error: String? = null,
    /** Momento da conclusão, em epoch millis. */
    val finishedAt: Long? = null,
    /** Item criado na Biblioteca ao concluir. */
    val libraryItemId: String? = null,
) {
    val isActive: Boolean get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED

    /** Tamanho para exibir: o do motor, se já informado; senão a estimativa do formato. */
    val sizeMb: Double get() = totalMb ?: request.sizeMb
}

/** Arquivo gravado no aparelho por um download. */
data class SavedFile(
    /** `content://` do arquivo; nulo no motor simulado. */
    val uri: String?,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
)

/** Arquivo salvo na Biblioteca. */
@Serializable
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
    /** `content://` do arquivo; nulo em dados de exemplo. */
    val uri: String? = null,
    val mimeType: String? = null,
) {
    /** Nome com extensão, para mandar ao Converter. */
    val fileName: String
        get() = if (Regex("\\.\\w{2,4}$").containsMatchIn(title)) title else "$title.${format.lowercase()}"
}

/** Conversão concluída, mostrada em Biblioteca › Histórico. */
@Serializable
data class HistoryEntry(
    val id: String,
    val name: String,
    val fromExt: String,
    val toExt: String,
    val inputBytes: Long,
    val outputBytes: Long,
    val at: Long,
    /** `content://` do arquivo gerado (do primeiro, se forem vários); nulo em dados antigos. */
    val uri: String? = null,
    val mimeType: String? = null,
    val fileCount: Int = 1,
)

/** Link inspecionado recentemente, listado no estado vazio do Baixar. */
@Serializable
data class RecentLink(val url: String, val title: String, val site: String, val at: Long)

/** Monta o pedido para baixar [format] de [media] no [container] escolhido. */
fun MediaInfo.requestFor(format: DownloadFormat, container: String, options: DownloadOptions) = DownloadRequest(
    url = url,
    title = title,
    kind = format.kind,
    formatId = format.id,
    container = container,
    quality = format.quality,
    resolution = format.resolution,
    site = siteOf(url) ?: site,
    sizeMb = format.sizeMb,
    duration = duration,
    progressive = format.progressive,
    videoCodec = format.videoCodec,
    sourceContainer = format.container,
    streamSizesMb = format.streamSizesMb,
    options = options,
)

/** Domínio de uma URL sem "www.": "https://www.youtube.com/x" → "youtube.com". */
fun siteOf(url: String): String? =
    Regex("//(?:www\\.)?([^/]+)").find(url)?.groupValues?.get(1)

/** Primeira URL http(s) dentro de um texto (o menu Compartilhar costuma mandar título + link). */
fun firstUrlIn(text: String): String? =
    Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(text)?.value?.trimEnd('.', ',', ')', ']', '"', '\'')

/** Duração em segundos como "m:ss" ou "h:mm:ss". */
fun formatDuration(seconds: Double): String {
    val total = Formatters.roundHalfUp(seconds).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}
