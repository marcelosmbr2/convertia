package com.convertia.app.data

import com.convertia.app.data.engine.DownloadEngine
import com.convertia.app.domain.DownloadJob
import com.convertia.app.domain.DownloadRequest
import com.convertia.app.domain.DownloadStatus
import com.convertia.app.domain.LibraryItem
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
 * Fila de downloads: guarda os jobs, respeita o limite de downloads simultâneos (lido ao
 * vivo dos ajustes), pausa/retoma/tenta de novo e, ao concluir, põe o arquivo na
 * Biblioteca. Quem baixa de fato é o [DownloadEngine].
 *
 * Todas as mudanças acontecem no [scope], que deve ser confinado à main thread.
 */
class DownloadQueue(
    private val scope: CoroutineScope,
    private val engine: DownloadEngine,
    private val library: LibraryRepository,
    private val clock: Clock,
    private val concurrency: StateFlow<Int>,
) {
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())

    /** Mais recentes primeiro, como no mockup. */
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val _finished = MutableSharedFlow<DownloadJob>(extraBufferCapacity = 16)

    /** Emite cada download concluído. */
    val finished: SharedFlow<DownloadJob> = _finished.asSharedFlow()

    private val workers = mutableMapOf<Long, Job>()
    private var nextId = 1L

    init {
        scope.launch { concurrency.collect { schedule() } }
    }

    fun enqueue(request: DownloadRequest): Long {
        val id = nextId++
        _jobs.value = listOf(DownloadJob(id, request, DownloadStatus.QUEUED)) + _jobs.value
        schedule()
        return id
    }

    fun find(id: Long): DownloadJob? = _jobs.value.firstOrNull { it.id == id }

    fun pause(id: Long) {
        val job = find(id) ?: return
        if (!job.isActive) return
        stopWorker(id)
        update(id) { it.copy(status = DownloadStatus.PAUSED, speedMbps = 0.0) }
        schedule()
    }

    fun resume(id: Long) {
        if (find(id)?.status != DownloadStatus.PAUSED) return
        update(id) { it.copy(status = DownloadStatus.QUEUED) }
        schedule()
    }

    fun retry(id: Long) {
        if (find(id)?.status != DownloadStatus.FAILED) return
        update(id) { it.copy(status = DownloadStatus.QUEUED, progress = 0.0, error = null) }
        schedule()
    }

    fun remove(id: Long) {
        stopWorker(id)
        _jobs.value = _jobs.value.filterNot { it.id == id }
        schedule()
    }

    fun pauseAll() {
        _jobs.value.filter { it.isActive }.forEach { stopWorker(it.id) }
        _jobs.value = _jobs.value.map {
            if (it.isActive) it.copy(status = DownloadStatus.PAUSED, speedMbps = 0.0) else it
        }
    }

    fun resumeAll() {
        _jobs.value = _jobs.value.map { if (it.status == DownloadStatus.PAUSED) it.copy(status = DownloadStatus.QUEUED) else it }
        schedule()
    }

    fun clearDone() {
        _jobs.value = _jobs.value.filterNot { it.status == DownloadStatus.DONE }
    }

    internal fun seed(jobs: List<DownloadJob>) {
        workers.values.forEach { it.cancel() }
        workers.clear()
        _jobs.value = jobs
        nextId = (jobs.maxOfOrNull { it.id } ?: 0) + 1
        schedule()
    }

    private fun schedule() {
        val jobs = _jobs.value
        // Downloads marcados como ativos sem worker (por exemplo, dados de exemplo).
        jobs.filter { it.status == DownloadStatus.DOWNLOADING && it.id !in workers }.forEach(::startWorker)
        var active = jobs.count { it.status == DownloadStatus.DOWNLOADING }
        // A lista está do mais novo para o mais antigo; a fila anda do mais antigo.
        for (job in jobs.asReversed()) {
            if (active >= concurrency.value) break
            if (job.status != DownloadStatus.QUEUED) continue
            update(job.id) { it.copy(status = DownloadStatus.DOWNLOADING) }
            find(job.id)?.let(::startWorker)
            active++
        }
    }

    private fun startWorker(job: DownloadJob) {
        val id = job.id
        lateinit var worker: Job
        worker = scope.launch {
            try {
                engine.download(job).collect { progress ->
                    update(id) { it.copy(progress = progress.percent, speedMbps = progress.speedMbps) }
                }
                complete(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update(id) { it.copy(status = DownloadStatus.FAILED, speedMbps = 0.0, error = e.message ?: e.javaClass.simpleName) }
            } finally {
                if (workers[id] === worker) workers.remove(id)
                if (find(id)?.status != DownloadStatus.DOWNLOADING) schedule()
            }
        }
        workers[id] = worker
    }

    private fun complete(id: Long) {
        val now = clock.millis()
        update(id) { it.copy(status = DownloadStatus.DONE, progress = 100.0, speedMbps = 0.0, finishedAt = now) }
        val job = find(id) ?: return
        library.add(job.toLibraryItem(now))
        _finished.tryEmit(job)
    }

    private fun stopWorker(id: Long) {
        workers.remove(id)?.cancel()
    }

    private inline fun update(id: Long, transform: (DownloadJob) -> DownloadJob) {
        _jobs.value = _jobs.value.map { if (it.id == id) transform(it) else it }
    }

    private fun DownloadJob.toLibraryItem(now: Long) = LibraryItem(
        id = "download-$id-$now",
        title = request.title,
        format = request.container.uppercase(),
        detail = request.resolution,
        sizeBytes = (request.sizeMb * 1e6).toLong(),
        duration = request.duration,
        addedAt = now,
        kind = request.kind,
    )
}
