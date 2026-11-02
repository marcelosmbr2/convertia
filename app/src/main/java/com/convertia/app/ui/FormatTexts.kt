package com.convertia.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.convertia.app.R
import com.convertia.app.domain.MediaCategory
import com.convertia.app.domain.OutputFormat

/** Textos do catálogo de formatos (descrições do seletor e nomes das categorias). */
object FormatTexts {
    private val descriptions: Map<OutputFormat, Int> = mapOf(
        OutputFormat("JPG", MediaCategory.IMAGE) to R.string.format_image_jpg,
        OutputFormat("PNG", MediaCategory.IMAGE) to R.string.format_image_png,
        OutputFormat("WEBP", MediaCategory.IMAGE) to R.string.format_image_webp,
        OutputFormat("AVIF", MediaCategory.IMAGE) to R.string.format_image_avif,
        OutputFormat("HEIC", MediaCategory.IMAGE) to R.string.format_image_heic,
        OutputFormat("GIF", MediaCategory.IMAGE) to R.string.format_image_gif,
        OutputFormat("TIFF", MediaCategory.IMAGE) to R.string.format_image_tiff,
        OutputFormat("BMP", MediaCategory.IMAGE) to R.string.format_image_bmp,
        OutputFormat("ICO", MediaCategory.IMAGE) to R.string.format_image_ico,
        OutputFormat("PDF", MediaCategory.DOCUMENT) to R.string.format_document_pdf,
        OutputFormat("DOCX", MediaCategory.DOCUMENT) to R.string.format_document_docx,
        OutputFormat("ODT", MediaCategory.DOCUMENT) to R.string.format_document_odt,
        OutputFormat("RTF", MediaCategory.DOCUMENT) to R.string.format_document_rtf,
        OutputFormat("TXT", MediaCategory.DOCUMENT) to R.string.format_document_txt,
        OutputFormat("HTML", MediaCategory.DOCUMENT) to R.string.format_document_html,
        OutputFormat("MD", MediaCategory.DOCUMENT) to R.string.format_document_md,
        OutputFormat("MP3", MediaCategory.AUDIO) to R.string.format_audio_mp3,
        OutputFormat("AAC", MediaCategory.AUDIO) to R.string.format_audio_aac,
        OutputFormat("M4A", MediaCategory.AUDIO) to R.string.format_audio_m4a,
        OutputFormat("OPUS", MediaCategory.AUDIO) to R.string.format_audio_opus,
        OutputFormat("OGG", MediaCategory.AUDIO) to R.string.format_audio_ogg,
        OutputFormat("FLAC", MediaCategory.AUDIO) to R.string.format_audio_flac,
        OutputFormat("WAV", MediaCategory.AUDIO) to R.string.format_audio_wav,
        OutputFormat("AIFF", MediaCategory.AUDIO) to R.string.format_audio_aiff,
        OutputFormat("MP4", MediaCategory.VIDEO) to R.string.format_video_mp4,
        OutputFormat("MOV", MediaCategory.VIDEO) to R.string.format_video_mov,
        OutputFormat("WEBM", MediaCategory.VIDEO) to R.string.format_video_webm,
        OutputFormat("MKV", MediaCategory.VIDEO) to R.string.format_video_mkv,
        OutputFormat("AVI", MediaCategory.VIDEO) to R.string.format_video_avi,
        OutputFormat("GIF", MediaCategory.VIDEO) to R.string.format_video_gif,
        OutputFormat("ZIP", MediaCategory.ARCHIVE) to R.string.format_archive_zip,
        OutputFormat("7Z", MediaCategory.ARCHIVE) to R.string.format_archive_7z,
        OutputFormat("TAR.GZ", MediaCategory.ARCHIVE) to R.string.format_archive_tar_gz,
        OutputFormat("TAR", MediaCategory.ARCHIVE) to R.string.format_archive_tar,
        OutputFormat("EPUB", MediaCategory.EBOOK) to R.string.format_ebook_epub,
        OutputFormat("FB2", MediaCategory.EBOOK) to R.string.format_ebook_fb2,
    )

    @StringRes
    fun description(format: OutputFormat): Int? = descriptions[format]

    @StringRes
    fun categoryLabel(category: MediaCategory): Int = when (category) {
        MediaCategory.IMAGE -> R.string.category_image
        MediaCategory.DOCUMENT -> R.string.category_document
        MediaCategory.AUDIO -> R.string.category_audio
        MediaCategory.VIDEO -> R.string.category_video
        MediaCategory.ARCHIVE -> R.string.category_archive
        MediaCategory.EBOOK -> R.string.category_ebook
    }

    /** Nome no singular ("Imagem"), usado em "3,2 MB · Imagem HEIC". */
    @StringRes
    fun categorySingular(category: MediaCategory): Int = when (category) {
        MediaCategory.IMAGE -> R.string.category_image_one
        MediaCategory.DOCUMENT -> R.string.category_document_one
        MediaCategory.AUDIO -> R.string.category_audio_one
        MediaCategory.VIDEO -> R.string.category_video_one
        MediaCategory.ARCHIVE -> R.string.category_archive_one
        MediaCategory.EBOOK -> R.string.category_ebook_one
    }

    /** "todas as imagens", usado em "Aplicar a todas as imagens". */
    @StringRes
    fun categoryAll(category: MediaCategory): Int = when (category) {
        MediaCategory.IMAGE -> R.string.category_image_all
        MediaCategory.DOCUMENT -> R.string.category_document_all
        MediaCategory.AUDIO -> R.string.category_audio_all
        MediaCategory.VIDEO -> R.string.category_video_all
        MediaCategory.ARCHIVE -> R.string.category_archive_all
        MediaCategory.EBOOK -> R.string.category_ebook_all
    }
}

@Composable
fun OutputFormat.descriptionText(): String = FormatTexts.description(this)?.let { stringResource(it) }.orEmpty()
