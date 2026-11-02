package com.convertia.app.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.convertia.app.MainActivity
import com.convertia.app.R
import com.convertia.app.domain.DownloadJob
import com.convertia.app.domain.DownloadStatus
import com.convertia.app.domain.Formatters
import kotlin.math.roundToInt

/** Canais e notificações do app (progresso dos downloads e avisos de conclusão). */
object Notifications {

    const val PROGRESS_ID = 1
    private const val CHANNEL_PROGRESS = "downloads"
    private const val CHANNEL_DONE = "completed"
    private var nextId = 100

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_PROGRESS, context.getString(R.string.notification_channel_progress), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.notification_channel_done), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /** Notificação fixa do serviço: quantos estão baixando e o progresso médio. */
    fun progress(context: Context, active: List<DownloadJob>): Notification {
        val downloading = active.filter { it.status == DownloadStatus.DOWNLOADING }
        val percent = if (downloading.isEmpty()) 0 else downloading.map { it.progress }.average().roundToInt()
        val speed = downloading.sumOf { it.speedMbps }
        val title = if (active.size == 1) {
            active.first().request.title
        } else {
            context.resources.getQuantityString(R.plurals.notification_downloading, active.size, active.size)
        }
        val text = if (downloading.isEmpty()) {
            context.getString(R.string.queue_download_queued)
        } else {
            context.getString(R.string.notification_progress, percent, Formatters.decimal(speed, 1))
        }
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent, downloading.isEmpty())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open(context, MainActivity.queueIntent(context)))
            .build()
    }

    /** Atualiza a notificação fixa do serviço. */
    fun updateProgress(context: Context, active: List<DownloadJob>) {
        try {
            NotificationManagerCompat.from(context).notify(PROGRESS_ID, progress(context, active))
        } catch (_: SecurityException) {
            // Sem permissão de notificação: o serviço continua, só sem atualizar o texto.
        }
    }

    /** "Download concluído" com o nome do arquivo; toque abre a Biblioteca. */
    fun downloadFinished(context: Context, job: DownloadJob) = post(
        context,
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.toast_download_finished))
            .setContentText(job.request.title)
            .setAutoCancel(true)
            .setContentIntent(open(context, MainActivity.libraryIntent(context, history = false)))
            .build(),
    )

    fun conversionsFinished(context: Context) = post(
        context,
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.toast_conversions_finished))
            .setAutoCancel(true)
            .setContentIntent(open(context, MainActivity.libraryIntent(context, history = true)))
            .build(),
    )

    private fun post(context: Context, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(nextId++, notification)
        } catch (_: SecurityException) {
            // Permissão revogada entre a checagem e o aviso.
        }
    }

    private fun open(context: Context, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            intent.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
