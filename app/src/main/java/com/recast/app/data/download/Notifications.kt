package com.recast.app.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.recast.app.MainActivity
import com.recast.app.R
import com.recast.app.domain.DownloadJob
import com.recast.app.domain.DownloadStatus
import com.recast.app.domain.Formatters
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

    /** Notificação fixa do serviço: o que está baixando e convertendo, com o progresso médio. */
    fun progress(context: Context, work: ActiveWork): Notification {
        val active = work.downloads
        val converting = work.conversions
        val downloading = active.filter { it.status == DownloadStatus.DOWNLOADING }
        val running = downloading.map { it.progress } + converting.map { it.progress }
        val percent = if (running.isEmpty()) 0 else running.average().roundToInt()
        val speed = downloading.sumOf { it.speedMbps }
        val title = when {
            active.isEmpty() && converting.size == 1 -> converting.first().name
            active.isEmpty() -> context.resources.getQuantityString(R.plurals.notification_converting, converting.size, converting.size)
            converting.isEmpty() && active.size == 1 -> active.first().request.title
            converting.isEmpty() -> context.resources.getQuantityString(R.plurals.notification_downloading, active.size, active.size)
            else -> context.getString(
                R.string.notification_mixed,
                context.resources.getQuantityString(R.plurals.notification_downloading, active.size, active.size),
                context.resources.getQuantityString(R.plurals.notification_converting, converting.size, converting.size).lowercase(),
            )
        }
        val text = when {
            downloading.isNotEmpty() -> context.getString(R.string.notification_progress, percent, Formatters.decimal(speed, 1))
            converting.isNotEmpty() -> context.getString(R.string.notification_conversion_progress, percent)
            else -> context.getString(R.string.queue_download_queued)
        }
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent, running.isEmpty())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open(context, MainActivity.queueIntent(context)))
            .build()
    }

    /** Atualiza a notificação fixa do serviço. */
    fun updateProgress(context: Context, work: ActiveWork) {
        try {
            NotificationManagerCompat.from(context).notify(PROGRESS_ID, progress(context, work))
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
