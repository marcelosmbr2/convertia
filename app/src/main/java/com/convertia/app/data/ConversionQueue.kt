package com.convertia.app.data

import com.convertia.app.data.engine.ConversionEngine
import com.convertia.app.data.engine.ConversionProgress
import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.ConversionOptions
import com.convertia.app.domain.ConversionStatus
import com.convertia.app.domain.FormatCatalog
import com.convertia.app.domain.HistoryEntry
import com.convertia.app.domain.OutputFormat
import com.convertia.app.domain.Preset
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
 * ajustes. Ao concluir cada arquivo, grava no Histórico; quando não sobra nada, emite
 * [allFinished] e para. Quem converte de fato é o [ConversionEngine].
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

    /** Emite quando a fila esvazia depois de converter tudo. */
    val allFinished: SharedFlow<Unit> = _allFinished.asSharedFlow()

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
        val remaining = _items.value.filterNot { it.id == id }
        _items.value = remaining
        _running.value = _running.value && remaining.any { it.status != ConversionStatus.DONE }
        schedule()
    }

    fun clearDone() {
        _items.value = _items.value.filterNot { it.status == ConversionStatus.DONE }
    }

    /** Converter/Retomar. Não faz nada se não houver arquivo pendente. */
    fun start() {
        if (_items.value.none { it.isPending }) return
        _running.value = true
        schedule()
    }

    /** Pausa tudo: as conversões em andamento mantêm o progresso. */
    fun pause() {
        _running.value = false
        workers.values.forEach { it.cancel() }
        workers.clear()
    }

    fun setTarget(id: String, target: OutputFormat) = updateEditable(id) { it.copy(target = target).resetIfDone() }

    fun updateOptions(id: String, transform: (ConversionOptions) -> ConversionOptions) =
        updateEditable(id) { it.copy(options = transform(it.options)) }

    /**
     * Define [target] em todos os arquivos que podem virar esse formato e ainda não estão
     * nele. Devolve quantos mudaram.
     */
    fun setTargetForAll(target: OutputFormat): Int = updateMany { item ->
        item.status != ConversionStatus.CONVERTING &&
            target.category in FormatCatalog.compatible.getValue(item.category) &&
            item.ext != target.ext.lowercase()
    }.apply(target = { target })

    /** Copia formato e ajustes de [sourceId] para os arquivos da mesma categoria. */
    fun applyToSameCategory(sourceId: String): Int {
        val source = find(sourceId) ?: return 0
        return updateMany { it.category == source.category && it.status != ConversionStatus.CONVERTING }
            .apply(target = { source.target }, options = { source.options }, keepDone = sourceId)
    }

    /** Aplica a predefinição aos arquivos com extensão correspondente. */
    fun applyPreset(preset: Preset): Int {
        var count = 0
        _items.value = _items.value.map { item ->
            if (item.status == ConversionStatus.CONVERTING || item.ext !in preset.sources) return@map item
            count++
            item.copy(
                target = preset.target,
                status = ConversionStatus.READY,
                progress = 0.0,
                outputBytes = 0,
                options = item.options.withQuality(preset.quality),
            )
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

    private val ConversionItem.isPending: Boolean
        get() = status == ConversionStatus.READY || status == ConversionStatus.CONVERTING

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
                        is ConversionProgress.Finished -> complete(id, progress.outputBytes)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (workers[id] === worker) workers.remove(id)
            }
        }
        workers[id] = worker
    }

    private fun complete(id: String, outputBytes: Long) {
        update(id) { it.copy(status = ConversionStatus.DONE, progress = 100.0, outputBytes = outputBytes) }
        _eta.value = _eta.value - id
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
            ),
        )
        val wasRunning = _running.value
        schedule()
        if (wasRunning && !_running.value) _allFinished.tryEmit(Unit)
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
                val changed = item.copy(
                    target = target(item),
                    options = options?.invoke(item) ?: item.options,
                )
                if (item.id == keepDone) changed else changed.resetIfDone()
            }
            schedule()
            return count
        }
    }
}
