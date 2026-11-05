package com.recast.app.data.convert

import com.recast.app.domain.ConversionItem
import java.io.File

/** Uma conversão pronta para rodar: a origem já copiada e a pasta onde as saídas vão. */
data class ConversionJob(
    val item: ConversionItem,
    /** Cópia local da origem (`src.<ext>`). */
    val input: File,
    /** Pasta vazia para as saídas; os nomes dos arquivos são os nomes finais. */
    val outputDir: File,
    val hardwareAcceleration: Boolean,
) {
    /** Saída principal, com o nome final ("IMG_4021.jpg"). */
    val output: File get() = File(outputDir, item.outputName)

    /** Saída numerada, para conversões com vários arquivos ("doc-1.png"). */
    fun output(index: Int): File {
        val base = item.name.substringBeforeLast('.', item.name)
        return File(outputDir, "$base-$index.${item.target.ext.lowercase()}")
    }
}

/** Progresso de um conversor: percentual (0–100) e, quando ele souber, segundos restantes. */
fun interface ProgressListener {
    fun onProgress(percent: Double, etaSeconds: Int?)
}

/**
 * Um motor de conversão de arquivos. Recebe a origem já copiada, grava as saídas em
 * [ConversionJob.outputDir] e as devolve na ordem (a primeira é a principal). Deve respeitar
 * o cancelamento da coroutine: o que ficou pela metade é apagado por quem chamou.
 */
interface FileConverter {
    fun supports(item: ConversionItem): Boolean

    suspend fun convert(job: ConversionJob, progress: ProgressListener): List<File>
}
