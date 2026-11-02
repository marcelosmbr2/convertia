package com.convertia.app.data.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.convertia.app.ConvertiaApplication
import com.convertia.app.domain.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano enquanto houver download baixando (não fica ligado só
 * esperando Wi-Fi): mantém o processo vivo com o
 * app fechado e mostra o progresso na notificação. Os downloads em si rodam na fila do
 * [com.convertia.app.AppContainer]; o serviço só observa e se encerra quando ela esvazia.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as ConvertiaApplication).container
        val active = container.downloads.jobs.value.filter { it.isActive }
        ServiceCompat.startForeground(
            this,
            Notifications.PROGRESS_ID,
            Notifications.progress(this, active),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (observer == null) {
            observer = scope.launch {
                container.downloads.jobs
                    .map { jobs -> jobs.filter { it.isActive } }
                    .sample(NOTIFICATION_INTERVAL_MS)
                    .collect { jobs ->
                        if (jobs.none { it.status == DownloadStatus.DOWNLOADING }) {
                            stop()
                        } else {
                            Notifications.updateProgress(this@DownloadService, jobs)
                        }
                    }
            }
        }
        if (active.none { it.status == DownloadStatus.DOWNLOADING }) stop()
        return START_NOT_STICKY
    }

    /** Android 15+: o tempo de "sincronização de dados" do dia acabou; o sistema pede para parar. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        (application as ConvertiaApplication).container.downloads.pauseAll()
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

    companion object {
        private const val NOTIFICATION_INTERVAL_MS = 1_000L

        /** Liga o serviço; só funciona com o app em primeiro plano (que é quando se pede download). */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            }
        }
    }
}
