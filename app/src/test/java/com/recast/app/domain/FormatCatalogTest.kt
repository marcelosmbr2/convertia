package com.recast.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Valores esperados obtidos rodando makeFile/factor do mockup no Node. */
class FormatCatalogTest {

    @Test
    fun `formato sugerido e categoria batem com o mockup`() {
        val cases = listOf(
            "IMG_4021.HEIC" to Triple(MediaCategory.IMAGE, "JPG", MediaCategory.IMAGE),
            "entrevista-bruta.wav" to Triple(MediaCategory.AUDIO, "MP3", MediaCategory.AUDIO),
            "contrato_assinado.pdf" to Triple(MediaCategory.DOCUMENT, "PNG", MediaCategory.IMAGE),
            "demo-produto.mov" to Triple(MediaCategory.VIDEO, "MP4", MediaCategory.VIDEO),
            "scan_0142.tiff" to Triple(MediaCategory.IMAGE, "PDF", MediaCategory.DOCUMENT),
            "foto.jpeg" to Triple(MediaCategory.IMAGE, "WEBP", MediaCategory.IMAGE),
            "b.webp" to Triple(MediaCategory.IMAGE, "PNG", MediaCategory.IMAGE),
            "c.gif" to Triple(MediaCategory.IMAGE, "PNG", MediaCategory.IMAGE),
            "d.mp3" to Triple(MediaCategory.AUDIO, "WAV", MediaCategory.AUDIO),
            "e.mp4" to Triple(MediaCategory.VIDEO, "WEBM", MediaCategory.VIDEO),
            "g.7z" to Triple(MediaCategory.ARCHIVE, "ZIP", MediaCategory.ARCHIVE),
            "h.epub" to Triple(MediaCategory.EBOOK, "PDF", MediaCategory.DOCUMENT),
            "i.tar" to Triple(MediaCategory.ARCHIVE, "ZIP", MediaCategory.ARCHIVE),
            "song.flac" to Triple(MediaCategory.AUDIO, "MP3", MediaCategory.AUDIO),
            "doc.docx" to Triple(MediaCategory.DOCUMENT, "PDF", MediaCategory.DOCUMENT),
            "pic.ico" to Triple(MediaCategory.IMAGE, "PNG", MediaCategory.IMAGE),
            "planilha.xlsx" to Triple(MediaCategory.DOCUMENT, "PDF", MediaCategory.DOCUMENT),
            "livro.fb2" to Triple(MediaCategory.EBOOK, "EPUB", MediaCategory.EBOOK),
            "backup.rar" to Triple(MediaCategory.ARCHIVE, "ZIP", MediaCategory.ARCHIVE),
            "logo.svg" to Triple(MediaCategory.IMAGE, "PNG", MediaCategory.IMAGE),
        )
        cases.forEach { (name, expected) ->
            val item = ConversionItem.create("id", name, 1_000_000, "p", ConversionSource.Sample)!!
            assertEquals(name, expected.first, item.category)
            assertEquals(name, expected.second, item.target.ext)
            assertEquals(name, expected.third, item.target.category)
        }
    }

    @Test
    fun `extensao desconhecida nao entra na lista`() {
        assertNull(ConversionItem.create("id", "sem_extensao", 1, "p", ConversionSource.Sample))
        assertNull(ConversionItem.create("id", "x.unknown", 1, "p", ConversionSource.Sample))
        assertNull(ConversionItem.create("id", "livro.mobi", 1, "p", ConversionSource.Sample))
        assertTrue(FormatCatalog.targetsFor("srt").isEmpty())
    }

    @Test
    fun `GIF de um video e video, GIF de uma imagem e imagem`() {
        val video = FormatCatalog.targetsFor("mp4")
        assertTrue(OutputFormat("GIF", MediaCategory.VIDEO) in video)
        assertTrue(OutputFormat("MP3", MediaCategory.AUDIO) in video)
        assertTrue(OutputFormat("JPG", MediaCategory.IMAGE) in video)
        assertTrue(OutputFormat("GIF", MediaCategory.IMAGE) in FormatCatalog.targetsFor("png"))
        assertTrue(OutputFormat("GIF", MediaCategory.IMAGE) !in video)
    }

    @Test
    fun `matriz exclui o proprio formato`() {
        assertTrue(FormatCatalog.targetsFor("jpeg").none { it.ext == "JPG" })
        assertTrue(FormatCatalog.targetsFor("tif").none { it.ext == "TIFF" })
        assertTrue(FormatCatalog.targetsFor("htm").none { it.ext == "HTML" })
        assertTrue(FormatCatalog.targetsFor("mp3").none { it.ext == "MP3" })
    }

    @Test
    fun `sem LibreOffice, documentos e e-books saem da lista e PDF so vira imagem`() {
        val caps = EngineCapabilities.None
        assertTrue(FormatCatalog.targetsFor("docx", caps).isEmpty())
        assertTrue(FormatCatalog.targetsFor("epub", caps).isEmpty())
        assertTrue(FormatCatalog.targetsFor("xlsx", caps).isEmpty())
        assertEquals(listOf("JPG", "PNG", "WEBP"), FormatCatalog.targetsFor("pdf", caps).map { it.ext })
        assertEquals(listOf("PDF"), FormatCatalog.targetsFor("png", caps).filter { it.category == MediaCategory.DOCUMENT }.map { it.ext })
        assertNull(ConversionItem.create("id", "a.docx", 1, "p", ConversionSource.Sample, capabilities = caps))
        assertEquals("PNG", ConversionItem.create("id", "a.pdf", 1, "p", ConversionSource.Sample, "DOCX", capabilities = caps)?.target?.ext)
    }

    @Test
    fun `com tudo ligado, PDF vira documento e imagem vira texto por OCR`() {
        val pdf = FormatCatalog.targetsFor("pdf").map { it.ext }
        assertTrue(pdf.containsAll(listOf("DOCX", "ODT", "TXT", "EPUB", "FB2", "PNG")))
        assertTrue("PDF" !in pdf)
        val image = FormatCatalog.targetsFor("jpg")
        assertTrue(OutputFormat("DOCX", MediaCategory.DOCUMENT) in image)
        assertTrue(OutputFormat("TXT", MediaCategory.DOCUMENT) in image)
        // Planilhas só saem como PDF ou imagem.
        assertEquals(listOf("PDF", "JPG", "PNG", "WEBP"), FormatCatalog.targetsFor("xlsx").map { it.ext })
    }

    @Test
    fun `trocar o conteiner ajusta o codec`() {
        val item = ConversionItem.create("id", "a.mov", 1, "p", ConversionSource.Sample, "MP4")!!
        assertEquals(VideoCodec.H264, item.options.codec)
        val webm = item.withTarget(OutputFormat("WEBM", MediaCategory.VIDEO))
        assertEquals(VideoCodec.VP9, webm.options.codec)
        val back = webm.withTarget(OutputFormat("MKV", MediaCategory.VIDEO))
        assertEquals(VideoCodec.H264, back.options.codec)
        assertTrue(FormatCatalog.codecsFor("GIF").isEmpty())
    }

    @Test
    fun `formato atual considera JPEG e TIF`() {
        assertTrue(FormatCatalog.isSameFormat("jpeg", "JPG"))
        assertTrue(FormatCatalog.isSameFormat("tif", "TIFF"))
        assertTrue(FormatCatalog.isSameFormat("png", "PNG"))
        assertFalse(FormatCatalog.isSameFormat("png", "WEBP"))
    }

    @Test
    fun `estimativa de tamanho bate com o mockup`() {
        data class Case(val name: String, val size: Long, val target: String, val options: ConversionOptions, val expected: Long)
        val default = ConversionOptions()
        val cases = listOf(
            Case("IMG_4021.HEIC", 3_200_000, "JPG", default, 1_763_034),
            Case("entrevista-bruta.wav", 412_600_000, "FLAC", default, 226_930_000),
            Case("contrato_assinado.pdf", 2_100_000, "DOCX", default, 882_000),
            Case("demo-produto.mov", 1_240_000_000, "MP4", default, 472_012_414),
            Case(
                "demo-produto.mov", 1_240_000_000, "MP4",
                default.copy(codec = VideoCodec.AV1, resolution = VideoResolution.P720).withQuality(55),
                82_061_917,
            ),
            Case("scan_0142.tiff", 48_100_000, "PDF", default, 14_141_400),
            Case("a.wav", 50_000_000, "MP3", default.copy(bitrate = AudioBitrate.KBPS_128).withQuality(95), 2_652_414),
            Case("a.png", 1_800_000, "WEBP", default.copy(resize = ImageResize.MAX_2560), 403_895),
            Case("a.zip", 10_000_000, "7Z", default.copy(compressionLevel = CompressionLevel.MAX), 7_820_000),
            Case("a.fb2", 640_000, "EPUB", default, 384_000),
            Case("a.mp4", 100_000_000, "MP3", default, 12_020_690),
            Case("a.mp4", 100_000_000, "GIF", default, 90_000_000),
            Case("a.docx", 1_000_000, "PDF", default.copy(compressImages = false), 420_000),
        )
        cases.forEach { c ->
            val item = ConversionItem.create("id", c.name, c.size, "p", ConversionSource.Sample, c.target, c.options)!!
            assertEquals("${c.name} → ${c.target}", c.target, item.target.ext)
            assertEquals("${c.name} → ${c.target}", c.expected, SizeEstimator.estimatedBytes(item))
        }
    }

    @Test
    fun `qualidade fora das predefinicoes vira personalizada`() {
        assertEquals(QualityPreset.BEST, ConversionOptions().withQuality(95).preset)
        assertEquals(QualityPreset.CUSTOM, ConversionOptions().withQuality(70).preset)
        assertEquals(55, ConversionOptions().withPreset(QualityPreset.SMALLER).quality)
    }

    @Test
    fun `nome de saida troca so a ultima extensao`() {
        val item = ConversionItem.create("id", "IMG_4021.HEIC", 1, "p", ConversionSource.Sample)!!
        assertEquals("IMG_4021.jpg", item.outputName)
        val archive = ConversionItem.create("id", "backup.zip", 1, "p", ConversionSource.Sample, "TAR.GZ")!!
        assertEquals("backup.tar.gz", archive.outputName)
    }
}
