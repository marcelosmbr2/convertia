package com.recast.app.data.engine.ffmpeg

import android.content.Context
import com.recast.app.data.engine.ytdlp.YtDlp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Roda o ffmpeg e o ffprobe empacotados pelo youtubedl-android como processos. Os binários
 * ficam na pasta de bibliotecas nativas (`libffmpeg.so`), e as bibliotecas de que eles
 * dependem são extraídas por [YtDlp.prepare] para o armazenamento do app.
 */
class Ffmpeg(context: Context, private val ytDlp: YtDlp) {

    private val binDir = File(context.applicationInfo.nativeLibraryDir)
    private val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")

    private val ffmpeg get() = File(binDir, "libffmpeg.so")
    private val ffprobe get() = File(binDir, "libffprobe.so")

    private val libraryPath: String
        get() = listOf("python", "ffmpeg").joinToString(":") { File(packages, "$it/usr/lib").absolutePath }

    /** Duração, faixas e dimensões de um arquivo de mídia. */
    suspend fun probe(input: File): MediaProbe {
        ytDlp.prepare()
        val args = listOf(
            ffprobe.absolutePath, "-v", "error", "-of", "json", "-show_format", "-show_streams", input.absolutePath,
        )
        val result = execute(args, input.parentFile) { }
        if (result.exitCode != 0) throw FfmpegException(result.errorMessage() ?: "Não foi possível ler o arquivo")
        return runCatching { MediaProbe.parse(result.stdout) }
            .getOrElse { throw FfmpegException("Não foi possível ler o arquivo") }
    }

    /**
     * Executa o ffmpeg com [args] (da entrada em diante), lendo o progresso de `-progress
     * pipe:1`. Cancelar a coroutine mata o processo. Lança [FfmpegException] com a última
     * mensagem de erro do ffmpeg se ele falhar.
     */
    suspend fun run(args: List<String>, workDir: File, onProgress: (FfmpegProgress.Update) -> Unit, duration: Double? = null) {
        ytDlp.prepare()
        val progress = FfmpegProgress(duration)
        val command = listOf(
            ffmpeg.absolutePath, "-nostdin", "-y", "-hide_banner", "-loglevel", "error", "-progress", "pipe:1", "-nostats",
        ) + args
        val result = execute(command, workDir) { line -> progress.onLine(line)?.let(onProgress) }
        if (result.exitCode != 0) throw FfmpegException(result.errorMessage() ?: "O ffmpeg falhou (código ${result.exitCode})")
    }

    private class Result(val exitCode: Int, val stdout: String, val stderr: List<String>) {
        /** Última linha útil do stderr, sem o prefixo "[módulo @ 0x…]". */
        fun errorMessage(): String? = stderr.asReversed()
            .map { it.replace(MODULE_PREFIX, "").trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("Conversion failed") }
            ?.let { if (it.length > 200) it.take(200) + "…" else it }
    }

    private suspend fun execute(command: List<String>, workDir: File?, onStdoutLine: (String) -> Unit): Result =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val process = try {
                    ProcessBuilder(command)
                        .directory(workDir)
                        .apply { environment()["LD_LIBRARY_PATH"] = libraryPath }
                        .start()
                } catch (e: IOException) {
                    continuation.resumeWithException(FfmpegException("Não foi possível iniciar o ffmpeg: ${e.message}"))
                    return@suspendCancellableCoroutine
                }
                continuation.invokeOnCancellation { process.destroy() }
                val stderr = ArrayDeque<String>()
                val errReader = thread(name = "ffmpeg-stderr", isDaemon = true) {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            synchronized(stderr) {
                                stderr.addLast(line)
                                if (stderr.size > MAX_STDERR_LINES) stderr.removeFirst()
                            }
                        }
                    }
                }
                thread(name = "ffmpeg-stdout", isDaemon = true) {
                    val out = StringBuilder()
                    try {
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                if (out.length < MAX_STDOUT_CHARS) out.append(line).append('\n')
                                onStdoutLine(line)
                            }
                        }
                        val code = process.waitFor()
                        errReader.join()
                        if (continuation.isActive) continuation.resume(Result(code, out.toString(), synchronized(stderr) { stderr.toList() }))
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(FfmpegException(e.message ?: "Falha ao rodar o ffmpeg"))
                    }
                }
            }
        }

    private companion object {
        const val MAX_STDERR_LINES = 30
        const val MAX_STDOUT_CHARS = 1 shl 20
        val MODULE_PREFIX = Regex("""^\[[^\]]+@ 0x[0-9a-f]+]\s*""")
    }
}

class FfmpegException(message: String) : IOException(message)
