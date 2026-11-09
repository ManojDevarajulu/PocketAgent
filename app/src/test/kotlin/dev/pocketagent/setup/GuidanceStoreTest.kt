package dev.pocketagent.setup

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GuidanceStoreTest {
    private val steps = "1. Open Settings.\n2. Tap Apps.\n"

    private fun assertRejected(result: Result<Guidance>, expected: String) {
        assertTrue(result.isFailure, "expected a failure")
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains(expected), "message \"$message\" should mention \"$expected\"")
    }

    @Test
    fun parsesAValidFile() {
        val g = GuidanceStore.parse("nothing.md", steps.replace("\n", "\r\n")).getOrThrow()
        assertEquals("nothing", g.manufacturer)
        assertEquals("1. Open Settings.\n2. Tap Apps.", g.body)
    }

    @Test
    fun rejectsAnOversizedFile() {
        assertRejected(GuidanceStore.parse("a.md", "x".repeat(2049)), "2048")
        assertTrue(GuidanceStore.parse("a.md", "x".repeat(2048)).isSuccess)
    }

    @Test
    fun rejectsAControlCharacter() {
        assertRejected(GuidanceStore.parse("a.md", steps + "bell\u0007\n"), "control character")
    }

    @Test
    fun rejectsABlankFile() {
        assertRejected(GuidanceStore.parse("a.md", " \n\t\n"), "blank")
    }

    @Test
    fun rejectsABadFileName() {
        for (name in listOf("Nothing.md", "a b.md", "x.txt")) {
            assertRejected(GuidanceStore.parse(name, steps), name)
        }
    }

    @Test
    fun loadRequiresGenericAndNamesEveryProblem() {
        val e = assertFailsWith<IllegalArgumentException> {
            GuidanceStore.load(mapOf("Bad.md" to steps, "empty.md" to "", "nothing.md" to steps))
        }
        val m = e.message.orEmpty()
        assertTrue(m.contains("Bad.md") && m.contains("empty.md") && m.contains("generic.md"), m)
        assertTrue(!m.contains("nothing.md"), m)
        assertFailsWith<IllegalArgumentException> { GuidanceStore.load(mapOf("nothing.md" to steps)) }
    }

    private val store = GuidanceStore.load(mapOf("generic.md" to "generic steps", "nothing.md" to "nothing steps"))

    @Test
    fun unknownManufacturerFallsBackToGeneric() {
        assertEquals("generic", store.forManufacturer("samsung").manufacturer)
    }

    @Test
    fun manufacturerMatchIsCaseInsensitive() {
        assertEquals("nothing", store.forManufacturer("Nothing").manufacturer)
        assertEquals("nothing", store.forManufacturer("  NOTHING ").manufacturer)
    }

    @Test
    fun nullManufacturerFallsBack() {
        assertEquals("generic", store.forManufacturer(null).manufacturer)
    }

    @Test
    fun bundledGuidanceFilesAreValid() {
        val files = File("src/main/assets/guidance").listFiles { f -> f.extension == "md" }.orEmpty()
            .associate { it.name to it.readText() }
        GuidanceStore.load(files)
    }
}
