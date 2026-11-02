package com.convertia.app.domain

/** Categorias de arquivo que o conversor entende (CATS no mockup). */
enum class MediaCategory { IMAGE, DOCUMENT, AUDIO, VIDEO, ARCHIVE, EBOOK }

/**
 * Formato de saída. A extensão sozinha não basta: GIF existe como imagem e como vídeo.
 * [ext] fica em maiúsculas, como aparece na interface ("JPG", "TAR.GZ").
 */
data class OutputFormat(val ext: String, val category: MediaCategory)

/**
 * O que os motores deste build e deste aparelho conseguem fazer. Decide quais conversões o
 * catálogo oferece: sem o LibreOffice, por exemplo, documentos nem entram na lista.
 */
data class EngineCapabilities(
    /** LibreOffice embutido (documentos, planilhas, apresentações e e-books). */
    val office: Boolean = true,
    /** Reconstrução de PDF em documento editável. */
    val pdfToDocument: Boolean = true,
    /** Reconhecimento de texto em imagens e páginas digitalizadas. */
    val ocr: Boolean = true,
) {
    companion object {
        val All = EngineCapabilities()
        val None = EngineCapabilities(office = false, pdfToDocument = false, ocr = false)
    }
}

/** Catálogo de formatos e regras de conversão (FORMATS, SRC_CAT, DEFAULT_TARGET… no mockup). */
object FormatCatalog {

    /** Formatos de saída por categoria, na ordem em que aparecem no seletor. */
    val outputs: Map<MediaCategory, List<String>> = mapOf(
        MediaCategory.IMAGE to listOf("JPG", "PNG", "WEBP", "AVIF", "HEIC", "GIF", "TIFF", "BMP", "ICO"),
        MediaCategory.DOCUMENT to listOf("PDF", "DOCX", "ODT", "RTF", "TXT", "HTML", "MD"),
        MediaCategory.AUDIO to listOf("MP3", "AAC", "M4A", "OPUS", "OGG", "FLAC", "WAV", "AIFF"),
        MediaCategory.VIDEO to listOf("MP4", "MOV", "WEBM", "MKV", "AVI", "GIF"),
        MediaCategory.ARCHIVE to listOf("ZIP", "7Z", "TAR.GZ", "TAR"),
        MediaCategory.EBOOK to listOf("EPUB", "FB2"),
    )

    private val sourceCategories: Map<String, MediaCategory> = buildMap {
        fun add(category: MediaCategory, exts: String) = exts.split(' ').forEach { put(it, category) }
        add(MediaCategory.IMAGE, "jpg jpeg png webp avif heic heif gif tiff tif bmp ico svg dng")
        add(MediaCategory.DOCUMENT, "pdf doc docx odt rtf txt html htm md pptx ppt xlsx xls csv")
        add(MediaCategory.AUDIO, "mp3 wav flac aac ogg m4a opus aiff aif wma")
        add(MediaCategory.VIDEO, "mp4 mov mkv webm avi wmv m4v 3gp")
        add(MediaCategory.ARCHIVE, "zip 7z rar tar gz tgz bz2")
        add(MediaCategory.EBOOK, "epub fb2")
    }

    /** Planilhas e apresentações: o LibreOffice só exporta para PDF e imagem. */
    private val officeOnlyDocs = setOf("xls", "xlsx", "csv", "ppt", "pptx")

    private val defaultTargets: Map<String, String> = mapOf(
        "heic" to "JPG", "heif" to "JPG", "png" to "WEBP", "jpg" to "WEBP", "jpeg" to "WEBP",
        "webp" to "PNG", "pdf" to "PNG", "mp3" to "WAV", "mp4" to "WEBM", "zip" to "7Z",
        "epub" to "PDF", "mov" to "MP4", "wav" to "MP3", "tiff" to "PDF", "7z" to "ZIP",
    )

    private val categoryDefaults: Map<MediaCategory, String> = mapOf(
        MediaCategory.IMAGE to "PNG",
        MediaCategory.DOCUMENT to "PDF",
        MediaCategory.AUDIO to "MP3",
        MediaCategory.VIDEO to "MP4",
        MediaCategory.ARCHIVE to "ZIP",
        MediaCategory.EBOOK to "EPUB",
    )

    /** Quanto o arquivo de saída tende a pesar em relação ao original (estimativa). */
    private val sizeFactors: Map<String, Double> = mapOf(
        "JPG" to .55, "PNG" to 1.3, "WEBP" to .32, "AVIF" to .24, "HEIC" to .4, "GIF" to .9,
        "TIFF" to 2.0, "BMP" to 3.0, "ICO" to .05, "PDF" to .42, "DOCX" to .6, "ODT" to .6,
        "RTF" to .8, "TXT" to .05, "HTML" to .3, "MD" to .04, "MP3" to .12, "AAC" to .1,
        "M4A" to .11, "OPUS" to .07, "OGG" to .1, "FLAC" to .55, "WAV" to 4.0, "AIFF" to 4.0,
        "MP4" to .38, "MOV" to 1.1, "WEBM" to .3, "MKV" to .9, "AVI" to 1.2, "ZIP" to 1.05,
        "7Z" to .85, "TAR.GZ" to .95, "TAR" to 1.1, "EPUB" to .6, "FB2" to .5,
    )

    private val lossless = setOf("PNG", "TIFF", "BMP", "ICO", "GIF", "FLAC", "WAV", "AIFF")

    /** Extensão (sem ponto, minúsculas) de um nome de arquivo; vazia se não houver. */
    fun extensionOf(fileName: String): String =
        if ('.' in fileName) fileName.substringAfterLast('.').lowercase() else ""

    /** Categoria de um arquivo de origem; `null` para extensões que o app não converte. */
    fun categoryOf(ext: String): MediaCategory? = sourceCategories[ext.lowercase()]

    /**
     * Tudo em que um arquivo com a extensão [sourceExt] pode ser convertido, na ordem do
     * seletor (categoria de origem primeiro). Nunca inclui o próprio formato. Vazio para
     * extensões não suportadas ou sem motor neste build.
     */
    fun targetsFor(sourceExt: String, capabilities: EngineCapabilities = EngineCapabilities.All): List<OutputFormat> {
        val ext = sourceExt.lowercase()
        val category = categoryOf(ext) ?: return emptyList()
        val office = capabilities.office
        val images = pick(MediaCategory.IMAGE, "JPG", "PNG", "WEBP")
        val editableDocs = pick(MediaCategory.DOCUMENT, "DOCX", "ODT", "RTF", "HTML", "MD")
        val formats: List<OutputFormat> = when (category) {
            MediaCategory.VIDEO -> all(MediaCategory.VIDEO) + all(MediaCategory.AUDIO) + images
            MediaCategory.AUDIO -> all(MediaCategory.AUDIO)
            MediaCategory.IMAGE -> buildList {
                addAll(all(MediaCategory.IMAGE))
                add(OutputFormat("PDF", MediaCategory.DOCUMENT))
                if (capabilities.ocr) {
                    if (office) addAll(editableDocs)
                    add(OutputFormat("TXT", MediaCategory.DOCUMENT))
                }
            }
            MediaCategory.DOCUMENT -> when {
                ext == "pdf" -> buildList {
                    addAll(images)
                    if (capabilities.pdfToDocument) {
                        if (office) addAll(editableDocs)
                        add(OutputFormat("TXT", MediaCategory.DOCUMENT))
                        if (office) addAll(all(MediaCategory.EBOOK))
                    }
                }
                !office -> emptyList()
                ext in officeOnlyDocs -> pick(MediaCategory.DOCUMENT, "PDF") + images
                else -> all(MediaCategory.DOCUMENT) + all(MediaCategory.EBOOK) + images
            }
            MediaCategory.ARCHIVE -> all(MediaCategory.ARCHIVE)
            MediaCategory.EBOOK -> if (office) all(MediaCategory.EBOOK) + all(MediaCategory.DOCUMENT) else emptyList()
        }
        return formats.filterNot { isSameFormat(ext, it.ext) }
    }

    /** Formato sugerido para um arquivo novo; `null` se ele não puder ser convertido. */
    fun defaultTarget(sourceExt: String, capabilities: EngineCapabilities = EngineCapabilities.All): OutputFormat? {
        val ext = sourceExt.lowercase()
        val targets = targetsFor(ext, capabilities)
        if (targets.isEmpty()) return null
        val preferred = defaultTargets[ext] ?: categoryOf(ext)?.let(categoryDefaults::get)
        return targets.firstOrNull { it.ext == preferred } ?: targets.first()
    }

    /** Codecs que cabem em cada contêiner de vídeo; vazio quando não há escolha (AVI, GIF). */
    fun codecsFor(containerExt: String): List<VideoCodec> = when (containerExt.uppercase()) {
        "MP4", "MOV", "MKV" -> listOf(VideoCodec.H264, VideoCodec.HEVC, VideoCodec.AV1)
        "WEBM" -> listOf(VideoCodec.VP9, VideoCodec.AV1)
        else -> emptyList()
    }

    fun isLossless(targetExt: String): Boolean = targetExt in lossless

    fun sizeFactor(targetExt: String): Double = sizeFactors[targetExt] ?: 0.8

    /** Se [targetExt] é o próprio formato do arquivo (JPEG conta como JPG, TIF como TIFF). */
    fun isSameFormat(sourceExt: String, targetExt: String): Boolean {
        val src = sourceExt.lowercase()
        return targetExt.lowercase() == src ||
            (targetExt == "JPG" && src == "jpeg") ||
            (targetExt == "TIFF" && src == "tif") ||
            (targetExt == "HTML" && src == "htm") ||
            (targetExt == "AIFF" && src == "aif")
    }

    private fun all(category: MediaCategory) = outputs.getValue(category).map { OutputFormat(it, category) }

    private fun pick(category: MediaCategory, vararg exts: String) = exts.map { OutputFormat(it, category) }
}
