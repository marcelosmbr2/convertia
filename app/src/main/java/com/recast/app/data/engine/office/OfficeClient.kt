package com.recast.app.data.engine.office

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lado do app do [OfficeService]: liga ao processo `:office` quando há documento para
 * converter, um por vez, e desliga depois de um tempo ocioso. Cancelar (pausar ou remover
 * o item) mata o processo, porque o LibreOffice não interrompe uma conversão no meio;
 * o próximo documento sobe um processo novo.
 */
class OfficeClient(
    private val context: Context,
    private val scope: CoroutineScope,
    private val idleMs: Long = IDLE_MS,
    private val timeoutMs: Long = TIMEOUT_MS,
) {
    private val lock = Mutex()
    private val main = Handler(Looper.getMainLooper())
    private val ids = AtomicInteger()
    private var connection: Connection? = null
    private var idleJob: Job? = null

    /** Converte [input] em [output] no [format] do LibreOfficeKit ("pdf", "docx"…), com [options] de filtro. */
    suspend fun convert(input: File, output: File, format: String, options: String = "") = lock.withLock {
        idleJob?.cancel()
        try {
            val conn = connect()
            val id = ids.incrementAndGet()
            val result = CompletableDeferred<Result<Unit>>()
            conn.pending[id] = result
            val message = Message.obtain(null, OfficeProtocol.MSG_CONVERT, id, 0).apply {
                replyTo = conn.replies
                data = Bundle().apply {
                    putString(OfficeProtocol.KEY_INPUT, input.absolutePath)
                    putString(OfficeProtocol.KEY_OUTPUT, output.absolutePath)
                    putString(OfficeProtocol.KEY_FORMAT, format)
                    putString(OfficeProtocol.KEY_OPTIONS, options)
                }
            }
            try {
                conn.service.send(message)
            } catch (e: RemoteException) {
                throw IOException("O LibreOffice parou antes de abrir o arquivo")
            }
            try {
                val outcome = withTimeoutOrNull(timeoutMs) { result.await() }
                    ?: run {
                        kill(conn)
                        throw IOException("O LibreOffice demorou demais com este arquivo")
                    }
                outcome.getOrThrow()
            } catch (e: CancellationException) {
                // Pausar ou remover: só matando o processo a conversão para.
                kill(conn)
                throw e
            } finally {
                conn.pending.remove(id)
            }
        } finally {
            scheduleDisconnect()
        }
    }

    private suspend fun connect(): Connection {
        connection?.takeIf { it.alive }?.let { return it }
        connection?.let { disconnect(it) }
        val ready = CompletableDeferred<Connection>()
        val conn = Connection(ready)
        val intent = Intent(context, OfficeService::class.java)
        val bound = withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            context.bindService(intent, conn, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        }
        if (!bound) throw IOException("Não foi possível iniciar o LibreOffice")
        val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { ready.await() }
        if (connected == null) {
            disconnect(conn)
            throw IOException("O LibreOffice não respondeu")
        }
        connection = conn
        return conn
    }

    private fun scheduleDisconnect() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleMs)
            lock.withLock { connection?.let { disconnect(it) }; connection = null }
        }
    }

    private fun kill(conn: Connection) {
        conn.pid?.let { pid -> runCatching { Process.killProcess(pid) } }
        disconnect(conn)
        if (connection === conn) connection = null
    }

    private fun disconnect(conn: Connection) {
        conn.alive = false
        main.post { runCatching { context.unbindService(conn) } }
    }

    private inner class Connection(private val ready: CompletableDeferred<Connection>) : ServiceConnection {
        lateinit var service: Messenger
        val replies = Messenger(ReplyHandler(this))
        val pending = java.util.concurrent.ConcurrentHashMap<Int, CompletableDeferred<Result<Unit>>>()
        @Volatile var alive = true
        @Volatile var pid: Int? = null

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = Messenger(binder)
            runCatching { binder.linkToDeath({ onDied() }, 0) }
            runCatching { service.send(Message.obtain(null, OfficeProtocol.MSG_HELLO).apply { replyTo = replies }) }
                .onFailure { onDied() }
        }

        override fun onServiceDisconnected(name: ComponentName) = onDied()

        fun onHello(pid: Int) {
            this.pid = pid
            ready.complete(this)
        }

        fun onDied() {
            alive = false
            if (!ready.isCompleted) ready.completeExceptionally(IOException("O LibreOffice parou ao iniciar"))
            pending.values.forEach { it.complete(Result.failure(IOException("O LibreOffice parou ao abrir este arquivo"))) }
            pending.clear()
        }
    }

    private class ReplyHandler(private val connection: Connection) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                OfficeProtocol.MSG_HELLO -> connection.onHello(msg.arg1)
                OfficeProtocol.MSG_RESULT -> {
                    val result = connection.pending[msg.arg1] ?: return
                    if (msg.arg2 == 1) {
                        result.complete(Result.success(Unit))
                    } else {
                        result.complete(Result.failure(IOException(msg.data.getString(OfficeProtocol.KEY_ERROR) ?: "O LibreOffice não converteu o arquivo")))
                    }
                }
            }
        }
    }

    private companion object {
        const val IDLE_MS = 60_000L
        const val TIMEOUT_MS = 10 * 60_000L
        const val CONNECT_TIMEOUT_MS = 30_000L
    }
}
