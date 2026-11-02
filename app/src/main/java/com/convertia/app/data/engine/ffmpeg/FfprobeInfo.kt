package com.convertia.app.data.engine.ffmpeg

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** O que o ffprobe diz de um arquivo de mídia (o que interessa para montar a conversão). */
data class MediaProbe(
    /** Segundos; `null` quando o contêiner não informa (streams ao vivo, alguns WebM). */
    val durationSeconds: Double?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val width: Int?,
    val height: Int?,
    /** Quadros por segundo do vídeo, se conhecido. */
    val fps: Double?,
    val videoCodec: String?,
    val audioCodec: String?,
    val audioChannels: Int?,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Lê a saída de `ffprobe -of json -show_format -show_streams`. */
        fun parse(text: String): MediaProbe {
            val root = json.parseToJsonElement(text).jsonObject
            val streams = (root["streams"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            val format = root["format"] as? JsonObject
            // Capas embutidas em áudio aparecem como vídeo "attached_pic": não contam como vídeo.
            val video = streams.firstOrNull { it.str("codec_type") == "video" && it.disposition("attached_pic") != 1 }
            val audio = streams.firstOrNull { it.str("codec_type") == "audio" }
            val duration = format?.num("duration") ?: video?.num("duration") ?: audio?.num("duration")
            return MediaProbe(
                durationSeconds = duration?.takeIf { it > 0 },
                hasVideo = video != null,
                hasAudio = audio != null,
                width = video?.int("width"),
                height = video?.int("height"),
                fps = video?.str("avg_frame_rate")?.let(::fraction) ?: video?.str("r_frame_rate")?.let(::fraction),
                videoCodec = video?.str("codec_name"),
                audioCodec = audio?.str("codec_name"),
                audioChannels = audio?.int("channels"),
            )
        }

        /** "30000/1001" → 29.97; `null` para "0/0". */
        private fun fraction(text: String): Double? {
            val parts = text.split('/')
            val num = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
            val den = parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0
            return if (den == 0.0 || num <= 0) null else num / den
        }

        private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.content
        private fun JsonObject.num(key: String) = (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
        private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull ?: (this[key] as? JsonPrimitive)?.doubleOrNull?.toInt()
        private fun JsonObject.disposition(key: String) = (this["disposition"] as? JsonObject)?.int(key)
    }
}
