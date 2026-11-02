package com.convertia.app.data

import com.convertia.app.domain.ConversionItem
import com.convertia.app.domain.ConversionSource
import com.convertia.app.domain.ConversionStatus
import com.convertia.app.domain.MediaCategory
import com.convertia.app.domain.OutputFormat
import com.convertia.app.domain.QualityPreset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversionQueueTest {

    private val library = LibraryRepository()
    private val parallelism = MutableStateFlow(2)

    private fun TestScope.queue(engine: FakeConversionEngine = FakeConversionEngine()) =
        ConversionQueue(backgroundScope, engine, library, fixedClock, parallelism, MutableStateFlow(true))

    private fun item(id: String, name: String, size: Long = 1_000_000) =
        ConversionItem.create(id, name, size, "Download", ConversionSource.Sample)!!

    @Test
    fun `converte ate o limite, grava historico e para quando acaba`() = runTest {
        val queue = queue()
        var allFinished = 0
        backgroundScope.launch { queue.allFinished.collect { allFinished++ } }
        queue.add(listOf(item("a", "a.png"), item("b", "b.wav"), item("c", "c.mov")))
        assertFalse(queue.running.value)

        queue.start()
        runCurrent()
        assertTrue(queue.running.value)
        assertEquals(2, queue.items.value.count { it.status == ConversionStatus.CONVERTING })

        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(queue.items.value.all { it.status == ConversionStatus.DONE })
        assertFalse(queue.running.value)
        assertEquals(1, allFinished)
        assertEquals(3, library.history.value.size)
        assertEquals(500_000L, queue.find("a")?.outputBytes)
        assertEquals("c.mov", library.history.value.first().name)
        assertEquals("content://convertia/c", library.history.value.first().uri)
        assertEquals(1, queue.find("a")?.outputs?.size)
    }

    @Test
    fun `pausar mantem o item em andamento, zera o progresso e retomar recomeca`() = runTest {
        val queue = queue()
        queue.add(listOf(item("a", "a.png")))
        queue.start()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(50.0, queue.find("a")!!.progress, 0.0)
        queue.pause()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(ConversionStatus.CONVERTING, queue.find("a")?.status)
        assertEquals(0.0, queue.find("a")!!.progress, 0.0)

        queue.start()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(50.0, queue.find("a")!!.progress, 0.0)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(ConversionStatus.DONE, queue.find("a")?.status)
    }

    @Test
    fun `falha marca o item, segue a fila e desliga ao acabar`() = runTest {
        val queue = queue(FakeConversionEngine(failing = setOf("wav"), silent = setOf("mov")))
        var allFinished = 0
        backgroundScope.launch { queue.allFinished.collect { allFinished++ } }
        parallelism.value = 1
        queue.add(listOf(item("a", "a.wav"), item("b", "b.mov"), item("c", "c.png")))
        queue.start()
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(ConversionStatus.FAILED, queue.find("a")?.status)
        assertEquals("Motor recusou a.wav", queue.find("a")?.error)
        assertEquals(ConversionStatus.FAILED, queue.find("b")?.status)
        assertEquals("A conversão terminou sem gerar arquivo", queue.find("b")?.error)
        assertEquals(ConversionStatus.DONE, queue.find("c")?.status)
        assertFalse(queue.running.value)
        assertEquals(1, allFinished)
        assertEquals(1, library.history.value.size)

        // Só falhas: Converter não faz nada; tentar de novo religa a fila para aquele item.
        queue.start()
        assertFalse(queue.running.value)
        queue.retry("b")
        assertTrue(queue.running.value)
        assertEquals(ConversionStatus.CONVERTING, queue.find("b")?.status)
        assertEquals(null, queue.find("b")?.error)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(ConversionStatus.FAILED, queue.find("b")?.status)
        assertFalse(queue.running.value)
    }

    @Test
    fun `quando so falhas sobram, a fila desliga sem avisar conclusao`() = runTest {
        val queue = queue(FakeConversionEngine(failing = setOf("wav")))
        var allFinished = 0
        backgroundScope.launch { queue.allFinished.collect { allFinished++ } }
        queue.add(listOf(item("a", "a.wav")))
        queue.start()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(ConversionStatus.FAILED, queue.find("a")?.status)
        assertFalse(queue.running.value)
        assertEquals(0, allFinished)
    }

    @Test
    fun `predefinicao vale so para as extensoes da regra e reinicia concluidos`() = runTest {
        val queue = queue()
        queue.add(listOf(item("a", "foto.heic"), item("b", "foto2.HEIF"), item("c", "musica.wav")))
        queue.start()
        advanceTimeBy(1_000)
        runCurrent()

        val count = queue.applyPreset(SampleData.presets.first { it.id == "heic-jpg" })
        assertEquals(2, count)
        assertEquals(ConversionStatus.READY, queue.find("a")?.status)
        assertEquals(85, queue.find("a")?.options?.quality)
        assertEquals(QualityPreset.CUSTOM, queue.find("a")?.options?.preset)
        assertEquals(ConversionStatus.DONE, queue.find("c")?.status)
    }

    @Test
    fun `formato para todos ignora incompativeis e quem ja esta no formato`() = runTest {
        val queue = queue()
        queue.add(listOf(item("a", "a.png"), item("b", "b.jpg"), item("c", "c.mp3"), item("d", "d.webp"), item("e", "e.mp4")))

        // e.mp4 pode virar imagem (um quadro), mas só JPG/PNG/WEBP.
        val count = queue.setTargetForAll(OutputFormat("WEBP", MediaCategory.IMAGE))
        assertEquals(3, count)
        assertEquals("WEBP", queue.find("e")?.target?.ext)
        // AVIF não vale para o quadro de vídeo: só as três imagens mudam.
        assertEquals(3, queue.setTargetForAll(OutputFormat("AVIF", MediaCategory.IMAGE)))
        assertEquals("WEBP", queue.find("e")?.target?.ext)
        assertEquals("AVIF", queue.find("a")?.target?.ext)
        assertEquals("AVIF", queue.find("d")?.target?.ext)
        assertEquals("WAV", queue.find("c")?.target?.ext)
    }

    @Test
    fun `aplicar a todos da categoria copia formato e ajustes`() = runTest {
        val queue = queue()
        queue.add(listOf(item("a", "a.png"), item("b", "b.jpg"), item("c", "c.wav"), item("d", "d.pdf")))
        queue.setTarget("a", OutputFormat("AVIF", MediaCategory.IMAGE))
        queue.updateOptions("a") { it.withQuality(60) }

        // d.pdf é "documento", então não entra; c.wav é áudio.
        assertEquals(2, queue.applyToSameCategory("a"))
        assertEquals("AVIF", queue.find("b")?.target?.ext)
        assertEquals(60, queue.find("b")?.options?.quality)
        assertEquals("MP3", queue.find("c")?.target?.ext)
        assertEquals("PNG", queue.find("d")?.target?.ext)
    }

    @Test
    fun `remover o ultimo pendente desliga a fila`() = runTest {
        val queue = queue()
        queue.add(listOf(item("a", "a.png")))
        queue.start()
        runCurrent()
        queue.remove("a")
        assertFalse(queue.running.value)
        assertTrue(queue.items.value.isEmpty())
    }
}
