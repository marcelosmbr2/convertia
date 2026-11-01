package com.convertia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Valores esperados obtidos rodando makeFile/factor do mockup no Node. */
class FormatCatalogTest {

    @Test
    fun `formato sugerido e categoria batem com o mockup`() {
        val cases = listOf(
            "IMG_4021.HEIC" to Triple(MediaCategory.IMAGE, "JPG", MediaCategory.IMAGE),
            "entrevista-bruta.wav" to Triple(MediaCategory.AUDIO, "MP3", MediaCategory.AUDIO),
            "contrato_assinado.pdf" to Triple(MediaCategory.DOCUMENT, "DOCX", MediaCategory.DOCUMENT),
            "demo-produto.mov" to Triple(MediaCategory.VIDEO, "MP4", MediaCategory.VIDEO),
            "scan_0142.tiff" to Triple(MediaCategory.IMAGE, "PDF", MediaCategory.DOCUMENT),
            "foto.jpeg" to Triple(MediaCategory.IMAGE, "WEBP", MediaCategory.IMAGE),
            "b.webp" to Triple(MediaCategory.IMAGE, "PNG", MediaCategory.IMAGE),
            "c.gif" to Triple(MediaCategory.IMAGE, "PNG", MediaCategory.IMAGE),
            "d.mp3" to Triple(MediaCategory.AUDIO, "WAV", MediaCategory.AUDIO),
            "e.mp4" to Triple(MediaCategory.VIDEO, "WEBM", MediaCategory.VIDEO),
            "g.7z" to Triple(MediaCategory.ARCHIVE, "ZIP", MediaCategory.ARCHIVE),
            "h.epub" to Triple(MediaCategory.EBOOK, "MOBI", MediaCategory.EBOOK),
            "i.tar" to Triple(MediaCategory.ARCHIVE, "ZIP", MediaCategory.ARCHIVE),
            "sem_extensao" to Triple(MediaCategory.DOCUMENT, "PDF", MediaCategory.DOCUMENT),
            "x.unknown" to Triple(MediaCategory.DOCUMENT, "PDF", MediaCategory.DOCUMENT),
            "song.flac" to Triple(MediaCategory.AUDIO, "MP3", MediaCategory.AUDIO),
            "doc.docx" to Triple(MediaCategory.DOCUMENT, "PDF", MediaCategory.DOCUMENT),
            "pic.ico" to Triple(MediaCategory.IMAGE, "PNG", MediaCategory.IMAGE),
        )
        cases.forEach { (name, expected) ->
            val item = ConversionItem.create("id", name, 1_000_000, "p", ConversionSource.Sample)
            assertEquals(name, expected.first, item.category)
            assertEquals(name, expected.second, item.target.ext)
            assertEquals(name, expected.third, item.target.category)
        }
    }

    @Test
    fun `GIF de um vídeo é vídeo, GIF de uma imagem é imagem`() {
        assertEquals(MediaCategory.VIDEO, FormatCatalog.targetCategory(MediaCategory.VIDEO, "GIF"))
        assertEquals(MediaCategory.IMAGE, FormatCatalog.targetCategory(MediaCategory.IMAGE, "GIF"))
        assertEquals(MediaCategory.AUDIO, FormatCatalog.targetCategory(MediaCategory.VIDEO, "MP3"))
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
            Case("a.epub", 640_000, "MOBI", default, 576_000),
            Case("a.mp4", 100_000_000, "MP3", default, 12_020_690),
            Case("a.mp4", 100_000_000, "GIF", default, 90_000_000),
            Case("a.docx", 1_000_000, "PDF", default.copy(compressImages = false), 420_000),
        )
        cases.forEach { c ->
            val item = ConversionItem.create("id", c.name, c.size, "p", ConversionSource.Sample, c.target, c.options)
            assertEquals("${c.name} → ${c.target}", c.expected, SizeEstimator.estimatedBytes(item))
        }
    }

    @Test
    fun `qualidade fora das predefinições vira personalizada`() {
        assertEquals(QualityPreset.BEST, ConversionOptions().withQuality(95).preset)
        assertEquals(QualityPreset.CUSTOM, ConversionOptions().withQuality(70).preset)
        assertEquals(55, ConversionOptions().withPreset(QualityPreset.SMALLER).quality)
    }

    @Test
    fun `nome de saída troca só a última extensão`() {
        val item = ConversionItem.create("id", "IMG_4021.HEIC", 1, "p", ConversionSource.Sample)
        assertEquals("IMG_4021.jpg", item.outputName)
        val archive = ConversionItem.create("id", "backup.zip", 1, "p", ConversionSource.Sample, "TAR.GZ")
        assertEquals("backup.tar.gz", archive.outputName)
    }
}
