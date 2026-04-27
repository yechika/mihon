package eu.kanade.tachiyomi.data.cloudsync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MangaProgressTest {

    @Test
    fun `merging two writes unions the read sets`() {
        val a = MangaProgress("m1", "ch1", 100L, setOf("ch1"))
        val b = MangaProgress("m1", "ch2", 200L, setOf("ch2"))

        val merged = a.mergedWith(b)

        assertEquals(setOf("ch1", "ch2"), merged.readChapterIds)
    }

    @Test
    fun `merge keeps the most recent lastReadChapterId`() {
        val a = MangaProgress("m1", "ch1", 100L, setOf("ch1"))
        val b = MangaProgress("m1", "ch5", 500L, setOf("ch5"))

        val merged = a.mergedWith(b)

        assertEquals("ch5", merged.lastReadChapterId)
        assertEquals(500L, merged.lastReadAt)
    }

    @Test
    fun `merge is commutative on read set`() {
        val a = MangaProgress("m1", "ch1", 100L, setOf("ch1"))
        val b = MangaProgress("m1", "ch2", 200L, setOf("ch2"))

        assertEquals(a.mergedWith(b).readChapterIds, b.mergedWith(a).readChapterIds)
    }

    @Test
    fun `merging different manga ids throws`() {
        val a = MangaProgress("m1", null, 0L, emptySet())
        val b = MangaProgress("m2", null, 0L, emptySet())

        assertThrows(IllegalArgumentException::class.java) { a.mergedWith(b) }
    }

    @Test
    fun `merging picks the maximum lastReadAt`() {
        val a = MangaProgress("m1", "ch5", 999L, setOf("ch5"))
        val b = MangaProgress("m1", "ch1", 100L, setOf("ch1"))

        assertEquals(999L, a.mergedWith(b).lastReadAt)
        assertEquals(999L, b.mergedWith(a).lastReadAt)
    }
}
