package com.recast.app.data.engine.archive

import com.recast.app.data.convert.ConversionJob
import com.recast.app.domain.CompressionLevel
import com.recast.app.domain.ConversionItem
import com.recast.app.domain.ConversionOptions
import com.recast.app.domain.ConversionSource
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** Roda de verdade na JVM: o Commons Compress é Java puro. */
class ArchiveConverterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val converter = ArchiveConverter()

    private val contents = mapOf(
        "leia-me.txt" to "olá, mundo".toByteArray(),
        "pasta/dados.bin" to ByteArray(300_000) { (it % 251).toByte() },
        "pasta/sub/vazio.txt" to ByteArray(0),
    )

    private var dirs = 0

    private fun sourceDir(): File {
        val dir = folder.newFolder("src-${dirs++}")
        contents.forEach { (name, bytes) -> File(dir, name).apply { parentFile.mkdirs(); writeBytes(bytes) } }
        return dir
    }

    private suspend fun pack(ext: String, level: CompressionLevel = CompressionLevel.NORMAL): File {
        val dir = sourceDir()
        val out = File(folder.root, "packed.${ext.lowercase()}")
        converter.compress(dir.walkTopDown().filter { it.isFile }.toList(), dir, out, ext, level) {}
        return out
    }

    private fun readAll(dir: File): Map<String, ByteArray> =
        dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).path.replace(File.separatorChar, '/') to it.readBytes() }

    @Test
    fun `ida e volta em cada formato preserva o conteudo`() = runTest {
        for (ext in listOf("ZIP", "7Z", "TAR", "TAR.GZ")) {
            val packed = pack(ext)
            assertTrue(ext, packed.length() > 0)
            val into = folder.newFolder("out-$ext")
            val sourceExt = when (ext) { "TAR.GZ" -> "tgz" else -> ext.lowercase() }
            val total = converter.extract(packed, sourceExt, into) {}
            val back = readAll(into)
            assertEquals(ext, contents.keys, back.keys)
            contents.forEach { (name, bytes) -> assertTrue("$ext $name", bytes.contentEquals(back.getValue(name))) }
            assertEquals(ext, contents.values.sumOf { it.size.toLong() }, total)
        }
    }

    @Test
    fun `conversao completa zip para 7z com progresso e nome final`() = runTest {
        val zip = pack("ZIP")
        val item = ConversionItem.create("id", "backup.zip", zip.length(), "Download", ConversionSource.Sample, "7Z", ConversionOptions(compressionLevel = CompressionLevel.MAX))!!
        val outDir = folder.newFolder("work")
        val job = ConversionJob(item, zip, outDir, hardwareAcceleration = false)
        val seen = mutableListOf<Double>()
        val outputs = converter.convert(job) { percent, _ -> seen += percent }
        assertEquals(listOf(File(outDir, "backup.7z")), outputs)
        assertTrue(outputs.first().length() > 0)
        assertTrue(seen.isNotEmpty() && seen.last() <= 100.0 && seen == seen.sorted())
        assertFalse(File(outDir, "extracted").exists())
    }

    @Test
    fun `gz solto vira um arquivo com o nome sem extensao`() = runTest {
        val plain = folder.newFile("notas.txt").apply { writeText("abc".repeat(1000)) }
        val gz = File(folder.root, "notas.txt.gz")
        org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream(gz.outputStream()).use { it.write(plain.readBytes()) }
        val into = folder.newFolder("gz")
        converter.extract(gz, "gz", into) {}
        assertEquals(plain.readText(), File(into, "notas.txt").readText())
    }

    @Test
    fun `entradas que escapam da pasta sao ignoradas`() = runTest {
        val evil = File(folder.root, "evil.zip")
        ZipArchiveOutputStream(evil).use { zip ->
            zip.putArchiveEntry(ZipArchiveEntry("../../escapou.txt")); zip.write("x".toByteArray()); zip.closeArchiveEntry()
            zip.putArchiveEntry(ZipArchiveEntry("ok.txt")); zip.write("y".toByteArray()); zip.closeArchiveEntry()
        }
        val into = folder.newFolder("safe")
        converter.extract(evil, "zip", into) {}
        assertEquals(setOf("ok.txt"), readAll(into).keys)
        assertFalse(File(folder.root, "escapou.txt").exists())
    }

    @Test
    fun `conteudo grande demais e recusado`() = runTest {
        val small = ArchiveConverter(maxExtractedBytes = 1_000)
        val zip = pack("ZIP")
        try {
            small.extract(zip, "zip", folder.newFolder("big")) {}
            fail("devia recusar")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("grande demais"))
        }
    }

    @Test
    fun `rar sem leitor nao e suportado`() {
        val item = ConversionItem.create("id", "a.rar", 1, "p", ConversionSource.Sample)!!
        assertFalse(ArchiveConverter().supports(item))
        assertTrue(ArchiveConverter(rarExtractor = { _, _, _ -> 0L }).supports(item))
    }
}
