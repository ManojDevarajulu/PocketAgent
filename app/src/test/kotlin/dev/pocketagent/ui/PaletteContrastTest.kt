package dev.pocketagent.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

class PaletteContrastTest {
    private val raw: Map<String, String> by lazy {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val root = factory.newDocumentBuilder().parse(File("src/main/res/values/colors.xml")).documentElement
        val nodes = root.getElementsByTagName("color")
        (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent.trim() }
    }

    /** Resolves `@color/other` references; returns #AARRGGBB text. */
    private fun value(name: String): String {
        var v = raw[name] ?: error("missing colour $name")
        while (v.startsWith("@color/")) v = raw[v.removePrefix("@color/")] ?: error("missing colour $v")
        return v.uppercase()
    }

    private fun luminance(name: String): Double {
        val rgb = value(name).removePrefix("#").takeLast(6).chunked(2).map { it.toInt(16) / 255.0 }
        val (r, g, b) = rgb.map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(a: String, b: String): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun darkTextOnOrangeIsAtLeast7() = assertTrue(contrast("brand_background", "brand_orange") >= 7.0)

    @Test
    fun textOnBackgroundIsAtLeast7() = assertTrue(contrast("brand_text", "brand_background") >= 7.0)

    @Test
    fun mutedOnBackgroundAndSurfaceIsAtLeast4_5() {
        assertTrue(contrast("brand_muted", "brand_background") >= 4.5)
        assertTrue(contrast("brand_muted", "brand_surface") >= 4.5)
    }

    /** Only 4:1, so labels on solid blue must be bold and at least 18 sp. */
    @Test
    fun textOnBlueIsAtLeast4() = assertTrue(contrast("brand_text", "brand_blue") >= 4.0)

    @Test
    fun allowAndDeclineAreIdentical() {
        assertEquals(value("button_allow"), value("button_decline"))
        assertEquals(value("brand_text"), value("button_allow_text"))
    }

    @Test
    fun bubbleRunningIsTheBrandBlue() = assertEquals(value("brand_blue"), value("bubble_running"))

    @Test
    fun bubbleStateColoursOtherThanRunningAreUnchanged() {
        assertEquals("#FF757575", value("bubble_idle"))
        assertEquals("#FFF2A100", value("bubble_waiting"))
        assertEquals("#FF2E9E4F", value("bubble_done"))
        assertEquals("#FFD83A3A", value("bubble_error"))
    }

    @Test
    fun everyBrandTokenHasTheSpecValue() {
        val expected = mapOf(
            "brand_background" to "#FF090A10",
            "brand_surface" to "#FF121520",
            "brand_border" to "#FF1E2333",
            "brand_blue" to "#FF1E6FFF",
            "brand_blue_subdued" to "#FF1A2B4C",
            "brand_orange" to "#FFFF8544",
            "brand_text" to "#FFF8F9FC",
            "brand_muted" to "#FF8E95A5",
        )
        expected.forEach { (name, hex) -> assertEquals(hex, value(name), name) }
    }
}
