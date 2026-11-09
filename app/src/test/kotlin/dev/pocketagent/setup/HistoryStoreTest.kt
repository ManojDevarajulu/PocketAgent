package dev.pocketagent.setup

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryStoreTest {
    private var clock = 1_000L
    private val store = HistoryStore(FakeStore()) { clock }

    @Test
    fun startsEmpty() = assertTrue(store.all().isEmpty())

    @Test
    fun savesTheTaskHowItWentAndTheTime() {
        clock = 5_000
        store.add("Take me to an address", true, "Maps is showing directions.", startedAt = 1_000, tokens = 6_200)
        val e = store.all().single()
        assertEquals("Take me to an address", e.task)
        assertTrue(e.ok)
        assertEquals("Maps is showing directions.", e.summary)
        assertEquals(4_000, e.durationMs)
        assertEquals(6_200, e.tokens)
    }

    @Test
    fun newestComesFirst() {
        clock = 2_000; store.add("first", true, "", 1_000, 0)
        clock = 3_000; store.add("second", false, "Stopped", 2_000, 0)
        assertEquals(listOf("second", "first"), store.all().map { it.task })
    }

    @Test
    fun idsStayUniqueWhenTwoTasksEndInTheSameMillisecond() {
        clock = 2_000
        store.add("a", true, "", 1_000, 0)
        store.add("b", true, "", 1_000, 0)
        assertEquals(2, store.all().map { it.id }.toSet().size)
    }

    @Test
    fun controlCharactersAndSeparatorsCannotBreakTheFormat() {
        store.add("line one\nline two\u001Fx\u001Ey", true, "tab\there", 0, 0)
        val e = store.all().single()
        assertEquals("line one line two x y", e.task)
        assertEquals("tab here", e.summary)
    }

    @Test
    fun blankTasksAreNotSaved() {
        assertNull(store.add("   ", true, "", 0, 0))
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun switchedOffSavesNothing() {
        store.enabled = false
        assertNull(store.add("x", true, "", 0, 0))
        assertTrue(store.all().isEmpty())
        assertFalse(store.enabled)
    }

    @Test
    fun keepsOnlyTheLatestHundred() {
        repeat(105) { i -> clock += 10; store.add("task $i", true, "", 0, 0) }
        assertEquals(100, store.all().size)
        assertEquals("task 104", store.all().first().task)
    }

    @Test
    fun removeAndClear() {
        clock = 2_000; val a = store.add("a", true, "", 1_000, 0)!!
        clock = 3_000; store.add("b", true, "", 2_000, 0)
        store.remove(a.id)
        assertEquals(listOf("b"), store.all().map { it.task })
        assertNotNull(store.find(store.all().first().id))
        store.clear()
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun aDamagedLineIsSkippedNotFatal() {
        val raw = FakeStore().also { it.putString("history.v1", "garbage\u001E1\u001F1\u001F5\u001F6\u001F7\u001Fok\u001Fdone") }
        val s = HistoryStore(raw) { 9_000 }
        assertEquals(listOf("ok"), s.all().map { it.task })
    }
}
