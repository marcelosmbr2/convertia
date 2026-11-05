package com.recast.app.data

import com.recast.app.data.engine.ConversionEngine
import com.recast.app.data.engine.ConversionProgress
import com.recast.app.domain.ConversionItem
import com.recast.app.domain.ConversionOptions
import com.recast.app.domain.ConversionStatus
import com.recast.app.domain.HistoryEntry
import com.recast.app.domain.OutputFormat
import com.recast.app.domain.Preset
import com.recast.app.domain.SavedFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * A lista do Converter é a própria fila de conversão: um interruptor global [running]
 * (o botão Converter/Pausar/Retomar) e até N conversões ao mesmo tempo, lido ao vivo dos
 * ajustes. Ao concluir cada arquivo, grava no Histórico; quando não sobra nada pendente,
 * emite [allFinished] e para. Quem converte de fato é o [ConversionEngine]; um erro dele
 * marca o item como falho e a fila segue para o próximo.
 *
 * Todas as mudanças acontecem no [scope], que deve ser confinado à main thread.
 */
class ConversionQueue(
    private val scope: CoroutineScope,
    private val engine: ConversionEngine,
    private val library: LibraryRepository,
    private val clock: Clock,
    private val parallelism: StateFlow<Int>,
    private val hardwareAcceleration: StateFlow<Boolean>,
) {
    private val _items = MutableStateFlow<List<ConversionItem>>(emptyList())
    val items: StateFlow<List<ConversionItem>> = _items.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** ETA por item, em segundos, quando o motor informa. */
    private val _eta = MutableStateFlow<Map<String, Int>>(emptyMap())
    val eta: StateFlow<Map<String, Int>> = _eta.asStateFlow()

    private val _allFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Emite quando a fila para depois de concluir ao menos um arquivo. */
    val allFinished: SharedFlow<Unit> = _allFinished.asSharedFlow()

    private val _removed = MutableSharedFlow<ConversionItem>(extraBufferCapacity = 16)

    /** Itens tirados da lista (para apagar o que sobrou na pasta de trabalho). */
    val removed: SharedFlow<ConversionItem> = _removed.asSharedFlow()

    private val workers = mutableMapOf<String, Job>()
    private var nextId = 1L

    init {
        scope.launch { parallelism.collect { schedule() } }
    }

    fun newId(): String = "file-${nextId++}"

    fun find(id: String): ConversionItem? = _items.value.firstOrNull { it.id == id }

    fun add(items: List<ConversionItem>) {
        if (items.isEmpty()) return
        _items.value = _items.value + items
        schedule()
    }

    fun remove(id: String) {
        stopWorker(id)
        val item = find(id)
        val remaining = _items.value.filterNot { it.id == id }
        _items.value = remaining
        _running.value = _running.value && remaining.any { it.isPending }
        item?.let { _removed.tryEmit(it) }
        schedule()
    }

    fun clearDone() {
        val (done, rest) = _items.value.partition { it.status == ConversionStatus.DONE }
        _items.value = rest
        done.forEach { _removed.tryEmit(it) }
    }

    /** Converter/Retomar. Não faz nada se não houver arquivo pendente. */
    fun start() {
        if (_items.value.none { it.isPending }) return
        _running.value = true
        schedule()
    }

    /**
     * Pausa tudo. Os arquivos em andamento continuam marcados como "convertendo", mas o
     * progresso volta a zero: o motor real recomeça o arquivo ao retomar.
     */
    fun pause() {
        _running.value = false
        workers.values.forEach { it.cancel() }
        workers.clear()
        _items.value = _items.value.map {
            if (it.status == ConversionStatus.CONVERTING) it.copy(progress = 0.0) else it
        }
        _eta.value = emptyMap()
    }

    /** Tenta de novo um arquivo que falhou (liga a fila se estava parada). */
    fun retry(id: String) {
        if (find(id)?.status != ConversionStatus.FAILED) return
        update(id) { it.resetIfDone() }
        _running.value = true
        schedule()
    }

    fun setTarget(id: String, target: OutputFormat) = updateEditable(id) { it.withTarget(target) }

    fun updateOptions(id: String, transform: (ConversionOptions) -> ConversionOptions) =
        updateEditable(id) { it.copy(options = transform(it.options)) }

    /**
     * Define [target] em todos os arquivos que podem virar esse formato e ainda não estão
     * nele. Devolve quantos mudaram.
     */
    fun setTargetForAll(target: OutputFormat): Int =
        updateMany { it.status != ConversionStatus.CONVERTING && it.supports(target) }
            .apply(target = { target })

    /** Copia formato e ajustes de [sourceId] para os arquivos da mesma categoria que aceitam o formato. */
    fun applyToSameCategory(sourceId: String): Int {
        val source = find(sourceId) ?: return 0
        return updateMany {
            it.category == source.category && it.status != ConversionStatus.CONVERTING && (it.id == sourceId || it.supports(source.target))
        }.apply(target = { source.target }, options = { source.options }, keepDone = sourceId)
    }

    /** Aplica a predefinição aos arquivos com extensão correspondente. */
    fun applyPreset(preset: Preset): Int {
        var count = 0
        _items.value = _items.value.map { item ->
            if (item.status == ConversionStatus.CONVERTING || item.ext !in preset.sources || !item.supports(preset.target)) return@map item
            count++
            item.withTarget(preset.target).copy(options = item.options.withQuality(preset.quality)).resetIfDone()
        }
        schedule()
        return count
    }

    internal fun seed(items: List<ConversionItem>, running: Boolean) {
        pause()
        _items.value = items
        _running.value = running
        schedule()
    }

    private fun schedule() {
        if (!_running.value) return
        val items = _items.value
        items.filter { it.status == ConversionStatus.CONVERTING && it.id !in workers }.forEach(::startWorker)
        var active = items.count { it.status == ConversionStatus.CONVERTING }
        for (item in items) {
            if (active >= parallelism.value) break
            if (item.status != ConversionStatus.READY) continue
            update(item.id) { it.copy(status = ConversionStatus.CONVERTING, progress = 0.0) }
            find(item.id)?.let(::startWorker)
            active++
        }
        if (items.none { it.isPending }) {
            _running.value = false
        }
    }

    private fun startWorker(item: ConversionItem) {
        val id = item.id
        lateinit var worker: Job
        worker = scope.launch {
            try {
                engine.convert(item, hardwareAcceleration.value).collect { progress ->
                    when (progress) {
                        is ConversionProgress.Running -> {
                            update(id) { it.copy(progress = progress.percent) }
                            progress.etaSeconds?.let { eta -> _eta.value = _eta.value + (id to eta) }
                        }
                        is ConversionProgress.Finished -> complete(id, progress.files)
                    }
                }
                if (find(id)?.status == ConversionStatus.CONVERTING) fail(id, NO_OUTPUT_ERROR)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(id, e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
            } finally {
                if (workers[id] === worker) workers.remove(id)
            }
        }
        workers[id] = worker
    }

    private fun complete(id: String, files: List<SavedFile>) {
        val outputBytes = files.sumOf { it.sizeBytes }
        update(id) { it.copy(status = ConversionStatus.DONE, progress = 100.0, outputBytes = outputBytes, outputs = files, error = null) }
        val item = find(id) ?: return
        library.addHistory(
            HistoryEntry(
                id = "history-$id-${clock.millis()}",
                name = item.name,
                fromExt = item.ext.uppercase(),
                toExt = item.target.ext,
                inputBytes = item.sizeBytes,
                outputBytes = outputBytes,
                at = clock.millis(),
                uri = files.firstOrNull()?.uri,
                mimeType = files.firstOrNull()?.mimeType,
                fileCount = files.size,
            ),
        )
        settle(id)
    }

    private fun fail(id: String, message: String) {
        update(id) { it.copy(status = ConversionStatus.FAILED, error = message) }
        settle(id)
    }

    /** Depois que um item termina (bem ou mal): libera a vaga e avisa se a fila esvaziou. */
    private fun settle(id: String) {
        _eta.value = _eta.value - id
        val wasRunning = _running.value
        schedule()
        if (wasRunning && !_running.value && _items.value.any { it.status == ConversionStatus.DONE }) {
            _allFinished.tryEmit(Unit)
        }
    }

    private fun stopWorker(id: String) {
        workers.remove(id)?.cancel()
        _eta.value = _eta.value - id
    }

    private inline fun update(id: String, transform: (ConversionItem) -> ConversionItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }

    private inline fun updateEditable(id: String, transform: (ConversionItem) -> ConversionItem) {
        if (find(id)?.status == ConversionStatus.CONVERTING) return
        update(id, transform)
    }

    private fun updateMany(predicate: (ConversionItem) -> Boolean) = BulkUpdate(predicate)

    private inner class BulkUpdate(private val predicate: (ConversionItem) -> Boolean) {
        /** Aplica nos itens que passam no filtro; itens concluídos voltam a "pronto". */
        fun apply(
            target: (ConversionItem) -> OutputFormat,
            options: ((ConversionItem) -> ConversionOptions)? = null,
            keepDone: String? = null,
        ): Int {
            var count = 0
            _items.value = _items.value.map { item ->
                if (!predicate(item)) return@map item
                count++
                val changed = item.copy(options = options?.invoke(item) ?: item.options)
                if (item.id == keepDone) changed.copy(target = target(item)) else changed.withTarget(target(item))
            }
            schedule()
            return count
        }
    }

    private companion object {
        const val NO_OUTPUT_ERROR = "A conversão terminou sem gerar arquivo"
    }
}
