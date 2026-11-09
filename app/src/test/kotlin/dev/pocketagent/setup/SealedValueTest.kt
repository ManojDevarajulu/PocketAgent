package dev.pocketagent.setup

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SealedValueTest {
    @Test
    fun roundTrips() {
        val bytes = byteArrayOf(0, 1, 2, 127, -128, -1, 42)
        val text = SealedValue.encode(bytes)
        assertTrue(text.startsWith("v1:"))
        assertContentEquals(bytes, SealedValue.decode(text))
    }

    @Test
    fun rejectsNullBlankWrongPrefixBadBase64AndEmptyPayload() {
        assertNull(SealedValue.decode(null))
        assertNull(SealedValue.decode(""))
        assertNull(SealedValue.decode("   "))
        assertNull(SealedValue.decode("v2:AAAA"))
        assertNull(SealedValue.decode("AAAA"))
        assertNull(SealedValue.decode("v1:!!!not base64!!!"))
        assertNull(SealedValue.decode("v1:"))
    }

    @Test
    fun encodeHasNoLineBreaks() {
        val text = SealedValue.encode(ByteArray(200) { it.toByte() })
        assertFalse(text.contains('\n') || text.contains('\r'))
    }
}
