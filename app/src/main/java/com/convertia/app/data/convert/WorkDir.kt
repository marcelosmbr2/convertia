package com.convertia.app.data.convert

import android.content.ContentResolver
import androidx.core.net.toUri
import com.convertia.app.data.LibraryRepository
import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.ConversionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Pasta de trabalho de cada conversão, em `noBackupFilesDir/convert/<id>/` (o sistema pode
 * limpar o `cacheDir` no meio de um arquivo grande). A origem é copiada para `src.<ext>`,
 * porque os processos nativos (ffmpeg, LibreOffice) não leem `content://`; as saídas
 * ficam em `out/` até serem gravadas em `Download/Convertia`.
 */
class WorkDir(
    private val root: File,
    private val resolver: ContentResolver,
    private val library: LibraryRepository,
) {
    fun dirFor(item: ConversionItem): File = File(root, item.id)

    fun outputDirFor(item: ConversionItem): File = File(dirFor(item), "out")

    /**
     * Copia a origem para a pasta do item, em blocos, respeitando o cancelamento. Grava
     * num arquivo temporário e renomeia no fim, para uma cópia interrompida nunca passar
     * por completa. [onProgress] recebe a fração copiada (0–1).
     */
    suspend fun stageInput(item: ConversionItem, onProgress: (Double) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val dir = dirFor(item).apply { mkdirs() }
        outputDirFor(item).apply { deleteRecursively(); mkdirs() }
        val target = File(dir, "src." + item.ext.ifEmpty { "bin" })
        if (target.isFile && target.length() == item.sizeBytes && item.sizeBytes > 0) return@withContext target

        checkFreeSpace(item)
        val uri = sourceUri(item)
        val temp = File(dir, target.name + ".tmp")
        try {
            val input = resolver.openInputStream(uri.toUri())
                ?: throw IOException("Não foi possível abrir o arquivo de origem")
            input.use { source ->
                temp.outputStream().use { sink ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        copied += read
                        if (item.sizeBytes > 0) onProgress((copied.toDouble() / item.sizeBytes).coerceIn(0.0, 1.0))
                    }
                }
            }
            if (!temp.renameTo(target)) throw IOException("Não foi possível preparar o arquivo de origem")
            target
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    /** Apaga tudo que sobrou de um item (concluído, falho ou removido). */
    fun discard(item: ConversionItem) {
        dirFor(item).deleteRecursively()
    }

    /** Na abertura do app: a lista do Converter não é salva, então nada aqui tem dono. */
    fun cleanAll() {
        root.deleteRecursively()
    }

    private fun sourceUri(item: ConversionItem): String = when (val source = item.source) {
        is ConversionSource.Document -> source.uri
        is ConversionSource.Library -> library.find(source.itemId)?.uri
            ?: throw IOException("O arquivo saiu da Biblioteca")
        ConversionSource.Sample -> throw IOException("Arquivo de exemplo, sem conteúdo")
    }

    private fun checkFreeSpace(item: ConversionItem) {
        root.mkdirs()
        // Entrada copiada + saída, com folga.
        val needed = item.sizeBytes * 2 + MIN_FREE_BYTES
        if (root.usableSpace in 1 until needed) {
            throw IOException("Espaço insuficiente no aparelho para converter este arquivo")
        }
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 20
        const val MIN_FREE_BYTES = 64L shl 20
    }
}
