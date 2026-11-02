package com.convertia.app

import android.content.Context
import com.convertia.app.data.AppMessages
import com.convertia.app.data.CompletionNotifier
import com.convertia.app.data.ConversionQueue
import com.convertia.app.data.DownloadQueue
import com.convertia.app.data.LibraryRepository
import com.convertia.app.data.SampleData
import com.convertia.app.data.SettingsRepository
import com.convertia.app.data.engine.MediaInspector
import com.convertia.app.data.engine.SimulatedConversionEngine
import com.convertia.app.data.engine.ytdlp.YtDlp
import com.convertia.app.data.engine.ytdlp.YtDlpDownloadEngine
import com.convertia.app.data.engine.ytdlp.YtDlpMediaInspector
import com.convertia.app.data.settingsDataStore
import com.convertia.app.data.storage.MediaSaver
import com.convertia.app.domain.AppSettings
import com.convertia.app.domain.Preset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock

/**
 * Dependências do app (DI manual). Vive enquanto o processo vive, então a fila continua
 * andando quando o usuário sai de uma tela ou baixa algo pelo menu Compartilhar.
 */
class AppContainer(context: Context) {

    val clock: Clock = Clock.systemDefaultZone()

    /** Escopo das filas e motores; confinado à main thread. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsRepository = SettingsRepository(context.applicationContext.settingsDataStore)

    /** `null` até o DataStore carregar (evita mostrar o onboarding por um instante). */
    val settingsOrNull: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<AppSettings> =
        settingsOrNull.map { it ?: AppSettings() }.stateIn(appScope, SharingStarted.Eagerly, AppSettings())

    val messages = AppMessages()
    val library = LibraryRepository()
    val presets: List<Preset> = SampleData.presets

    /** yt-dlp + ffmpeg empacotados; inicializa em segundo plano assim que o app abre. */
    val ytDlp = YtDlp(context.applicationContext)
    val mediaSaver = MediaSaver(context.applicationContext)
    val mediaInspector: MediaInspector = YtDlpMediaInspector(ytDlp)

    val downloads = DownloadQueue(
        scope = appScope,
        engine = YtDlpDownloadEngine(ytDlp, mediaSaver, File(context.cacheDir, "downloads")),
        library = library,
        clock = clock,
        concurrency = settings.map { it.concurrentDownloads }.stateIn(appScope, SharingStarted.Eagerly, settings.value.concurrentDownloads),
    )

    val conversions = ConversionQueue(
        scope = appScope,
        engine = SimulatedConversionEngine(),
        library = library,
        clock = clock,
        parallelism = settings.map { it.parallelConversions }.stateIn(appScope, SharingStarted.Eagerly, settings.value.parallelConversions),
        hardwareAcceleration = settings.map { it.hardwareAcceleration }.stateIn(appScope, SharingStarted.Eagerly, settings.value.hardwareAcceleration),
    )

    private val _pendingLink = MutableStateFlow<String?>(null)

    /** Link que chegou de fora (menu Compartilhar) esperando a tela Baixar inspecionar. */
    val pendingLink: StateFlow<String?> = _pendingLink.asStateFlow()

    fun offerLink(url: String) {
        _pendingLink.value = url
    }

    fun consumeLink(): String? = _pendingLink.value.also { _pendingLink.value = null }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        appScope.launch { settingsRepository.update(transform) }
    }

    init {
        CompletionNotifier(
            scope = appScope,
            downloads = downloads,
            conversions = conversions,
            notificationsEnabled = settings.map { it.notifications }.stateIn(appScope, SharingStarted.Eagerly, true),
            messages = messages,
        )
        appScope.launch {
            // Erros aqui reaparecem (com mensagem) na primeira inspeção ou download.
            runCatching { ytDlp.prepare() }
        }
    }
}
