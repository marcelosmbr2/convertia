package com.convertia.app.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepositoryTest {

    private val item = SampleData.libraryItems(fixedClock).first().copy(uri = "content://media/1")

    @Test
    fun `exclusao apaga o arquivo so depois do prazo do desfazer`() = runTest {
        val deleted = mutableListOf<String>()
        val library = LibraryRepository(backgroundScope, { deleted += it }, deleteDelayMs = 10_000)
        library.add(item)

        library.remove(item.id)
        assertTrue(library.items.value.isEmpty())
        advanceTimeBy(9_000)
        runCurrent()
        assertTrue(deleted.isEmpty())
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf("content://media/1"), deleted)
    }

    @Test
    fun `desfazer cancela a exclusao`() = runTest {
        val deleted = mutableListOf<String>()
        val library = LibraryRepository(backgroundScope, { deleted += it }, deleteDelayMs = 10_000)
        library.add(item)

        val (removed, index) = library.remove(item.id)!!
        library.restore(removed, index)
        advanceTimeBy(20_000)
        runCurrent()
        assertTrue(deleted.isEmpty())
        assertEquals(listOf(item), library.items.value)
    }
}
