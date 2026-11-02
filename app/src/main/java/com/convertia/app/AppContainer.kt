package com.convertia.app

import android.content.Context
import com.convertia.app.data.AppMessages
import com.convertia.app.data.AppSnapshot
import com.convertia.app.data.AppStateStore
import com.convertia.app.data.CompletionNotifier
import com.convertia.app.data.ConversionQueue
import com.convertia.app.data.DownloadQueue
import com.convertia.app.data.LibraryRepository
import com.convertia.app.data.SampleData
import com.convertia.app.data.SettingsRepository
import com.convertia.app.data.convert.CompositeConversionEngine
import com.convertia.app.data.convert.WorkDir
import com.convertia.app.data.download.ActiveWork
import com.convertia.app.data.download.WorkService
import com.convertia.app.data.download.NetworkMonitor
import com.convertia.app.data.download.Notifications
import com.convertia.app.data.engine.MediaInspector
import com.convertia.app.data.engine.SimulatedConversionEngine
import com.convertia.app.data.engine.ffmpeg.Ffmpeg
import com.convertia.app.data.engine.ffmpeg.FfmpegMediaConverter
import com.convertia.app.data.engine.archive.ArchiveConverter
import com.convertia.app.data.engine.archive.RarExtractor
import com.convertia.app.data.engine.image.ImageConverter
import com.convertia.app.data.engine.office.OfficeAvailability
import com.convertia.app.data.engine.office.OfficeClient
import com.convertia.app.data.engine.office.OfficeEngine
import com.convertia.app.data.engine.pdf.PdfBoxExtractor
import com.convertia.app.data.engine.pdf.PdfDocumentConverter
import com.convertia.app.data.engine.ytdlp.YtDlp
import com.convertia.app.data.engine.ytdlp.YtDlpDownloadEngine
import com.convertia.app.data.engine.ytdlp.YtDlpMediaInspector
import com.convertia.app.data.settingsDataStore
import com.convertia.app.data.storage.MediaSaver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.convertia.app.domain.AppSettings
import com.convertia.app.domain.EngineCapabilities
import com.convertia.app.domain.Preset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val presets: List<Preset> = SampleData.presets

    /** O que o motor de conversão deste build faz. OCR entra na próxima fase. */
    val capabilities = EngineCapabilities(
        office = OfficeAvailability.isAvailable(context),
        pdfToDocument = true,
        ocr = false,
    )

    /** yt-dlp + ffmpeg empacotados; inicializa em segundo plano assim que o app abre. */
    val ytDlp = YtDlp(context.applicationContext)
    val mediaSaver = MediaSaver(context.applicationContext)

    val library = LibraryRepository(
        scope = appScope,
        deleteFile = { uri -> appScope.launch(Dispatchers.IO) { mediaSaver.delete(uri) } },
    )

    private val stateStore = AppStateStore(File(context.filesDir, "state.json"))
    val mediaInspector: MediaInspector = YtDlpMediaInspector(ytDlp)

    private val network = NetworkMonitor(context.applicationContext)

    /** "Somente em Wi-Fi" ligado e rede limitada: downloads esperam. */
    val downloadsAllowed: StateFlow<Boolean> =
        combine(settings.map { it.wifiOnly }, network.unmetered) { wifiOnly, unmetered -> !wifiOnly || unmetered }
            .stateIn(appScope, SharingStarted.Eagerly, !settings.value.wifiOnly || network.unmetered.value)

    val downloads = DownloadQueue(
        scope = appScope,
        engine = YtDlpDownloadEngine(ytDlp, mediaSaver, File(context.cacheDir, "downloads")),
        library = library,
        clock = clock,
        concurrency = settings.map { it.concurrentDownloads }.stateIn(appScope, SharingStarted.Eagerly, settings.value.concurrentDownloads),
        allowed = downloadsAllowed,
    )

    private val workDir = WorkDir(File(context.noBackupFilesDir, "convert"), context.contentResolver, library)

    /** ffmpeg/ffprobe do youtubedl-android, para as conversões de áudio, vídeo e imagem. */
    val ffmpeg = Ffmpeg(context.applicationContext, ytDlp)

    /** Motores por par origem → destino; o que ainda não tem motor cai no simulado. */
    private val conversionEngine = CompositeConversionEngine(
        workDir = workDir,
        saver = mediaSaver,
        library = library,
        settings = settings,
        converters = run {
            val office = if (capabilities.office) OfficeClient(context.applicationContext, appScope) else null
            listOfNotNull(
                FfmpegMediaConverter(ffmpeg),
                ImageConverter(ffmpeg),
                ArchiveConverter(rarExtractor = RarExtractor()),
                PdfDocumentConverter(PdfBoxExtractor(context.applicationContext), office),
                office?.let(::OfficeEngine),
            )
        },
        fallback = SimulatedConversionEngine(),
    )

    val conversions = ConversionQueue(
        scope = appScope,
        engine = conversionEngine,
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
        val app = context.applicationContext
        Notifications.createChannels(app)
        CompletionNotifier(
            scope = appScope,
            downloads = downloads,
            conversions = conversions,
            notificationsEnabled = settings.map { it.notifications }.stateIn(appScope, SharingStarted.Eagerly, true),
            messages = messages,
            isForeground = { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
            notifyDownload = { job -> Notifications.downloadFinished(app, job) },
            notifyConversions = { Notifications.conversionsFinished(app) },
        )
        // Serviço em primeiro plano enquanto houver download baixando ou conversão rodando.
        appScope.launch {
            combine(downloads.jobs, conversions.items, conversions.running, ActiveWork::of)
                .map { it.isBusy }
                .distinctUntilChanged()
                .collect { busy -> if (busy) WorkService.start(app) }
        }
        appScope.launch { conversions.removed.collect { item -> withContext(Dispatchers.IO) { workDir.discard(item) } } }
        appScope.launch(Dispatchers.IO) { runCatching { workDir.cleanAll() } }
        restoreState()
        appScope.launch {
            // Erros aqui reaparecem (com mensagem) na primeira inspeção ou download.
            runCatching { ytDlp.prepare() }
        }
    }

    /** Lê o estado salvo (arquivo pequeno, então é lido na hora) e passa a gravar cada mudança. */
    private fun restoreState() {
        stateStore.load()?.withInterruptedJobsPaused()?.let { snapshot ->
            library.seed(snapshot.library, snapshot.history, snapshot.recents)
            downloads.seed(snapshot.jobs)
        }
        appScope.launch {
            combine(library.items, library.history, library.recents, downloads.jobs) { items, history, recents, jobs ->
                AppSnapshot(items, history, recents, jobs)
            }
                .drop(1)
                // Durante um download o progresso muda várias vezes por segundo.
                .sample(SAVE_INTERVAL_MS)
                .collect { snapshot -> withContext(Dispatchers.IO) { runCatching { stateStore.save(snapshot) } } }
        }
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 1_000L
    }
}
