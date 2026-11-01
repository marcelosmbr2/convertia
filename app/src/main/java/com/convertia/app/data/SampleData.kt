package com.convertia.app.data

import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.ConversionOptions
import com.convertia.app.domain.ConversionSource
import com.convertia.app.domain.ConversionStatus
import com.convertia.app.domain.DownloadFormat
import com.convertia.app.domain.DownloadJob
import com.convertia.app.domain.DownloadRequest
import com.convertia.app.domain.DownloadStatus
import com.convertia.app.domain.FormatBadge
import com.convertia.app.domain.HistoryEntry
import com.convertia.app.domain.LibraryItem
import com.convertia.app.domain.MediaInfo
import com.convertia.app.domain.MediaKind
import com.convertia.app.domain.OutputFormat
import com.convertia.app.domain.MediaCategory
import com.convertia.app.domain.Preset
import com.convertia.app.domain.RecentLink
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Dados de exemplo do mockup (JOBS0, LIB0, HISTORY, SAMPLES, RECENT, V, A). Usados nos
 * previews, pelo inspetor simulado e, só no build de debug, para popular o app.
 */
object SampleData {

    const val DEFAULT_URL = "https://www.youtube.com/watch?v=k3yB0ard_fs"
    const val DEFAULT_TITLE = "Construindo um teclado mecânico do zero — Montagem completa"

    /** Títulos conhecidos dos links de exemplo. */
    private val knownTitles = mapOf(
        DEFAULT_URL to DEFAULT_TITLE,
        "https://www.youtube.com/watch?v=la4_vectors" to "Introdução à Álgebra Linear — Aula 4",
        "https://vimeo.com/884213301" to "Gravação de campo: chuva no telhado de zinco",
        "https://www.youtube.com/watch?v=gpu_text_22" to "Palestra: Renderizando texto na GPU",
    )

    val videoFormats = listOf(
        DownloadFormat("v2160", MediaKind.VIDEO, "2160p", null, "VP9 + Opus", "60", "webm", 2410.0, "3840×2160"),
        DownloadFormat("v1440", MediaKind.VIDEO, "1440p", null, "VP9 + Opus", "60", "webm", 1180.0, "2560×1440"),
        DownloadFormat("v1080", MediaKind.VIDEO, "1080p", FormatBadge.RECOMMENDED, "H.264 + AAC", "60", "mp4", 412.0, "1920×1080"),
        DownloadFormat("v1080a", MediaKind.VIDEO, "1080p", null, "AV1 + Opus", "30", "mp4", 298.0, "1920×1080"),
        DownloadFormat("v720", MediaKind.VIDEO, "720p", null, "H.264 + AAC", "30", "mp4", 186.0, "1280×720"),
        DownloadFormat("v480", MediaKind.VIDEO, "480p", null, "H.264 + AAC", "30", "mp4", 94.0, "854×480"),
    )

    val audioFormats = listOf(
        DownloadFormat("a1", MediaKind.AUDIO, "160 kbps", FormatBadge.BEST, "Opus", "48 kHz", "webm", 21.4, "160 kbps"),
        DownloadFormat("a2", MediaKind.AUDIO, "128 kbps", null, "AAC", "44,1 kHz", "m4a", 17.1, "128 kbps"),
        DownloadFormat("a3", MediaKind.AUDIO, "320 kbps", null, "MP3 · recodificado", "44,1 kHz", "mp3", 42.8, "320 kbps"),
        DownloadFormat("a4", MediaKind.AUDIO, "192 kbps", null, "MP3 · recodificado", "44,1 kHz", "mp3", 25.7, "192 kbps"),
    )

    fun mediaInfo(url: String = DEFAULT_URL, site: String = "youtube.com") = MediaInfo(
        url = url,
        title = knownTitles[url] ?: DEFAULT_TITLE,
        channel = "Hollow Workshop",
        site = site,
        duration = "18:42",
        license = "CC BY 4.0",
        chapterCount = 9,
        subtitleLanguages = listOf("EN", "DE", "JA"),
        videoFormats = videoFormats,
        audioFormats = audioFormats,
    )

    val presets = listOf(
        Preset(
            "heic-jpg", "Fotos HEIC para JPG", "Qualidade 85 · Remove localização",
            listOf("heic", "heif"), OutputFormat("JPG", MediaCategory.IMAGE), 85,
        ),
        Preset(
            "web-images", "Imagens para web", "Qualidade 80 · Até 2560 px",
            listOf("png", "jpg", "jpeg", "tiff", "bmp"), OutputFormat("WEBP", MediaCategory.IMAGE), 80,
        ),
        Preset(
            "audio-mp3", "Áudio para MP3", "320 kbps · Estéreo",
            listOf("wav", "aiff", "flac", "m4a"), OutputFormat("MP3", MediaCategory.AUDIO), 95,
        ),
        Preset(
            "share-video", "Vídeo para compartilhar", "H.264 · 1080p · Equilibrado",
            listOf("mov", "mkv", "avi", "webm", "m4v"), OutputFormat("MP4", MediaCategory.VIDEO), 80,
        ),
        Preset(
            "scans-pdf", "Digitalizações para PDF", "Reconhecimento de texto · Imagens comprimidas",
            listOf("tiff", "tif"), OutputFormat("PDF", MediaCategory.DOCUMENT), 80,
        ),
    )

    fun conversionItems(): List<ConversionItem> {
        fun item(n: Int, name: String, size: Long, location: String, target: String, ocr: Boolean = false) =
            ConversionItem.create(
                id = "sample-file-$n",
                name = name,
                sizeBytes = size,
                location = location,
                source = ConversionSource.Sample,
                targetExt = target,
                options = ConversionOptions(ocr = ocr),
            )
        return listOf(
            item(1, "IMG_4021.HEIC", 3_200_000, "DCIM/Camera", "JPG")
                .copy(status = ConversionStatus.DONE, progress = 100.0, outputBytes = 4_100_000),
            item(2, "entrevista-bruta.wav", 412_600_000, "Recordings", "FLAC")
                .copy(status = ConversionStatus.CONVERTING, progress = 38.0),
            item(3, "contrato_assinado.pdf", 2_100_000, "Download", "DOCX"),
            item(4, "demo-produto.mov", 1_240_000_000, "Movies", "MP4"),
            item(5, "scan_0142.tiff", 48_100_000, "Documents", "PDF", ocr = true),
        )
    }

    fun downloadJobs(clock: Clock): List<DownloadJob> {
        val todayAt1042 = ZonedDateTime.now(clock).with(LocalTime.of(10, 42)).toInstant().toEpochMilli()
        fun request(title: String, kind: MediaKind, quality: String, container: String, resolution: String, site: String, mb: Double, duration: String) =
            DownloadRequest(
                url = "https://$site/",
                title = title,
                kind = kind,
                formatId = "",
                container = container,
                quality = quality,
                resolution = resolution,
                site = site,
                sizeMb = mb,
                duration = duration,
            )
        return listOf(
            DownloadJob(
                1,
                request("Gravação de campo: chuva no telhado de zinco", MediaKind.AUDIO, "320 kbps", "mp3", "320 kbps", "vimeo.com", 288.0, "2:00:12"),
                DownloadStatus.DOWNLOADING, progress = 64.0, speedMbps = 5.1,
            ),
            DownloadJob(
                2,
                request("Introdução à Álgebra Linear — Aula 3", MediaKind.VIDEO, "720p", "mp4", "1280×720", "youtube.com", 186.0, "48:10"),
                DownloadStatus.QUEUED,
            ),
            DownloadJob(
                3,
                request("Timelapse: aurora boreal sobre Tromsø", MediaKind.VIDEO, "2160p", "webm", "3840×2160", "vimeo.com", 2410.0, "6:02"),
                DownloadStatus.PAUSED, progress = 18.0,
            ),
            DownloadJob(
                4,
                request("Live set — Sessão no galpão", MediaKind.VIDEO, "1080p", "mp4", "1920×1080", "twitch.tv", 1640.0, "1:12:40"),
                DownloadStatus.FAILED, progress = 3.0, error = "HTTP 403 · formato indisponível",
            ),
            DownloadJob(
                5,
                request("Palestra: Renderizando texto na GPU", MediaKind.VIDEO, "1080p", "mp4", "1920×1080", "youtube.com", 538.0, "42:17"),
                DownloadStatus.DONE, progress = 100.0, finishedAt = todayAt1042,
            ),
        )
    }

    fun libraryItems(clock: Clock): List<LibraryItem> {
        val now = clock.instant()
        fun daysAgo(days: Long) = now.minus(Duration.ofDays(days)).toEpochMilli()
        fun hoursAgo(hours: Long) = now.minus(Duration.ofHours(hours)).toEpochMilli()
        return listOf(
            LibraryItem("sample-lib-1", "Palestra: Renderizando texto na GPU", "MP4", "1920×1080", 538_000_000, "42:17", hoursAgo(1), MediaKind.VIDEO),
            LibraryItem("sample-lib-2", "Podcast ep. 112 — Criando software local-first", "M4A", "256 kbps", 64_200_000, "1:08:33", hoursAgo(3), MediaKind.AUDIO),
            LibraryItem("sample-lib-3", "Gravação de campo — Floresta ao amanhecer", "MP3", "320 kbps", 48_100_000, "20:04", daysAgo(9), MediaKind.AUDIO),
            LibraryItem("sample-lib-4", "Como funcionam os relógios mecânicos", "MKV", "3840×2160", 1_920_000_000, "24:51", daysAgo(11), MediaKind.VIDEO),
            LibraryItem("sample-lib-5", "Aula 3 — Espaços vetoriais", "MP4", "1280×720", 201_000_000, "51:12", daysAgo(12), MediaKind.VIDEO),
            LibraryItem("sample-lib-6", "Synth Jam 04", "FLAC", "48 kHz", 212_000_000, "18:40", daysAgo(14), MediaKind.AUDIO),
            LibraryItem("sample-lib-7", "Timelapse — Porto à noite", "WEBM", "1920×1080", 96_400_000, "2:30", daysAgo(18), MediaKind.VIDEO),
        )
    }

    fun history(clock: Clock): List<HistoryEntry> {
        val now = clock.instant()
        fun minutesAgo(minutes: Long) = now.minus(Duration.ofMinutes(minutes)).toEpochMilli()
        fun daysAgo(days: Long) = now.minus(Duration.ofDays(days)).toEpochMilli()
        return listOf(
            HistoryEntry("sample-h1", "Relatório trimestral.pptx", "PPTX", "PDF", 18_400_000, 6_100_000, minutesAgo(20)),
            HistoryEntry("sample-h2", "IMG_4021.HEIC", "HEIC", "JPG", 3_200_000, 4_100_000, minutesAgo(21)),
            HistoryEntry("sample-h3", "ferias-0418.mov", "MOV", "MP4", 2_860_000_000, 912_000_000, minutesAgo(156)),
            HistoryEntry("sample-h4", "logo-final.png", "PNG", "WEBP", 1_800_000, 240_000, daysAgo(1)),
            HistoryEntry("sample-h5", "capitulo-01.docx", "DOCX", "EPUB", 640_000, 410_000, daysAgo(1)),
            HistoryEntry("sample-h6", "notas-de-campo.m4a", "M4A", "MP3", 54_200_000, 38_900_000, daysAgo(9)),
        )
    }

    fun recents(clock: Clock): List<RecentLink> {
        val now = clock.instant()
        return listOf(
            RecentLink("https://www.youtube.com/watch?v=la4_vectors", "Introdução à Álgebra Linear — Aula 4", "youtube.com", now.minus(Duration.ofHours(2)).toEpochMilli()),
            RecentLink("https://vimeo.com/884213301", "Gravação de campo: chuva no telhado de zinco", "vimeo.com", now.minus(Duration.ofDays(1)).toEpochMilli()),
            RecentLink("https://www.youtube.com/watch?v=gpu_text_22", "Palestra: Renderizando texto na GPU", "youtube.com", now.minus(Duration.ofDays(8)).toEpochMilli()),
        )
    }
}
