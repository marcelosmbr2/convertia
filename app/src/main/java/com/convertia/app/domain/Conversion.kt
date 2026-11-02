package com.convertia.app.domain

/** Predefinições de qualidade do inspetor: Menor 55, Equilibrado 80, Melhor 95. */
enum class QualityPreset(val quality: Int?) {
    SMALLER(55), BALANCED(80), BEST(95), CUSTOM(null);

    companion object {
        val choices = listOf(SMALLER, BALANCED, BEST)
        fun forQuality(quality: Int): QualityPreset = choices.firstOrNull { it.quality == quality } ?: CUSTOM
    }
}

enum class VideoCodec(val sizeFactor: Double) { H264(1.0), HEVC(.65), VP9(.65), AV1(.5) }

enum class VideoResolution(val sizeFactor: Double) { ORIGINAL(1.0), P1080(.7), P720(.42) }

enum class AudioBitrate(val sizeFactor: Double) { KBPS_128(.4), KBPS_192(.6), KBPS_320(1.0) }

enum class AudioChannels { STEREO, MONO }

enum class ImageResize(val sizeFactor: Double) { ORIGINAL(1.0), HALF(.3), MAX_2560(.7) }

enum class CompressionLevel(val sizeFactor: Double) { FAST(1.08), NORMAL(1.0), MAX(.92) }

/** Ajustes de conversão de um arquivo (opts no mockup). */
data class ConversionOptions(
    val preset: QualityPreset = QualityPreset.BALANCED,
    val quality: Int = 80,
    val codec: VideoCodec = VideoCodec.H264,
    val resolution: VideoResolution = VideoResolution.ORIGINAL,
    val bitrate: AudioBitrate = AudioBitrate.KBPS_320,
    val channels: AudioChannels = AudioChannels.STEREO,
    val resize: ImageResize = ImageResize.ORIGINAL,
    val stripMetadata: Boolean = true,
    val compressImages: Boolean = true,
    val ocr: Boolean = false,
    val compressionLevel: CompressionLevel = CompressionLevel.NORMAL,
) {
    fun withQuality(quality: Int) = copy(quality = quality, preset = QualityPreset.forQuality(quality))

    fun withPreset(preset: QualityPreset) = copy(preset = preset, quality = preset.quality ?: quality)
}

/** De onde veio o arquivo, para o motor real saber como abri-lo. */
sealed interface ConversionSource {
    /** Arquivo escolhido no seletor do sistema ou na galeria (`content://`). */
    data class Document(val uri: String) : ConversionSource

    /** Arquivo da Biblioteca do app. */
    data class Library(val itemId: String) : ConversionSource

    /** Dado de exemplo (previews e build de debug). */
    data object Sample : ConversionSource
}

enum class ConversionStatus { READY, CONVERTING, DONE, FAILED }

/** Um arquivo na lista do Converter. */
data class ConversionItem(
    val id: String,
    val name: String,
    /** Extensão de origem, minúsculas e sem ponto. */
    val ext: String,
    val category: MediaCategory,
    val sizeBytes: Long,
    /** Pasta exibida sob o nome ("DCIM/Camera", "Download"). */
    val location: String,
    val source: ConversionSource,
    val target: OutputFormat,
    /** Em que este arquivo pode ser convertido neste aparelho (sem o próprio formato). */
    val targets: List<OutputFormat>,
    val status: ConversionStatus = ConversionStatus.READY,
    /** 0–100. */
    val progress: Double = 0.0,
    val outputBytes: Long = 0,
    /** Arquivos gerados (um PDF vira uma imagem por página). */
    val outputs: List<SavedFile> = emptyList(),
    /** Motivo da falha, quando [status] é FAILED. */
    val error: String? = null,
    val options: ConversionOptions = ConversionOptions(),
    /** Se o ajuste "Reconhecer texto" faz sentido neste build. */
    val ocrAvailable: Boolean = false,
) {
    /** Nome do arquivo de saída: "IMG_4021.jpg". */
    val outputName: String
        get() = name.substringBeforeLast('.', name) + "." + target.ext.lowercase()

    /** Pronto ou em andamento: ainda vai passar pelo motor. */
    val isPending: Boolean
        get() = status == ConversionStatus.READY || status == ConversionStatus.CONVERTING

    fun supports(format: OutputFormat): Boolean = format in targets

    /** Volta para "pronto" se já tinha terminado ou falhado (ao trocar formato ou ajustes). */
    fun resetIfDone(): ConversionItem =
        if (status == ConversionStatus.DONE || status == ConversionStatus.FAILED) {
            copy(status = ConversionStatus.READY, progress = 0.0, outputBytes = 0, outputs = emptyList(), error = null)
        } else {
            this
        }

    /** Troca o formato e ajusta o codec ao que o contêiner aceita. */
    fun withTarget(target: OutputFormat): ConversionItem {
        val codecs = FormatCatalog.codecsFor(target.ext)
        val options = if (codecs.isNotEmpty() && options.codec !in codecs) options.copy(codec = codecs.first()) else options
        return copy(target = target, options = options).resetIfDone()
    }

    companion object {
        /**
         * Cria um item com o formato sugerido (makeFile no mockup). `null` se o arquivo não
         * puder ser convertido neste aparelho. [targetExt] fora do possível cai no sugerido.
         */
        fun create(
            id: String,
            name: String,
            sizeBytes: Long,
            location: String,
            source: ConversionSource,
            targetExt: String? = null,
            options: ConversionOptions = ConversionOptions(),
            capabilities: EngineCapabilities = EngineCapabilities.All,
        ): ConversionItem? {
            val ext = FormatCatalog.extensionOf(name)
            val category = FormatCatalog.categoryOf(ext) ?: return null
            val targets = FormatCatalog.targetsFor(ext, capabilities)
            val target = targets.firstOrNull { it.ext == targetExt }
                ?: FormatCatalog.defaultTarget(ext, capabilities)
                ?: return null
            return ConversionItem(
                id = id,
                name = name,
                ext = ext,
                category = category,
                sizeBytes = sizeBytes,
                location = location,
                source = source,
                target = target,
                targets = targets,
                options = options,
                ocrAvailable = capabilities.ocr,
            ).withTarget(target)
        }
    }
}

/** Estimativa do tamanho de saída (factor() no mockup). */
object SizeEstimator {

    fun factor(item: ConversionItem): Double {
        val target = item.target
        val options = item.options
        val lossless = FormatCatalog.isLossless(target.ext)
        var k = FormatCatalog.sizeFactor(target.ext)
        val category = target.category
        if (!lossless && category in setOf(MediaCategory.IMAGE, MediaCategory.VIDEO, MediaCategory.AUDIO)) {
            k *= 0.45 + options.quality / 145.0
        }
        when (category) {
            MediaCategory.VIDEO -> k *= options.codec.sizeFactor * options.resolution.sizeFactor
            MediaCategory.AUDIO -> if (!lossless) k *= options.bitrate.sizeFactor
            MediaCategory.IMAGE -> k *= options.resize.sizeFactor
            MediaCategory.DOCUMENT -> if (options.compressImages) k *= 0.7
            MediaCategory.ARCHIVE -> k *= options.compressionLevel.sizeFactor
            MediaCategory.EBOOK -> Unit
        }
        return k
    }

    fun estimatedBytes(item: ConversionItem): Long = Formatters.roundHalfUp(item.sizeBytes * factor(item))
}

/** Regra salva de formato e qualidade (PRESETS no mockup). */
data class Preset(
    val id: String,
    val name: String,
    val detail: String,
    /** Extensões de origem, minúsculas. */
    val sources: List<String>,
    val target: OutputFormat,
    val quality: Int,
) {
    /** "HEIC, HEIF → JPG". */
    val rule: String get() = sources.joinToString(", ") { it.uppercase() } + " → " + target.ext
}
