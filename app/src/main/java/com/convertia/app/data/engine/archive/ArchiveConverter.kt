package com.convertia.app.data.engine.archive

import com.convertia.app.data.convert.ConversionJob
import com.convertia.app.data.convert.FileConverter
import com.convertia.app.data.convert.ProgressListener
import com.convertia.app.domain.CompressionLevel
import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.MediaCategory
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.tukaani.xz.LZMA2Options
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.Deflater
import kotlin.coroutines.coroutineContext

/**
 * Compactados: extrai ZIP, 7Z, TAR, TAR.GZ/TGZ, GZ e BZ2 para a pasta de trabalho e
 * recompacta em ZIP, 7Z, TAR.GZ ou TAR. RAR entra por um [extractor][rarExtractor] à
 * parte (libarchive), porque o formato é proprietário.
 */
class ArchiveConverter(
    private val rarExtractor: Extractor? = null,
    private val maxExtractedBytes: Long = DEFAULT_MAX_EXTRACTED_BYTES,
) : FileConverter {

    /** Extrai um compactado em [into]; devolve os bytes gravados. */
    fun interface Extractor {
        suspend fun extract(archive: File, into: File, onBytes: (Long) -> Unit): Long
    }

    override fun supports(item: ConversionItem): Boolean =
        item.category == MediaCategory.ARCHIVE && item.target.category == MediaCategory.ARCHIVE &&
            (item.ext != "rar" || rarExtractor != null)

    override suspend fun convert(job: ConversionJob, progress: ProgressListener): List<File> {
        val item = job.item
        val extracted = File(job.outputDir, "extracted").apply { mkdirs() }
        // Extração: metade da barra, pelos bytes descompactados em relação ao tamanho de entrada.
        val estimate = maxOf(item.sizeBytes, 1)
        val total = extract(job.input, item.ext, extracted) { bytes ->
            progress.onProgress(minOf(50.0, bytes * 50.0 / (estimate * 2)), null)
        }
        progress.onProgress(50.0, null)
        val files = extracted.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty() && extracted.listFiles().isNullOrEmpty()) throw IOException("O arquivo compactado está vazio")
        val output = job.output
        compress(files, extracted, output, item.target.ext, item.options.compressionLevel) { bytes ->
            progress.onProgress(50.0 + minOf(49.5, bytes * 50.0 / maxOf(total, 1)), null)
        }
        extracted.deleteRecursively()
        return listOf(output)
    }

    // Extração --------------------------------------------------------------------------------

    suspend fun extract(input: File, ext: String, into: File, onBytes: (Long) -> Unit): Long = when (ext) {
        "zip" -> extractZip(input, into, onBytes)
        "7z" -> extract7z(input, into, onBytes)
        "tar" -> extractTar(BufferedInputStream(input.inputStream()), into, onBytes)
        "tgz" -> extractTar(GzipCompressorInputStream(BufferedInputStream(input.inputStream())), into, onBytes)
        "gz" -> extractSingle(input, into, onBytes, ::GzipCompressorInputStream)
        "bz2" -> extractSingle(input, into, onBytes, ::BZip2CompressorInputStream)
        "rar" -> (rarExtractor ?: throw IOException("Este aparelho não lê RAR")).extract(input, into, onBytes)
        else -> throw IOException("Formato de compactado desconhecido: $ext")
    }

    private suspend fun extractZip(input: File, into: File, onBytes: (Long) -> Unit): Long {
        var total = 0L
        ZipFile.builder().setFile(input).get().use { zip ->
            for (entry in zip.entries.asSequence()) {
                coroutineContext.ensureActive()
                if (entry.isUnixSymlink) continue
                val target = safeTarget(into, entry.name) ?: continue
                if (entry.isDirectory) { target.mkdirs(); continue }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { total = write(it, target, total, onBytes) }
                if (entry.time > 0) target.setLastModified(entry.time)
            }
        }
        return total
    }

    private suspend fun extract7z(input: File, into: File, onBytes: (Long) -> Unit): Long {
        var total = 0L
        val sevenZ = try {
            SevenZFile.builder().setFile(input).get()
        } catch (e: org.apache.commons.compress.PasswordRequiredException) {
            throw IOException("Compactado protegido por senha")
        }
        sevenZ.use { archive ->
            var entry: SevenZArchiveEntry? = archive.nextEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                val target = safeTarget(into, entry.name)
                if (target != null) {
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        archive.getInputStream(entry).use { total = write(it, target, total, onBytes) }
                        if (entry.hasLastModifiedDate) target.setLastModified(entry.lastModifiedTime.toMillis())
                    }
                }
                entry = archive.nextEntry
            }
        }
        return total
    }

    private suspend fun extractTar(stream: InputStream, into: File, onBytes: (Long) -> Unit): Long {
        var total = 0L
        TarArchiveInputStream(stream).use { tar -> total = extractEntries(tar, into, onBytes) }
        return total
    }

    private suspend fun <E : ArchiveEntry> extractEntries(archive: ArchiveInputStream<E>, into: File, onBytes: (Long) -> Unit): Long {
        var total = 0L
        var entry: E? = archive.nextEntry
        while (entry != null) {
            coroutineContext.ensureActive()
            val isLink = (entry as? TarArchiveEntry)?.let { it.isSymbolicLink || it.isLink } ?: false
            val target = if (isLink) null else safeTarget(into, entry.name)
            if (target != null) {
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    total = write(archive, target, total, onBytes, close = false)
                    entry.lastModifiedDate?.time?.takeIf { it > 0 }?.let(target::setLastModified)
                }
            }
            entry = archive.nextEntry
        }
        return total
    }

    /** `.gz` e `.bz2` soltos: um arquivo só, com o nome sem a extensão. */
    private suspend fun extractSingle(input: File, into: File, onBytes: (Long) -> Unit, open: (InputStream) -> InputStream): Long {
        val name = input.name.substringBeforeLast('.').ifEmpty { "arquivo" }.let { if (it == "src") "arquivo" else it }
        val target = File(into, name)
        open(BufferedInputStream(input.inputStream())).use { stream ->
            // Dentro pode haver um TAR (.tar.gz chega como .gz quando a extensão dupla não é reconhecida).
            return write(stream, target, 0, onBytes)
        }
    }

    /** Destino dentro de [root]; `null` para nomes que tentam escapar da pasta (zip-slip). */
    private fun safeTarget(root: File, name: String): File? {
        val clean = name.replace('\\', '/').trimStart('/')
        if (clean.isEmpty()) return null
        val target = File(root, clean)
        val rootPath = root.canonicalPath + File.separator
        return if (target.canonicalPath.startsWith(rootPath)) target else null
    }

    private suspend fun write(source: InputStream, target: File, totalSoFar: Long, onBytes: (Long) -> Unit, close: Boolean = true): Long {
        var total = totalSoFar
        val buffer = ByteArray(BUFFER_SIZE)
        target.outputStream().use { out ->
            while (true) {
                coroutineContext.ensureActive()
                val read = source.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                total += read
                if (total > maxExtractedBytes) throw IOException("O conteúdo compactado é grande demais para este aparelho")
                onBytes(total)
            }
        }
        if (close) source.close()
        return total
    }

    // Compactação -----------------------------------------------------------------------------

    suspend fun compress(files: List<File>, root: File, output: File, ext: String, level: CompressionLevel, onBytes: (Long) -> Unit) {
        when (ext) {
            "ZIP" -> compressZip(files, root, output, level, onBytes)
            "7Z" -> compress7z(files, root, output, level, onBytes)
            "TAR" -> TarArchiveOutputStream(output.outputStream().buffered()).use { writeTar(it, files, root, onBytes) }
            "TAR.GZ" -> {
                val params = GzipParameters().apply { compressionLevel = deflateLevel(level) }
                GzipCompressorOutputStream(output.outputStream().buffered(), params).use { gz ->
                    TarArchiveOutputStream(gz).use { writeTar(it, files, root, onBytes) }
                }
            }
            else -> throw IOException("Não sei gravar $ext")
        }
    }

    private suspend fun compressZip(files: List<File>, root: File, output: File, level: CompressionLevel, onBytes: (Long) -> Unit) {
        ZipArchiveOutputStream(output).use { zip ->
            zip.setLevel(deflateLevel(level))
            zip.setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode.AsNeeded)
            var total = 0L
            for (file in files) {
                coroutineContext.ensureActive()
                val entry = ZipArchiveEntry(file, relative(root, file))
                zip.putArchiveEntry(entry)
                total = copy(file, zip::write, total, onBytes)
                zip.closeArchiveEntry()
            }
        }
    }

    private suspend fun compress7z(files: List<File>, root: File, output: File, level: CompressionLevel, onBytes: (Long) -> Unit) {
        SevenZOutputFile(output).use { sevenZ ->
            sevenZ.setContentMethods(listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2, LZMA2Options(lzmaPreset(level)))))
            var total = 0L
            for (file in files) {
                coroutineContext.ensureActive()
                val entry = sevenZ.createArchiveEntry(file, relative(root, file))
                sevenZ.putArchiveEntry(entry)
                total = copy(file, { buffer, offset, length -> sevenZ.write(buffer, offset, length) }, total, onBytes)
                sevenZ.closeArchiveEntry()
            }
        }
    }

    private suspend fun writeTar(tar: TarArchiveOutputStream, files: List<File>, root: File, onBytes: (Long) -> Unit) {
        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
        tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
        var total = 0L
        for (file in files) {
            coroutineContext.ensureActive()
            tar.putArchiveEntry(TarArchiveEntry(file, relative(root, file)))
            total = copy(file, tar::write, total, onBytes)
            tar.closeArchiveEntry()
        }
        tar.finish()
    }

    private suspend fun copy(file: File, sink: (ByteArray, Int, Int) -> Unit, totalSoFar: Long, onBytes: (Long) -> Unit): Long {
        var total = totalSoFar
        val buffer = ByteArray(BUFFER_SIZE)
        file.inputStream().use { input ->
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                sink(buffer, 0, read)
                total += read
                onBytes(total)
            }
        }
        return total
    }

    private fun relative(root: File, file: File): String = file.relativeTo(root).path.replace(File.separatorChar, '/')

    private fun deflateLevel(level: CompressionLevel): Int = when (level) {
        CompressionLevel.FAST -> Deflater.BEST_SPEED
        CompressionLevel.NORMAL -> 6
        CompressionLevel.MAX -> Deflater.BEST_COMPRESSION
    }

    /** Preset LZMA2 (0–9): dicionário maior gasta memória; 9 usa ~700 MB e não cabe num celular. */
    private fun lzmaPreset(level: CompressionLevel): Int = when (level) {
        CompressionLevel.FAST -> 1
        CompressionLevel.NORMAL -> 5
        CompressionLevel.MAX -> 7
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 16
        const val DEFAULT_MAX_EXTRACTED_BYTES = 8L shl 30
    }
}
