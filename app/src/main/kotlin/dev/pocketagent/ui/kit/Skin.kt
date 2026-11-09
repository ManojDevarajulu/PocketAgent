package dev.pocketagent.ui.kit

import android.content.Context
import android.content.res.Configuration
import dev.pocketagent.R

/**
 * Colour skins. The default (tonal blue) lives in tokens.xml; the monochrome "ink" skin overrides it here so the two
 * can be compared on a real phone before one is chosen. Only the preview switches skins; the app ships one.
 */
object Skin {
    var ink = false

    private fun n(v: Long) = v.toInt()

    private val light = mapOf(
        R.color.pa_bg to n(0xFFF5F5F3), R.color.pa_card to n(0xFFFFFFFF), R.color.pa_line to n(0xFFE6E6E3),
        R.color.pa_ink to n(0xFF111111), R.color.pa_muted to n(0xFF6F6F6B),
        R.color.pa_blue to n(0xFF111111), R.color.pa_on_blue to n(0xFFFFFFFF),
        R.color.pa_tile to n(0xFF111111), R.color.pa_on_tile to n(0xFFFFFFFF), R.color.pa_tile_icon to n(0xFFFFFFFF),
        R.color.pa_toggle to n(0xFF30A46C), R.color.pa_icon to n(0xFF111111), R.color.pa_bar to n(0xFFFFFFFF),
        R.color.pa_fill to n(0x1F767680),
    )
    private val dark = mapOf(
        R.color.pa_bg to n(0xFF0B0B0C), R.color.pa_card to n(0xFF151516), R.color.pa_line to n(0xFF262628),
        R.color.pa_ink to n(0xFFF2F2F0), R.color.pa_muted to n(0xFF8E8E8A),
        R.color.pa_blue to n(0xFFF2F2F0), R.color.pa_on_blue to n(0xFF0B0B0C),
        R.color.pa_tile to n(0xFFEDEDEA), R.color.pa_on_tile to n(0xFF0B0B0C), R.color.pa_tile_icon to n(0xFF0B0B0C),
        R.color.pa_toggle to n(0xFF30A46C), R.color.pa_icon to n(0xFFF2F2F0), R.color.pa_bar to n(0xFF121213),
        R.color.pa_fill to n(0x3D767680),
    )

    /** The override for [id] in the current skin, or null to use the XML token. */
    fun resolve(c: Context, id: Int): Int? {
        if (!ink) return null
        val night = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return (if (night) dark else light)[id]
    }
}
