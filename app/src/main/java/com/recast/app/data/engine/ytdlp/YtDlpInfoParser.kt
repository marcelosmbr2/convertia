package com.recast.app.data.engine.ytdlp

import com.recast.app.domain.DownloadFormat
import com.recast.app.domain.FormatBadge
import com.recast.app.domain.Formatters
import com.recast.app.domain.MediaInfo
import com.recast.app.domain.MediaKind
import com.recast.app.domain.formatDuration
import com.recast.app.domain.siteOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Converte a saída de `yt-dlp --dump-single-json` na lista de formatos da tela Baixar:
 * uma opção de vídeo por altura (com o codec mais compatível) e até quatro de áudio.
 */
object YtDlpInfoParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Formatos de vídeo abaixo disso só aparecem se não houver o suficiente acima. */
    private const val MIN_HEIGHT = 360
    private const val MIN_VISIBLE_VIDEO = 3
    private const val MAX_AUDIO = 4

    fun parse(text: String, requestedUrl: String): MediaInfo {
        val root = json.parseToJsonElement(text).jsonObject
        val duration = root.double("duration")
        val raw = (root["formats"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.toRaw(duration) }.orEmpty()
            .ifEmpty { listOfNotNull(root.toRaw(duration)) }
            .filter { it.usable }

        val audio = raw.filter { it.isAudioOnly }
        val bestM4a = audio.filter { it.audioFamily == "mp4a" }.maxByOrNull { it.bitrate }
        val bestOpus = audio.filter { it.audioFamily == "opus" }.maxByOrNull { it.bitrate }
        val bestAny = audio.maxByOrNull { it.bitrate }

        val videos = pickVideos(raw.filter { it.hasVideo }).map { video ->
            val pair = when {
                video.hasAudio -> null
                video.ext == "webm" -> bestOpus ?: bestAny
                else -> bestM4a ?: bestAny
            }
            video.toDownloadFormat(pair)
        }
        val audioFormats = pickAudio(audio).map { it.toAudioFormat() }

        val site = root.string("webpage_url_domain") ?: siteOf(requestedUrl) ?: ""
        return MediaInfo(
            url = root.string("webpage_url") ?: requestedUrl,
            title = root.string("title") ?: root.string("fulltitle") ?: requestedUrl,
            channel = root.string("channel") ?: root.string("uploader") ?: "",
            site = site.removePrefix("www."),
            duration = root.string("duration_string") ?: duration?.let(::formatDuration) ?: "",
            license = licenseLabel(root.string("license")),
            chapterCount = (root["chapters"] as? JsonArray)?.size ?: 0,
            subtitleLanguages = (root["subtitles"] as? JsonObject)?.keys.orEmpty()
                .filterNot { it == "live_chat" }
                .map { it.substringBefore('-').uppercase() }
                .distinct()
                .take(3),
            videoFormats = markRecommended(videos),
            audioFormats = audioFormats.mapIndexed { index, format -> if (index == 0) format.copy(badge = FormatBadge.BEST) else format },
            thumbnailUrl = root.string("thumbnail"),
        )
    }

    /** "Creative Commons Attribution license (reuse allowed)" vira "Creative Commons". */
    private fun licenseLabel(license: String?): String? = when {
        license.isNullOrBlank() -> null
        license.contains("Creative Commons", ignoreCase = true) -> "Creative Commons"
        license.length <= 24 -> license
        else -> null
    }

    /** Uma opção por altura, da maior para a menor, preferindo o codec mais compatível. */
    private fun pickVideos(videos: List<RawFormat>): List<RawFormat> {
        val byHeight = videos.groupBy { it.height ?: 0 }
        val picked = byHeight.toSortedMap(compareByDescending { it }).map { (height, candidates) ->
            val adaptive = candidates.filter { !it.hasAudio }
            val pool = adaptive.ifEmpty { candidates }
            // Até 1080p o H.264 toca em qualquer lugar; acima disso o YouTube só tem VP9/AV1.
            val order = if (height in 1..1080) listOf("avc1", "vp9", "av01") else listOf("vp9", "av01", "avc1")
            pool.sortedWith(
                compareBy<RawFormat> { order.indexOf(it.videoFamily).let { i -> if (i < 0) order.size else i } }
                    .thenByDescending { it.fps ?: 0.0 }
                    .thenByDescending { it.bitrate },
            ).first()
        }
        val tall = picked.filter { (it.height ?: 0) >= MIN_HEIGHT }
        return if (tall.size >= MIN_VISIBLE_VIDEO) tall else picked
    }

    /** Até quatro faixas de áudio, da maior taxa para a menor, sem repetir codec + taxa. */
    private fun pickAudio(audio: List<RawFormat>): List<RawFormat> =
        audio.filter { it.audioFamily in setOf("mp4a", "opus", "vorbis", "mp3", "flac") }
            .sortedByDescending { it.bitrate }
            .distinctBy { it.audioFamily to Formatters.roundHalfUp(it.bitrate / 8) }
            .take(MAX_AUDIO)

    /** "Recomendado": o melhor H.264 até 1080p; senão o maior até 1080p; senão o primeiro. */
    private fun markRecommended(videos: List<DownloadFormat>): List<DownloadFormat> {
        if (videos.isEmpty()) return videos
        val heights = videos.map { it.quality.removeSuffix("p").toIntOrNull() ?: 0 }
        val index = videos.indices.firstOrNull { videos[it].videoCodec == "avc1" && heights[it] in 1..1080 }
            ?: videos.indices.firstOrNull { heights[it] in 1..1080 }
            ?: 0
        return videos.mapIndexed { i, format -> if (i == index) format.copy(badge = FormatBadge.RECOMMENDED) else format }
    }

    private fun RawFormat.toDownloadFormat(audio: RawFormat?): DownloadFormat {
        val videoMb = sizeMb
        val audioMb = audio?.sizeMb
        val streams = listOfNotNull(videoMb, audioMb)
        val audioCodec = when {
            hasAudio -> audioCodecLabel(audioFamily)
            audio != null -> audioCodecLabel(audio.audioFamily)
            else -> null
        }
        return DownloadFormat(
            id = id,
            kind = MediaKind.VIDEO,
            quality = height?.let { "${it}p" } ?: note ?: id,
            codec = listOfNotNull(videoCodecLabel(videoFamily), audioCodec).joinToString(" + "),
            detail = fps?.let { Formatters.roundHalfUp(it).toString() } ?: "",
            container = ext,
            sizeMb = if (streams.size == (if (hasAudio || audio == null) 1 else 2)) streams.sum() else 0.0,
            resolution = if (width != null && height != null) "$width×$height" else height?.let { "${it}p" } ?: "",
            progressive = hasAudio,
            videoCodec = videoFamily,
            streamSizesMb = if (hasAudio || audio == null) listOfNotNull(videoMb) else listOf(videoMb ?: 0.0, audioMb ?: 0.0),
        )
    }

    private fun RawFormat.toAudioFormat(): DownloadFormat {
        val kbps = Formatters.roundHalfUp(bitrate).toString() + " kbps"
        return DownloadFormat(
            id = id,
            kind = MediaKind.AUDIO,
            quality = kbps,
            codec = audioCodecLabel(audioFamily) ?: acodec.orEmpty(),
            detail = sampleRate?.let { rateLabel(it) } ?: "",
            container = ext,
            sizeMb = sizeMb ?: 0.0,
            resolution = kbps,
            streamSizesMb = listOfNotNull(sizeMb),
        )
    }

    /** 44100 → "44,1 kHz", 48000 → "48 kHz". */
    private fun rateLabel(hz: Int): String {
        val khz = hz / 1000.0
        return (if (hz % 1000 == 0) "${hz / 1000}" else Formatters.decimal(khz, 1)) + " kHz"
    }

    private fun videoCodecLabel(family: String?): String? = when (family) {
        "avc1" -> "H.264"
        "hevc" -> "HEVC"
        "vp9" -> "VP9"
        "vp8" -> "VP8"
        "av01" -> "AV1"
        null -> null
        else -> family.uppercase()
    }

    private fun audioCodecLabel(family: String?): String? = when (family) {
        "mp4a" -> "AAC"
        "opus" -> "Opus"
        "vorbis" -> "Vorbis"
        "mp3" -> "MP3"
        "flac" -> "FLAC"
        "ac-3" -> "AC-3"
        "ec-3" -> "E-AC-3"
        null -> null
        else -> family.uppercase()
    }

    // Leitura do JSON -----------------------------------------------------------------------

    private data class RawFormat(
        val id: String,
        val ext: String,
        val vcodec: String?,
        val acodec: String?,
        val height: Int?,
        val width: Int?,
        val fps: Double?,
        val sizeMb: Double?,
        /** kbps (tbr para vídeo, abr para áudio). */
        val bitrate: Double,
        val sampleRate: Int?,
        val note: String?,
        val protocol: String?,
        val drm: Boolean,
    ) {
        val hasVideo get() = vcodec != null && vcodec != "none"
        val hasAudio get() = acodec != null && acodec != "none"
        val isAudioOnly get() = hasAudio && !hasVideo

        val videoFamily: String?
            get() = vcodec?.takeIf { hasVideo }?.lowercase()?.let {
                when {
                    it.startsWith("avc") || it.startsWith("h264") -> "avc1"
                    it.startsWith("hev") || it.startsWith("hvc") || it.startsWith("h265") -> "hevc"
                    it.startsWith("vp09") || it.startsWith("vp9") -> "vp9"
                    it.startsWith("vp8") -> "vp8"
                    it.startsWith("av01") || it.startsWith("av1") -> "av01"
                    else -> it.substringBefore('.')
                }
            }

        val audioFamily: String?
            get() = acodec?.takeIf { hasAudio }?.lowercase()?.let {
                when {
                    it.startsWith("mp4a") || it.startsWith("aac") -> "mp4a"
                    else -> it.substringBefore('.')
                }
            }

        /** Fora: storyboards, DRM e as faixas "DRC" (volume comprimido, duplicam as normais). */
        val usable: Boolean
            get() = (hasVideo || hasAudio) && !drm && ext != "mhtml" && protocol != "mhtml" &&
                note?.contains("storyboard", ignoreCase = true) != true && !id.endsWith("-drc")
    }

    private fun JsonObject.toRaw(duration: Double?): RawFormat? {
        val id = string("format_id") ?: return null
        val vcodec = string("vcodec")
        val acodec = string("acodec")
        val hasVideo = vcodec != null && vcodec != "none"
        val bitrate = (if (hasVideo) double("tbr") else double("abr") ?: double("tbr")) ?: 0.0
        val bytes = double("filesize") ?: double("filesize_approx")
            ?: if (bitrate > 0 && duration != null) bitrate * 1000 / 8 * duration else null
        return RawFormat(
            id = id,
            ext = string("ext") ?: "mp4",
            vcodec = vcodec,
            acodec = acodec,
            height = double("height")?.toInt(),
            width = double("width")?.toInt(),
            fps = double("fps"),
            sizeMb = bytes?.let { it / 1e6 },
            bitrate = bitrate,
            sampleRate = double("asr")?.toInt(),
            note = string("format_note"),
            protocol = string("protocol"),
            drm = (this["has_drm"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull
}
