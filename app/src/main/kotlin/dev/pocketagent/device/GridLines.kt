package dev.pocketagent.device

/**
 * Where to draw the coordinate grid on a screenshot. The picture is scaled down but taps use real screen pixels,
 * so every line carries its real pixel value and the model can read a position off the image without doing the maths.
 */
object GridLines {
    private val STEPS = listOf(50, 100, 200, 250, 500, 1000)

    /** Real pixel positions of the lines for a side of [extent] pixels: round numbers, about [target] of them. */
    fun marks(extent: Int, target: Int = 8): List<Int> {
        if (extent <= 0) return emptyList()
        val step = STEPS.firstOrNull { extent / it <= target } ?: STEPS.last()
        return (step until extent step step).toList()
    }
}
