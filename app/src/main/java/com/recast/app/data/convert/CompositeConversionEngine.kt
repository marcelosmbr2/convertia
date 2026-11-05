package com.recast.app.data.convert

import com.recast.app.data.LibraryRepository
import com.recast.app.data.engine.ConversionEngine
import com.recast.app.data.engine.ConversionProgress
import com.recast.app.data.storage.MediaSaver
import com.recast.app.domain.AppSettings
import com.recast.app.domain.ConversionItem
import com.recast.app.domain.ConversionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * O motor de conversão do app: copia a origem para a pasta de trabalho, escolhe o
 * [FileConverter] que atende o par origem → destino, grava as saídas em `Download/Recast`
 * (respeitando o ajuste de conflito) e, se "Manter originais" estiver desligado, apaga a
 * origem quando ela é da Biblioteca. Pares sem conversor caem no [fallback], se houver.
 */
class CompositeConversionEngine(
    private val workDir: WorkDir,
    private val saver: MediaSaver,
    private val library: LibraryRepository,
    private val settings: StateFlow<AppSettings>,
    private val converters: List<FileConverter>,
    private val fallback: ConversionEngine? = null,
) : ConversionEngine {

    override fun convert(item: ConversionItem, hardwareAcceleration: Boolean): Flow<ConversionProgress> {
        val converter = converters.firstOrNull { it.supports(item) }
            ?: return fallback?.convert(item, hardwareAcceleration)
                ?: throw IOException("Este aparelho não converte ${item.ext.uppercase()} em ${item.target.ext}")
        return channelFlow {
            try {
                send(ConversionProgress.Running(0.0, null))
                val input = workDir.stageInput(item) { fraction -> trySend(ConversionProgress.Running(fraction * STAGING_PERCENT, null)) }
                val job = ConversionJob(item, input, workDir.outputDirFor(item), hardwareAcceleration)
                val outputs = converter.convert(job) { percent, eta ->
                    trySend(ConversionProgress.Running(STAGING_PERCENT + percent.coerceIn(0.0, 100.0) * (100 - STAGING_PERCENT) / 100, eta))
                }
                if (outputs.isEmpty()) throw IOException("A conversão terminou sem gerar arquivo")
                // Depois de gravar, pausar não pode deixar o arquivo pela metade nem duplicá-lo.
                val saved = withContext(NonCancellable) {
                    val policy = settings.value.conflictPolicy
                    outputs.map { saver.save(it, policy) }
                }
                if (!settings.value.keepOriginals) deleteOriginal(item)
                send(ConversionProgress.Finished(saved))
            } finally {
                workDir.discard(item)
            }
        }.flowOn(Dispatchers.IO)
    }

    /** Só apaga o que é do app: arquivos escolhidos no seletor vêm com permissão de leitura apenas. */
    private suspend fun deleteOriginal(item: ConversionItem) {
        val source = item.source as? ConversionSource.Library ?: return
        withContext(Dispatchers.Main.immediate) { library.remove(source.itemId) }
    }

    private companion object {
        /** Parte da barra reservada à cópia da origem. */
        const val STAGING_PERCENT = 3.0
    }
}
