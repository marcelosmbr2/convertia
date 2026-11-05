package com.recast.app.data.engine.office

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import androidx.core.content.edit
import com.recast.app.BuildConfig
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

/**
 * LibreOffice embutido, num processo à parte (`:office`): se ele travar ou estourar a
 * memória num documento, o app continua de pé. Um documento por vez, numa thread própria.
 * Quando o app desvincula, o processo termina (o LibreOfficeKit não reinicia no mesmo
 * processo).
 */
class OfficeService : Service() {

    private val worker = HandlerThread("lokit").apply { start() }
    private val workerHandler = Handler(worker.looper)
    private val messenger = Messenger(IncomingHandler(this))
    private var office: Office? = null

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        stopSelf()
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Encerrando o processo do LibreOffice")
        worker.quitSafely()
        exitProcess(0)
    }

    private class IncomingHandler(service: OfficeService) : Handler(Looper.getMainLooper()) {
        private val ref = java.lang.ref.WeakReference(service)

        override fun handleMessage(msg: Message) {
            val service = ref.get() ?: return
            service.handle(msg)
        }
    }

    private fun handle(msg: Message) {
        run {
            val reply = msg.replyTo ?: return
            when (msg.what) {
                OfficeProtocol.MSG_HELLO -> reply.send(Message.obtain(null, OfficeProtocol.MSG_HELLO, Process.myPid(), 0))
                OfficeProtocol.MSG_CONVERT -> {
                    val id = msg.arg1
                    val data = msg.data
                    val input = data.getString(OfficeProtocol.KEY_INPUT).orEmpty()
                    val output = data.getString(OfficeProtocol.KEY_OUTPUT).orEmpty()
                    val format = data.getString(OfficeProtocol.KEY_FORMAT).orEmpty()
                    val options = data.getString(OfficeProtocol.KEY_OPTIONS).orEmpty()
                    workerHandler.post {
                        val result = Message.obtain(null, OfficeProtocol.MSG_RESULT, id, 0)
                        try {
                            convert(input, output, format, options)
                            result.arg2 = 1
                        } catch (e: Throwable) {
                            Log.w(TAG, "Conversão falhou", e)
                            result.data.putString(OfficeProtocol.KEY_ERROR, e.message ?: e.javaClass.simpleName)
                        }
                        runCatching { reply.send(result) }
                    }
                }
            }
        }
    }

    private fun convert(input: String, output: String, format: String, options: String) {
        val office = ensureOffice()
        val document = office.documentLoad(File(input).toURI().toString())
            ?: throw IOException(
                (office.getError()?.takeIf { it.isNotBlank() }?.let(::friendly) ?: "O LibreOffice não conseguiu abrir o arquivo") +
                    " (.${File(input).extension.lowercase()})",
            )
        try {
            document.saveAs(File(output).toURI().toString(), format, options)
        } finally {
            document.destroy()
        }
        val file = File(output)
        if (!file.isFile || file.length() == 0L) {
            throw IOException(office.getError()?.takeIf { it.isNotBlank() }?.let(::friendly) ?: "O LibreOffice não gravou o arquivo em ${format.uppercase()}")
        }
    }

    /** Primeira chamada: extrai os assets que o LibreOffice lê do disco e inicializa o LOKit. */
    private fun ensureOffice(): Office {
        office?.let { return it }
        unpackAssets()
        LibreOfficeKit.init(this)
        val handle = LibreOfficeKit.getLibreOfficeKitHandle() ?: throw IOException("O LibreOffice não inicializou")
        return Office(handle).also { office = it }
    }

    /** `assets/unpack` → dataDir, uma vez por versão do app (como o LibreOffice Viewer faz). */
    private fun unpackAssets() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dataDir = File(applicationInfo.dataDir)
        if (prefs.getInt(KEY_UNPACKED, -1) == BuildConfig.VERSION_CODE && File(dataDir, "program/sofficerc").isFile) return
        copyAssetDir(assets, "unpack", dataDir)
        prefs.edit { putInt(KEY_UNPACKED, BuildConfig.VERSION_CODE) }
    }

    private fun copyAssetDir(manager: AssetManager, path: String, into: File) {
        val children = manager.list(path).orEmpty()
        for (child in children) {
            val childPath = "$path/$child"
            val grandChildren = manager.list(childPath).orEmpty()
            val target = File(into, child)
            if (grandChildren.isEmpty()) {
                target.parentFile?.mkdirs()
                manager.open(childPath).use { input -> target.outputStream().use { input.copyTo(it) } }
            } else {
                target.mkdirs()
                copyAssetDir(manager, childPath, target)
            }
        }
    }

    /** Mensagens do LibreOffice em algo que o usuário entenda. */
    private fun friendly(error: String): String = when {
        "password" in error.lowercase() -> "Documento protegido por senha"
        "filter" in error.lowercase() && "not" in error.lowercase() -> "O LibreOffice não tem filtro para este formato"
        "empty reference" in error.lowercase() -> "O LibreOffice não reconheceu o formato do arquivo"
        else -> error.lineSequence().firstOrNull { it.isNotBlank() }?.take(200) ?: error
    }

    private companion object {
        const val TAG = "OfficeService"
        const val PREFS = "office"
        const val KEY_UNPACKED = "unpacked_version_code"
    }
}
