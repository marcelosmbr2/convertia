package com.recast.app.data

import com.recast.app.domain.DownloadStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueTest {

    private val library = LibraryRepository()
    private val concurrency = MutableStateFlow(2)

    private fun TestScope.queue(engine: FakeDownloadEngine = FakeDownloadEngine()) =
        DownloadQueue(backgroundScope, engine, library, fixedClock, concurrency)

    @Test
    fun `respeita o limite e comeca pelo mais antigo`() = runTest {
        val queue = queue()
        val first = queue.enqueue(request("a"))
        val second = queue.enqueue(request("b"))
        val third = queue.enqueue(request("c"))
        runCurrent()

        assertEquals(DownloadStatus.DOWNLOADING, queue.find(first)?.status)
        assertEquals(DownloadStatus.DOWNLOADING, queue.find(second)?.status)
        assertEquals(DownloadStatus.QUEUED, queue.find(third)?.status)
    }

    @Test
    fun `ao concluir vai para a Biblioteca e libera a vaga`() = runTest {
        val queue = queue()
        val finished = mutableListOf<Long>()
        backgroundScope.launch { queue.finished.collect { finished += it.id } }
        val ids = listOf("a", "b", "c").map { queue.enqueue(request(it)) }

        advanceTimeBy(450) // 4 passos de 25%: os dois primeiros terminam
        runCurrent()
        assertEquals(DownloadStatus.DONE, queue.find(ids[0])?.status)
        assertEquals(DownloadStatus.DOWNLOADING, queue.find(ids[2])?.status)

        advanceTimeBy(450)
        runCurrent()
        assertEquals(listOf(DownloadStatus.DONE), ids.map { queue.find(it)?.status }.distinct())
        assertEquals(3, library.items.value.size)
        assertEquals("c", library.items.value.first().title)
        assertEquals(ids, finished)
        assertEquals(fixedClock.millis(), queue.find(ids[0])?.finishedAt)
    }

    @Test
    fun `pausar guarda o progresso e retomar continua dali`() = runTest {
        val queue = queue()
        val id = queue.enqueue(request("a"))
        advanceTimeBy(250)
        runCurrent()
        assertEquals(50.0, queue.find(id)!!.progress, 0.0)

        queue.pause(id)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(DownloadStatus.PAUSED, queue.find(id)?.status)
        assertEquals(50.0, queue.find(id)!!.progress, 0.0)
        assertEquals(0.0, queue.find(id)!!.speedMbps, 0.0)

        queue.resume(id)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(DownloadStatus.DONE, queue.find(id)?.status)
    }

    @Test
    fun `erro do motor marca falha e tentar de novo recomeca do zero`() = runTest {
        val queue = queue(FakeDownloadEngine(failAt = 50.0))
        val id = queue.enqueue(request("a"))
        advanceTimeBy(250)
        runCurrent()
        assertEquals(DownloadStatus.FAILED, queue.find(id)?.status)
        assertEquals("HTTP 403", queue.find(id)?.error)

        queue.retry(id)
        runCurrent()
        assertEquals(DownloadStatus.DOWNLOADING, queue.find(id)?.status)
        assertEquals(0.0, queue.find(id)!!.progress, 0.0)
        assertEquals(null, queue.find(id)?.error)
    }

    @Test
    fun `pausar tudo e retomar tudo`() = runTest {
        val queue = queue()
        val ids = listOf("a", "b", "c").map { queue.enqueue(request(it)) }
        runCurrent()
        queue.pauseAll()
        assertEquals(listOf(DownloadStatus.PAUSED), ids.map { queue.find(it)?.status }.distinct())

        queue.resumeAll()
        runCurrent()
        assertEquals(2, ids.count { queue.find(it)?.status == DownloadStatus.DOWNLOADING })
    }

    @Test
    fun `capitulos separados viram itens da Biblioteca depois do principal`() = runTest {
        val queue = queue(FakeDownloadEngine(extraFiles = 2))
        val id = queue.enqueue(request("aula"))
        advanceTimeBy(450)
        runCurrent()
        val items = library.items.value
        assertEquals(listOf("aula", "Capítulo 0", "Capítulo 1"), items.map { it.title })
        assertEquals("content://media/$id", items.first().uri)
        assertEquals(items.first().id, queue.find(id)?.libraryItemId)
    }

    @Test
    fun `remover um download incompleto descarta o que sobrou`() = runTest {
        val engine = FakeDownloadEngine()
        val queue = queue(engine)
        val id = queue.enqueue(request("a"))
        advanceTimeBy(150)
        runCurrent()
        queue.remove(id)
        assertEquals(listOf(id), engine.discarded)
    }

    @Test
    fun `sem Wi-Fi os downloads esperam e continuam de onde pararam`() = runTest {
        val allowed = MutableStateFlow(true)
        val queue = DownloadQueue(backgroundScope, FakeDownloadEngine(), library, fixedClock, concurrency, allowed)
        val id = queue.enqueue(request("a"))
        advanceTimeBy(250)
        runCurrent()
        assertEquals(50.0, queue.find(id)!!.progress, 0.0)

        allowed.value = false
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(DownloadStatus.QUEUED, queue.find(id)?.status)
        assertEquals(50.0, queue.find(id)!!.progress, 0.0)
        val second = queue.enqueue(request("b"))
        runCurrent()
        assertEquals(DownloadStatus.QUEUED, queue.find(second)?.status)

        allowed.value = true
        advanceTimeBy(250)
        runCurrent()
        assertEquals(DownloadStatus.DONE, queue.find(id)?.status)
    }

    @Test
    fun `aumentar o limite inicia mais downloads`() = runTest {
        val queue = queue()
        val ids = listOf("a", "b", "c").map { queue.enqueue(request(it)) }
        runCurrent()
        concurrency.value = 3
        runCurrent()
        assertEquals(3, ids.count { queue.find(it)?.status == DownloadStatus.DOWNLOADING })
    }
}
