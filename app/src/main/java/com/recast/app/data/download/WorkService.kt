package com.recast.app.data.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.recast.app.AppContainer
import com.recast.app.RecastApplication
import com.recast.app.domain.ConversionItem
import com.recast.app.domain.ConversionStatus
import com.recast.app.domain.DownloadJob
import com.recast.app.domain.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/** O que está em andamento, para a notificação e para decidir quando o serviço para. */
data class ActiveWork(val downloads: List<DownloadJob>, val conversions: List<ConversionItem>) {
    val isBusy: Boolean
        get() = downloads.any { it.status == DownloadStatus.DOWNLOADING } || conversions.isNotEmpty()

    companion object {
        fun of(container: AppContainer): ActiveWork = of(
            container.downloads.jobs.value,
            container.conversions.items.value,
            container.conversions.running.value,
        )

        fun of(jobs: List<DownloadJob>, items: List<ConversionItem>, running: Boolean) = ActiveWork(
            downloads = jobs.filter { it.isActive },
            conversions = if (running) items.filter { it.status == ConversionStatus.CONVERTING } else emptyList(),
        )
    }
}

/**
 * Serviço em primeiro plano enquanto houver download baixando ou conversão rodando (não
 * fica ligado só esperando Wi-Fi): mantém o processo vivo com o app fechado e mostra o
 * progresso na notificação. O trabalho em si roda nas filas do [AppContainer]; o serviço
 * só observa e se encerra quando elas esvaziam.
 */
class WorkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as RecastApplication).container
        val work = ActiveWork.of(container)
        ServiceCompat.startForeground(this, Notifications.PROGRESS_ID, Notifications.progress(this, work), foregroundType())
        if (observer == null) {
            observer = scope.launch {
                combine(container.downloads.jobs, container.conversions.items, container.conversions.running, ActiveWork::of)
                    .sample(NOTIFICATION_INTERVAL_MS)
                    .collect { current ->
                        if (current.isBusy) Notifications.updateProgress(this@WorkService, current) else stop()
                    }
            }
        }
        if (!work.isBusy) stop()
        return START_NOT_STICKY
    }

    /** Android 15+: o tempo diário desse tipo de serviço acabou; o sistema pede para parar. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        val container = (application as RecastApplication).container
        container.downloads.pauseAll()
        container.conversions.pause()
        stop()
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Download é "sincronização de dados"; conversão é "processamento de mídia" (Android 15+). */
    private fun foregroundType(): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        else -> 0
    }

    companion object {
        private const val NOTIFICATION_INTERVAL_MS = 1_000L

        /** Liga o serviço; só funciona com o app em primeiro plano (que é quando se pede o trabalho). */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, WorkService::class.java))
            }
        }
    }
}
