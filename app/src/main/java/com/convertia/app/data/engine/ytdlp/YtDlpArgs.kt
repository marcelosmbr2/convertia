package com.convertia.app.data.engine.ytdlp

import com.convertia.app.domain.DownloadRequest
import com.convertia.app.domain.MediaKind

/** Argumentos de linha de comando do yt-dlp para cada operação do app. */
object YtDlpArgs {

    /** Modelo do nome do arquivo ("Modelo de nome" em Ajustes), limitado a 180 bytes. */
    const val OUTPUT_TEMPLATE = "%(title).180B.%(ext)s"
    private const val CHAPTER_TEMPLATE = "chapter:%(title).120B - %(section_number)02d %(section_title).60B.%(ext)s"

    /** Metadados e formatos, sem baixar. */
    fun inspect(): List<String> = listOf("--dump-single-json", "--no-playlist", "--skip-download", "--no-warnings")

    /** Download de [request] para [outputDir]. */
    fun download(request: DownloadRequest, outputDir: String): List<String> = buildList {
        addAll(listOf("--no-playlist", "--newline", "--no-mtime", "--windows-filenames"))
        addAll(listOf("-P", outputDir, "-o", OUTPUT_TEMPLATE))

        val container = request.container.lowercase()
        if (request.kind == MediaKind.VIDEO) {
            addAll(videoSelection(request, container))
        } else {
            addAll(listOf("-f", request.formatId, "-x", "--audio-format", container))
            if (container == "mp3") addAll(listOf("--audio-quality", "0"))
        }

        val options = request.options
        if (options.embedMetadata) add("--embed-metadata")
        if (options.embedThumbnail && container in THUMBNAIL_CONTAINERS) add("--embed-thumbnail")
        if (options.subtitles && request.kind == MediaKind.VIDEO) {
            addAll(listOf("--write-subs", "--sub-langs", "en.*,en", "--embed-subs"))
        }
        if (options.splitChapters) addAll(listOf("--split-chapters", "-o", CHAPTER_TEMPLATE))
    }

    /**
     * Vídeo: junta a faixa escolhida com o melhor áudio compatível com o contêiner. Só
     * recodifica (lento no celular) quando o codec não cabe no contêiner pedido.
     */
    private fun videoSelection(request: DownloadRequest, container: String): List<String> = buildList {
        val id = request.formatId
        val codec = request.videoCodec
        if (request.progressive) {
            addAll(listOf("-f", id))
            if (container != request.sourceContainer.lowercase()) {
                if (fitsContainer(codec, container)) addAll(listOf("--remux-video", container))
                else addAll(listOf("--recode-video", container))
            }
        } else {
            val audio = when (container) {
                "mp4" -> "bestaudio[ext=m4a]/bestaudio"
                "webm" -> "bestaudio[ext=webm]/bestaudio"
                else -> "bestaudio"
            }
            val selector = audio.split('/').joinToString("/") { "$id+$it" } + "/$id"
            addAll(listOf("-f", selector, "--merge-output-format", container))
            if (!fitsContainer(codec, container)) addAll(listOf("--recode-video", container))
        }
    }

    /** Se o codec de vídeo pode ir para o contêiner sem recodificar. */
    fun fitsContainer(codec: String?, container: String): Boolean = when (container) {
        "mkv" -> true
        "webm" -> codec in setOf("vp9", "vp8", "av01")
        "mp4" -> codec == null || codec in setOf("avc1", "hevc", "av01", "vp9")
        else -> true
    }

    /** Contêineres em que o yt-dlp consegue embutir a miniatura. */
    private val THUMBNAIL_CONTAINERS = setOf("mp4", "mkv", "m4a", "mp3", "opus")
}
