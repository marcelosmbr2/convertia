package com.convertia.app.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.convertia.app.data.ConversionQueue
import com.convertia.app.data.DownloadQueue
import com.convertia.app.domain.ConversionStatus
import com.convertia.app.ui.convertiaContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class QueueViewModel(
    private val downloads: DownloadQueue,
    private val conversions: ConversionQueue,
) : ViewModel() {

    val overview: StateFlow<QueueOverview> = combine(
        downloads.jobs,
        conversions.items,
        conversions.running,
        downloads.allowed,
    ) { jobs, items, running, allowed ->
        QueueOverview.build(jobs, items, running, allowed)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        QueueOverview.build(downloads.jobs.value, conversions.items.value, conversions.running.value, downloads.allowed.value),
    )

    fun pause(row: QueueRow) = when (row) {
        is QueueRow.Download -> downloads.pause(row.job.id)
        is QueueRow.Conversion -> conversions.pause()
    }

    fun resume(row: QueueRow) = when (row) {
        is QueueRow.Download -> downloads.resume(row.job.id)
        is QueueRow.Conversion -> conversions.start()
    }

    fun retry(row: QueueRow) {
        if (row is QueueRow.Download) downloads.retry(row.job.id)
    }

    fun remove(row: QueueRow) = when (row) {
        is QueueRow.Download -> downloads.remove(row.job.id)
        is QueueRow.Conversion -> conversions.remove(row.item.id)
    }

    /** "Pausar tudo" / "Retomar tudo" da seção Em andamento. */
    fun toggleAll() {
        val running = downloads.jobs.value.any { it.isActive } || conversions.running.value
        if (running) {
            conversions.pause()
            downloads.pauseAll()
        } else {
            // Como no mockup: retoma conversões só se alguma tinha começado.
            if (conversions.items.value.any { it.status == ConversionStatus.CONVERTING }) conversions.start()
            downloads.resumeAll()
        }
    }

    /** "Limpar" da seção Concluídos: tira downloads e conversões terminados. */
    fun clearDone() {
        downloads.clearDone()
        conversions.clearDone()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = convertiaContainer()
                QueueViewModel(c.downloads, c.conversions)
            }
        }
    }
}
