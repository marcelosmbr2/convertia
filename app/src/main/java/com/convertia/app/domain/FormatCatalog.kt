package com.convertia.app.domain

/** Categorias de arquivo que o conversor entende (CATS no mockup). */
enum class MediaCategory { IMAGE, DOCUMENT, AUDIO, VIDEO, ARCHIVE, EBOOK }

/**
 * Formato de saída. A extensão sozinha não basta: GIF existe como imagem e como vídeo.
 * [ext] fica em maiúsculas, como aparece na interface ("JPG", "TAR.GZ").
 */
data class OutputFormat(val ext: String, val category: MediaCategory)

/** Catálogo de formatos e regras de conversão (FORMATS, COMPAT, SRC_CAT… no mockup). */
object FormatCatalog {

    /** Formatos de saída por categoria, na ordem em que aparecem no seletor. */
    val outputs: Map<MediaCategory, List<String>> = mapOf(
        MediaCategory.IMAGE to listOf("JPG", "PNG", "WEBP", "AVIF", "HEIC", "GIF", "TIFF", "BMP", "ICO"),
        MediaCategory.DOCUMENT to listOf("PDF", "DOCX", "ODT", "RTF", "TXT", "HTML", "MD"),
        MediaCategory.AUDIO to listOf("MP3", "AAC", "M4A", "OPUS", "OGG", "FLAC", "WAV", "AIFF"),
        MediaCategory.VIDEO to listOf("MP4", "MOV", "WEBM", "MKV", "AVI", "GIF"),
        MediaCategory.ARCHIVE to listOf("ZIP", "7Z", "TAR.GZ", "TAR"),
        MediaCategory.EBOOK to listOf("EPUB", "MOBI", "AZW3", "FB2"),
    )

    /** Para quais categorias cada categoria de origem pode ser convertida. */
    val compatible: Map<MediaCategory, List<MediaCategory>> = mapOf(
        MediaCategory.IMAGE to listOf(MediaCategory.IMAGE, MediaCategory.DOCUMENT),
        MediaCategory.DOCUMENT to listOf(MediaCategory.DOCUMENT, MediaCategory.IMAGE, MediaCategory.EBOOK),
        MediaCategory.AUDIO to listOf(MediaCategory.AUDIO),
        MediaCategory.VIDEO to listOf(MediaCategory.VIDEO, MediaCategory.AUDIO, MediaCategory.IMAGE),
        MediaCategory.ARCHIVE to listOf(MediaCategory.ARCHIVE),
        MediaCategory.EBOOK to listOf(MediaCategory.EBOOK, MediaCategory.DOCUMENT),
    )

    private val sourceCategories: Map<String, MediaCategory> = buildMap {
        fun add(category: MediaCategory, exts: String) = exts.split(' ').forEach { put(it, category) }
        add(MediaCategory.IMAGE, "jpg jpeg png webp avif heic heif gif tiff tif bmp ico svg dng")
        add(MediaCategory.DOCUMENT, "pdf doc docx odt rtf txt html htm md pptx ppt xlsx xls csv")
        add(MediaCategory.AUDIO, "mp3 wav flac aac ogg m4a opus aiff aif wma")
        add(MediaCategory.VIDEO, "mp4 mov mkv webm avi wmv m4v 3gp")
        add(MediaCategory.ARCHIVE, "zip 7z rar tar gz tgz bz2")
        add(MediaCategory.EBOOK, "epub mobi azw3 fb2")
    }

    private val defaultTargets: Map<String, String> = mapOf(
        "heic" to "JPG", "heif" to "JPG", "png" to "WEBP", "jpg" to "WEBP", "jpeg" to "WEBP",
        "webp" to "PNG", "pdf" to "DOCX", "mp3" to "WAV", "mp4" to "WEBM", "zip" to "7Z",
        "epub" to "MOBI", "mov" to "MP4", "wav" to "MP3", "tiff" to "PDF", "7z" to "ZIP",
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
        "7Z" to .85, "TAR.GZ" to .95, "TAR" to 1.1, "EPUB" to .6, "MOBI" to .9, "AZW3" to .9,
        "FB2" to .5,
    )

    private val lossless = setOf("PNG", "TIFF", "BMP", "ICO", "GIF", "FLAC", "WAV", "AIFF")

    /** Extensão (sem ponto, minúsculas) de um nome de arquivo; vazia se não houver. */
    fun extensionOf(fileName: String): String =
        if ('.' in fileName) fileName.substringAfterLast('.').lowercase() else ""

    /** Categoria de um arquivo de origem; extensões desconhecidas contam como documento. */
    fun categoryOf(ext: String): MediaCategory = sourceCategories[ext.lowercase()] ?: MediaCategory.DOCUMENT

    /**
     * Categoria de saída de [targetExt] para um arquivo de [source]: a primeira categoria
     * compatível que tem esse formato (findCat no mockup).
     */
    fun targetCategory(source: MediaCategory, targetExt: String): MediaCategory =
        compatible.getValue(source).firstOrNull { targetExt in outputs.getValue(it) } ?: source

    /** Formato sugerido para um arquivo novo; nunca o próprio formato de origem. */
    fun defaultTarget(sourceExt: String): OutputFormat {
        val ext = sourceExt.lowercase()
        val category = categoryOf(ext)
        var target = defaultTargets[ext] ?: categoryDefaults.getValue(category)
        if (target.lowercase() == ext) {
            target = outputs.getValue(category).first { it.lowercase() != ext }
        }
        return OutputFormat(target, targetCategory(category, target))
    }

    fun isLossless(targetExt: String): Boolean = targetExt in lossless

    fun sizeFactor(targetExt: String): Double = sizeFactors[targetExt] ?: 0.8

    /** Se [targetExt] é o próprio formato do arquivo (JPEG conta como JPG, TIF como TIFF). */
    fun isSameFormat(sourceExt: String, targetExt: String): Boolean {
        val src = sourceExt.lowercase()
        return targetExt.lowercase() == src ||
            (targetExt == "JPG" && src == "jpeg") ||
            (targetExt == "TIFF" && src == "tif")
    }
}
