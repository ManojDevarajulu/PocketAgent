package dev.pocketagent.device

/**
 * Where to draw the coordinate grid on a screenshot. The picture is scaled down but taps use real screen pixels,
 * so every line carries its real pixel value and the model can read a position off the image without doing the maths.
 */
object GridLines {
    private val STEPS = listOf(50, 100, 200, 250, 500, 1000)

    /** One round step for both axes, so the squares are square: the largest of [STEPS] that still gives about [target] lines on the long side. */
    fun stepFor(longSide: Int, target: Int = 12): Int = STEPS.firstOrNull { longSide / it <= target } ?: STEPS.last()

    /** Real pixel positions of the lines for a side of [extent] pixels, every [step] pixels. */
    fun marks(extent: Int, step: Int): List<Int> =
        if (extent <= 0 || step <= 0) emptyList() else (step until extent step step).toList()
}
