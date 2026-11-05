package com.recast.app.data.engine.archive

import kotlinx.coroutines.ensureActive
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/** Lê RAR (4 e 5) com a libarchive, extraindo cada entrada para a pasta pedida. */
class RarExtractor(private val maxExtractedBytes: Long = 8L shl 30) : ArchiveConverter.Extractor {

    override suspend fun extract(archive: File, into: File, onBytes: (Long) -> Unit): Long {
        var total = 0L
        val handle = try {
            Archive.readNew()
        } catch (e: ArchiveException) {
            throw IOException("Não foi possível iniciar o leitor de RAR")
        }
        try {
            Archive.readSupportFilterAll(handle)
            Archive.readSupportFormatRar(handle)
            Archive.readSupportFormatRar5(handle)
            Archive.readOpenFileName(handle, archive.path.toByteArray(), BLOCK_SIZE)
            val buffer = ByteBuffer.allocateDirect(BLOCK_SIZE.toInt())
            while (true) {
                coroutineContext.ensureActive()
                val entry = Archive.readNextHeader(handle)
                if (entry == 0L) break
                val name = ArchiveEntry.pathnameUtf8(entry) ?: continue
                val type = ArchiveEntry.filetype(entry)
                val target = safeTarget(into, name) ?: run { Archive.readDataSkip(handle); continue }
                when (type) {
                    ArchiveEntry.AE_IFDIR -> target.mkdirs()
                    ArchiveEntry.AE_IFREG -> {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out ->
                            while (true) {
                                coroutineContext.ensureActive()
                                buffer.clear()
                                Archive.readData(handle, buffer)
                                buffer.flip()
                                if (!buffer.hasRemaining()) break
                                val chunk = ByteArray(buffer.remaining())
                                buffer.get(chunk)
                                out.write(chunk)
                                total += chunk.size
                                if (total > maxExtractedBytes) throw IOException("O conteúdo compactado é grande demais para este aparelho")
                                onBytes(total)
                            }
                        }
                    }
                    else -> Archive.readDataSkip(handle)
                }
            }
        } catch (e: ArchiveException) {
            val message = e.message.orEmpty()
            throw IOException(
                when {
                    "passphrase" in message.lowercase() || "encrypted" in message.lowercase() -> "Compactado protegido por senha"
                    else -> "Não foi possível ler o RAR (${message.ifEmpty { "arquivo danificado" }})"
                },
            )
        } finally {
            runCatching { Archive.readFree(handle) }
        }
        return total
    }

    private fun safeTarget(root: File, name: String): File? {
        val clean = name.replace('\\', '/').trimStart('/')
        if (clean.isEmpty()) return null
        val target = File(root, clean)
        return if (target.canonicalPath.startsWith(root.canonicalPath + File.separator)) target else null
    }

    private companion object {
        const val BLOCK_SIZE = 1L shl 16
    }
}
