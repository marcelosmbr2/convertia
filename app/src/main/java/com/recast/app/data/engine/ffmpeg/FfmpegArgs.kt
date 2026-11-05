package com.recast.app.data.engine.ffmpeg

import com.recast.app.domain.AudioBitrate
import com.recast.app.domain.AudioChannels
import com.recast.app.domain.ConversionOptions
import com.recast.app.domain.FormatCatalog
import com.recast.app.domain.ImageResize
import com.recast.app.domain.MediaCategory
import com.recast.app.domain.OutputFormat
import com.recast.app.domain.VideoCodec
import com.recast.app.domain.VideoResolution
import kotlin.math.roundToInt

/**
 * Monta os argumentos do ffmpeg para cada conversão. Funções puras, sem Android, para
 * serem testadas como as do yt-dlp. Os argumentos globais (`-nostdin -y -progress`) ficam
 * com quem executa; aqui vai da entrada em diante.
 */
object FfmpegArgs {

    /** Encoders por hardware (MediaCodec) que o ffmpeg empacotado expõe. */
    private val hardwareEncoders = mapOf(VideoCodec.H264 to "h264_mediacodec", VideoCodec.HEVC to "hevc_mediacodec")

    /**
     * Vídeo → vídeo, vídeo/áudio → áudio, ou vídeo → imagem (um quadro). [hardware] pede o
     * encoder MediaCodec quando o codec tem um.
     */
    fun convert(
        input: String,
        output: String,
        target: OutputFormat,
        options: ConversionOptions,
        probe: MediaProbe,
        hardware: Boolean,
    ): List<String> = buildList {
        add("-i"); add(input)
        when (target.category) {
            MediaCategory.VIDEO -> if (target.ext == "GIF") addAll(gif(options, probe)) else addAll(video(target, options, probe, hardware))
            MediaCategory.AUDIO -> addAll(audio(target.ext, options, probe))
            MediaCategory.IMAGE -> addAll(frame(target.ext, options, probe))
            else -> error("ffmpeg não gera ${target.ext}")
        }
        add(output)
    }

    /** Se este par de argumentos usa um encoder por hardware (para decidir a repetição em software). */
    fun usesHardware(args: List<String>): Boolean = args.any { it.endsWith("_mediacodec") }

    /** Vídeo pelo codec, com o áudio recodificado no que o contêiner aceita. */
    private fun video(target: OutputFormat, options: ConversionOptions, probe: MediaProbe, hardware: Boolean): List<String> = buildList {
        val container = target.ext
        val codec = options.codec.takeIf { it in FormatCatalog.codecsFor(container) }
            ?: FormatCatalog.codecsFor(container).firstOrNull() ?: VideoCodec.H264
        val hardwareEncoder = hardwareEncoders[codec]?.takeIf { hardware }
        add("-map"); add("0:v:0")
        if (probe.hasAudio) { add("-map"); add("0:a:0") }
        add("-sn"); add("-dn")
        add("-map_metadata"); add("0")

        val filters = buildList {
            scaleFilter(options.resolution)?.let(::add)
            // Dimensões pares, exigidas pelos encoders 4:2:0.
            if (options.resolution == VideoResolution.ORIGINAL) add("scale=trunc(iw/2)*2:trunc(ih/2)*2")
        }
        add("-vf"); add(filters.joinToString(","))
        add("-pix_fmt"); add("yuv420p")

        add("-c:v")
        if (hardwareEncoder != null) {
            add(hardwareEncoder)
            add("-b:v"); add("${hardwareBitrateKbps(options, probe)}k")
        } else {
            when (codec) {
                VideoCodec.H264 -> { add("libx264"); add("-preset"); add("medium"); add("-crf"); add(crf(options.quality).toString()) }
                VideoCodec.HEVC -> { add("libx265"); add("-preset"); add("medium"); add("-crf"); add((crf(options.quality) + 5).toString()); add("-x265-params"); add("log-level=error") }
                VideoCodec.VP9 -> { add("libvpx-vp9"); add("-b:v"); add("0"); add("-crf"); add((crf(options.quality) + 8).coerceAtMost(63).toString()); add("-row-mt"); add("1"); add("-deadline"); add("good"); add("-cpu-used"); add("2") }
                VideoCodec.AV1 -> { add("libsvtav1"); add("-preset"); add("8"); add("-crf"); add((crf(options.quality) + 10).coerceAtMost(63).toString()) }
            }
        }
        if (codec == VideoCodec.HEVC && container in setOf("MP4", "MOV")) { add("-tag:v"); add("hvc1") }

        if (probe.hasAudio) {
            when (container) {
                "WEBM" -> { add("-c:a"); add("libopus"); add("-b:a"); add("128k") }
                "AVI" -> { add("-c:a"); add("libmp3lame"); add("-b:a"); add("192k") }
                else -> { add("-c:a"); add("aac"); add("-b:a"); add("160k") }
            }
            // Opus só aceita estas taxas; a maioria dos vídeos vem em 44,1 ou 48 kHz.
            if (container == "WEBM") { add("-ar"); add("48000") }
        }
        if (container in setOf("MP4", "MOV")) { add("-movflags"); add("+faststart") }
        add("-f"); add(muxer(container))
    }

    /** GIF animado com paleta própria, 12 quadros/s, largura limitada. */
    private fun gif(options: ConversionOptions, probe: MediaProbe): List<String> {
        val width = when (options.resolution) {
            VideoResolution.ORIGINAL -> 480
            VideoResolution.P1080 -> 1080
            VideoResolution.P720 -> 720
        }
        val filter = "fps=12,scale=min(iw\\,$width):-2:flags=lanczos,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle"
        // Sem -map: a saída do filter_complex já é a única faixa (o muxer GIF só aceita uma).
        return listOf("-an", "-sn", "-dn", "-filter_complex", filter, "-loop", "0", "-f", "gif")
    }

    /** Só o áudio, no codec do formato pedido. */
    private fun audio(ext: String, options: ConversionOptions, probe: MediaProbe): List<String> = buildList {
        add("-vn"); add("-sn"); add("-dn")
        add("-map"); add("0:a:0")
        add("-map_metadata"); add("0")
        val kbps = options.bitrate.kbps
        when (ext) {
            "MP3" -> { add("-c:a"); add("libmp3lame"); add("-b:a"); add("${kbps}k"); add("-id3v2_version"); add("3"); add("-f"); add("mp3") }
            "AAC" -> { add("-c:a"); add("aac"); add("-b:a"); add("${minOf(kbps, 256)}k"); add("-f"); add("adts") }
            "M4A" -> { add("-c:a"); add("aac"); add("-b:a"); add("${minOf(kbps, 256)}k"); add("-movflags"); add("+faststart"); add("-f"); add("ipod") }
            "OPUS" -> { add("-c:a"); add("libopus"); add("-b:a"); add("${minOf(kbps, 256)}k"); add("-ar"); add("48000"); add("-f"); add("opus") }
            // Vorbis por qualidade (escala por canal: mono a 320k não existe em taxa fixa).
            "OGG" -> { add("-c:a"); add("libvorbis"); add("-q:a"); add(vorbisQuality(kbps).toString()); add("-f"); add("ogg") }
            "FLAC" -> { add("-c:a"); add("flac"); add("-compression_level"); add("8"); add("-f"); add("flac") }
            "WAV" -> { add("-c:a"); add("pcm_s16le"); add("-f"); add("wav") }
            "AIFF" -> { add("-c:a"); add("pcm_s16be"); add("-f"); add("aiff") }
            else -> error("ffmpeg não gera áudio $ext")
        }
        val channels = when (options.channels) {
            AudioChannels.MONO -> 1
            AudioChannels.STEREO -> 2
        }
        // Não "cria" canais: mono de origem continua mono.
        if (probe.audioChannels == null || probe.audioChannels > channels || (channels == 2 && probe.audioChannels > 2)) {
            add("-ac"); add(channels.toString())
        }
    }

    /** Um quadro representativo do vídeo (entre os primeiros 100 depois de 10 % da duração). */
    private fun frame(ext: String, options: ConversionOptions, probe: MediaProbe): List<String> = buildList {
        add("-map"); add("0:v:0")
        add("-an"); add("-sn"); add("-dn")
        val filters = listOfNotNull("thumbnail=100", resizeFilter(options.resize))
        add("-vf"); add(filters.joinToString(","))
        add("-frames:v"); add("1")
        add("-update"); add("1")
        when (ext) {
            "JPG" -> { add("-c:v"); add("mjpeg"); add("-q:v"); add(mjpegQuality(options.quality).toString()); add("-pix_fmt"); add("yuvj420p"); add("-f"); add("image2") }
            "PNG" -> { add("-c:v"); add("png"); add("-f"); add("image2") }
            "WEBP" -> { add("-c:v"); add("libwebp"); add("-quality"); add(options.quality.toString()); add("-f"); add("image2") }
            else -> error("ffmpeg não gera quadro $ext")
        }
    }

    /** Seleciona o ponto de partida para o quadro: 10 % do vídeo, até 30 s. */
    fun frameSeekSeconds(probe: MediaProbe): Double? = probe.durationSeconds?.let { (it * 0.1).coerceAtMost(30.0) }?.takeIf { it > 0.5 }

    // Imagens -------------------------------------------------------------------------------

    /** Imagem (PNG temporário) → AVIF, TIFF, BMP, GIF ou ICO. */
    fun image(input: String, output: String, ext: String, options: ConversionOptions): List<String> = buildList {
        add("-i"); add(input)
        when (ext) {
            "AVIF" -> {
                add("-c:v"); add("libaom-av1"); add("-still-picture"); add("1")
                add("-crf"); add(avifCrf(options.quality).toString()); add("-b:v"); add("0")
                add("-cpu-used"); add("6"); add("-pix_fmt"); add("yuv420p"); add("-f"); add("avif")
            }
            "TIFF" -> { add("-c:v"); add("tiff"); add("-compression_algo"); add("deflate"); add("-f"); add("image2") }
            "BMP" -> { add("-c:v"); add("bmp"); add("-pix_fmt"); add("bgr24"); add("-f"); add("image2") }
            "GIF" -> { add("-vf"); add("split[a][b];[a]palettegen=max_colors=256:stats_mode=single[p];[b][p]paletteuse=dither=sierra2_4a"); add("-f"); add("gif") }
            "ICO" -> { add("-vf"); add("scale=min(iw\\,256):-1"); add("-c:v"); add("png"); add("-pix_fmt"); add("rgba"); add("-f"); add("ico") }
            else -> error("ffmpeg não gera imagem $ext")
        }
        add(output)
    }

    /** Imagem em formato que o Android não decodifica (TIFF, AVIF antigo) → PNG temporário. */
    fun decodeToPng(input: String, output: String): List<String> = listOf("-i", input, "-frames:v", "1", "-update", "1", "-c:v", "png", "-f", "image2", output)

    // Mapeamentos -----------------------------------------------------------------------------

    /** Qualidade do slider (55 / 80 / 95) → CRF do x264 (29 / 23 / 20). */
    fun crf(quality: Int): Int = (41 - 0.225 * quality).roundToInt().coerceIn(10, 40)

    /** libaom para imagem parada: 55 / 80 / 95 → 40 / 30 / 24. */
    fun avifCrf(quality: Int): Int = (62 - 0.4 * quality).roundToInt().coerceIn(10, 63)

    /** Qualidade Vorbis: 128 / 192 / 320 kbps → 4 / 6 / 9 (≈ as mesmas taxas em estéreo). */
    fun vorbisQuality(kbps: Int): Int = when {
        kbps <= 128 -> 4
        kbps <= 192 -> 6
        else -> 9
    }

    /** `-q:v` do mjpeg (2 melhor … 31 pior): 55 / 80 / 95 → 15 / 8 / 3. */
    fun mjpegQuality(quality: Int): Int = (31 - 0.29 * quality).roundToInt().coerceIn(2, 31)

    /**
     * Encoders MediaCodec trabalham por taxa de bits: bits por pixel por quadro entre 0,10
     * (menor) e 0,15 (melhor), sobre a resolução de saída e os quadros/s da origem.
     */
    fun hardwareBitrateKbps(options: ConversionOptions, probe: MediaProbe): Int {
        val height = probe.height ?: 1080
        val width = probe.width ?: (height * 16 / 9)
        val outHeight = when (options.resolution) {
            VideoResolution.ORIGINAL -> height
            VideoResolution.P1080 -> minOf(height, 1080)
            VideoResolution.P720 -> minOf(height, 720)
        }
        val outWidth = width * outHeight / height
        val fps = probe.fps?.coerceIn(10.0, 60.0) ?: 30.0
        val bitsPerPixel = 0.05 + options.quality / 100.0 * 0.1
        return (outWidth * outHeight * fps * bitsPerPixel / 1000).roundToInt().coerceIn(300, 60_000)
    }

    private fun scaleFilter(resolution: VideoResolution): String? = when (resolution) {
        VideoResolution.ORIGINAL -> null
        VideoResolution.P1080 -> "scale=-2:min(ih\\,1080)"
        VideoResolution.P720 -> "scale=-2:min(ih\\,720)"
    }

    private fun resizeFilter(resize: ImageResize): String? = when (resize) {
        ImageResize.ORIGINAL -> null
        ImageResize.HALF -> "scale=trunc(iw/4)*2:trunc(ih/4)*2"
        ImageResize.MAX_2560 -> "scale=min(iw\\,2560):-2"
    }

    private fun muxer(container: String): String = when (container) {
        "MP4" -> "mp4"
        "MOV" -> "mov"
        "WEBM" -> "webm"
        "MKV" -> "matroska"
        "AVI" -> "avi"
        else -> error("Contêiner desconhecido: $container")
    }

    private val AudioBitrate.kbps: Int
        get() = when (this) {
            AudioBitrate.KBPS_128 -> 128
            AudioBitrate.KBPS_192 -> 192
            AudioBitrate.KBPS_320 -> 320
        }
}
