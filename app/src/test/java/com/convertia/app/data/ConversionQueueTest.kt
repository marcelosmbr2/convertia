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

    private fun TestScope.queue() =
        ConversionQueue(backgroundScope, FakeConversionEngine(), library, fixedClock, parallelism, MutableStateFlow(true))

    private fun item(id: String, name: String, size: Long = 1_000_000) =
        ConversionItem.create(id, name, size, "Download", ConversionSource.Sample)

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
    }

    @Test
    fun `pausar mantem o progresso e retomar continua`() = runTest {
        val queue = queue()
        queue.add(listOf(item("a", "a.png")))
        queue.start()
        advanceTimeBy(250)
        runCurrent()
        queue.pause()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(ConversionStatus.CONVERTING, queue.find("a")?.status)
        assertEquals(50.0, queue.find("a")!!.progress, 0.0)

        queue.start()
        advanceTimeBy(250)
        runCurrent()
        assertEquals(ConversionStatus.DONE, queue.find("a")?.status)
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
        queue.add(listOf(item("a", "a.png"), item("b", "b.jpg"), item("c", "c.mp3"), item("d", "d.webp")))

        val count = queue.setTargetForAll(OutputFormat("WEBP", MediaCategory.IMAGE))
        assertEquals(2, count)
        assertEquals("WEBP", queue.find("a")?.target?.ext)
        assertEquals("WAV", queue.find("c")?.target?.ext)
        assertEquals("PNG", queue.find("d")?.target?.ext)
    }

    @Test
    fun `aplicar a todos da categoria copia formato e ajustes`() = runTest {
        val queue = queue()
        queue.add(listOf(item("a", "a.png"), item("b", "b.jpg"), item("c", "c.wav")))
        queue.setTarget("a", OutputFormat("AVIF", MediaCategory.IMAGE))
        queue.updateOptions("a") { it.withQuality(60) }

        assertEquals(2, queue.applyToSameCategory("a"))
        assertEquals("AVIF", queue.find("b")?.target?.ext)
        assertEquals(60, queue.find("b")?.options?.quality)
        assertEquals("MP3", queue.find("c")?.target?.ext)
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
