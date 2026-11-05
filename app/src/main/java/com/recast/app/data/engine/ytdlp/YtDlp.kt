package com.recast.app.data.engine.ytdlp

import android.content.Context
import com.recast.app.data.engine.ffmpeg.WebpLibraries
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Acesso ao yt-dlp empacotado (youtubedl-android: Python, yt-dlp, QuickJS e ffmpeg). A
 * primeira inicialização extrai o Python para o armazenamento do app e leva alguns
 * segundos; por isso [prepare] roda em segundo plano assim que o app abre.
 */
class YtDlp(private val context: Context) {

    private val initLock = Mutex()
    @Volatile private var ready = false
    private val ids = AtomicLong()
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val _version = MutableStateFlow<String?>(null)

    /** Versão do yt-dlp em uso, depois de inicializado. */
    val version: StateFlow<String?> = _version.asStateFlow()

    suspend fun prepare() = withContext(Dispatchers.IO) {
        initLock.withLock {
            if (ready) return@withLock
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            WebpLibraries.install(context)
            ready = true
            _version.value = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
        }
    }

    /** `--dump-single-json` de [url]. */
    suspend fun inspect(url: String): String {
        prepare()
        val request = YoutubeDLRequest(url).addCommands(YtDlpArgs.inspect())
        return run(request, "inspect-${ids.incrementAndGet()}") { _, _ -> }.out
    }

    /**
     * Roda um download. [onLine] recebe cada linha de saída com o percentual que a
     * biblioteca já extraiu. Cancelar a coroutine mata o processo.
     */
    suspend fun download(
        url: String,
        args: List<String>,
        processId: String,
        onLine: (line: String, percent: Float) -> Unit,
    ) {
        prepare()
        run(YoutubeDLRequest(url).addCommands(args), processId) { percent, line -> onLine(line, percent) }
    }

    /** Atualiza o yt-dlp pelo canal estável. Devolve a versão nova, ou `null` se já estava em dia. */
    suspend fun update(): String? = withContext(Dispatchers.IO) {
        prepare()
        initLock.withLock { updateLocked() }
    }

    /**
     * Atualiza se a última verificação tem mais de um dia. O YouTube muda com frequência e
     * o yt-dlp que vem na biblioteca envelhece: sem isso, os downloads falham com HTTP 403.
     * Segura o mesmo lock do [prepare], então inspeções e downloads esperam a versão nova.
     */
    suspend fun updateIfStale() = withContext(Dispatchers.IO) {
        prepare()
        initLock.withLock {
            val elapsed = System.currentTimeMillis() - prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
            if (elapsed in 0 until UPDATE_INTERVAL_MS) return@withLock
            runCatching { updateLocked() }
        }
    }

    private fun updateLocked(): String? {
        val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply()
        val version = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
        _version.value = version
        return if (status == YoutubeDL.UpdateStatus.DONE) version else null
    }

    /**
     * Executa num thread próprio (o processo bloqueia) e liga o cancelamento da coroutine
     * ao `destroyProcessById`.
     */
    private suspend fun run(
        request: YoutubeDLRequest,
        processId: String,
        onProgress: (Float, String) -> Unit,
    ): Response = suspendCancellableCoroutine { continuation ->
        val worker = thread(name = "yt-dlp-$processId") {
            try {
                val response = YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
                    onProgress(progress, line)
                }
                continuation.resume(Response(response.out, response.err))
            } catch (e: YoutubeDL.CanceledException) {
                continuation.cancel(e)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(YtDlpException(e))
            }
        }
        continuation.invokeOnCancellation {
            YoutubeDL.getInstance().destroyProcessById(processId)
            worker.interrupt()
        }
    }

    data class Response(val out: String, val err: String)

    private companion object {
        const val PREFS = "ytdlp"
        const val KEY_LAST_UPDATE_CHECK = "lastUpdateCheck"
        const val UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}

/** Erro do yt-dlp com a mensagem já limpa para mostrar ao usuário. */
class YtDlpException(cause: Throwable) : Exception(
    YtDlpProgress.errorMessage(cause.message) ?: cause.message?.lineSequence()?.lastOrNull { it.isNotBlank() } ?: cause.javaClass.simpleName,
    cause,
)
