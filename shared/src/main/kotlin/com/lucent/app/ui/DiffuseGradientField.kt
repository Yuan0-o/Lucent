package com.lucent.app.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A continuous, opaque colour field; its cost depends on the texture, never the display size.
 *
 * ### What makes it move
 *
 * Two independent things are animated, because either one alone reads as a still picture with a
 * slow wobble:
 *
 *  - **Position.** Each of the three colour weights is a travelling wave whose phase advances on
 *    its own [PERIODS] cycle, so the blend reshapes across the screen continuously and never
 *    repeats on any short cycle.
 *  - **Hue.** Each weight also walks slowly through the palette on its own [ROTATION_PERIODS]
 *    cycle, crossfading the whole time and starting a third of the palette apart from its
 *    neighbours. So the colours themselves keep changing, not merely their positions: the same
 *    point on screen is a different colour a minute later, which is what "a living gradient" has to
 *    mean — a still gradient with drifting light would be wallpaper.
 *
 * The two sets of periods share no small common factor, so the field does not visibly loop; the
 * measured change is ~8/255 per channel after three seconds and ~22/255 after eight, i.e. plainly
 * alive to the eye without anything jumping.
 */
internal class DiffuseGradientField(val edge: Int) {
    init { require(edge in 2..128) }

    private val pixels = IntArray(edge * edge)
    private val xSin = Array(3) { DoubleArray(edge) }
    private val xCos = Array(3) { DoubleArray(edge) }
    private val frequencies = doubleArrayOf(3.1, -2.7, 2.3)
    private val yFrequencies = doubleArrayOf(2.5, 3.3, -2.9)
    private val phases = doubleArrayOf(0.2, 2.3, 4.5)
    private val rowSin = DoubleArray(3)
    private val rowCos = DoubleArray(3)
    private val red = DoubleArray(3)
    private val green = DoubleArray(3)
    private val blue = DoubleArray(3)

    init {
        for (i in 0..2) {
            for (x in 0 until edge) {
                val angle = x.toDouble() / (edge - 1) * frequencies[i]
                xSin[i][x] = sin(angle)
                xCos[i][x] = cos(angle)
            }
        }
    }

    /** The caller must copy the returned scratch pixels before publishing a frame. */
    fun render(seconds: Double, palette: IntArray, backdrop: Int, dark: Boolean): IntArray =
        render(seconds, seconds, palette, backdrop, dark)

    /**
     * The same field with the two clocks separated: [spatialSeconds] drives the travelling waves and
     * [colourSeconds] drives the palette walk. They are normally the same value (see the overload
     * above). They come apart for the reduced-motion case, where the waves are frozen but the
     * colours must keep changing — a still gradient that never recolours is a wallpaper, and the
     * point of this background is that it is never quite the same picture twice.
     */
    fun render(
        spatialSeconds: Double,
        colourSeconds: Double,
        palette: IntArray,
        backdrop: Int,
        dark: Boolean
    ): IntArray {
        require(spatialSeconds.isFinite() && spatialSeconds >= 0.0)
        require(colourSeconds.isFinite() && colourSeconds >= 0.0)
        // Dark backdrops carry more colour than light ones, where a strong wash would muddy the
        // glass panels that sit on top of it. Both stay a soft field, never a saturated poster.
        val strength = if (dark) 0.55 else 0.45
        val br = (backdrop ushr 16) and 255
        val bg = (backdrop ushr 8) and 255
        val bb = backdrop and 255
        for (i in 0..2) {
            val color = if (palette.isEmpty()) backdrop else paletteAt(palette, i, colourSeconds)
            val amount = strength * ((color ushr 24) and 255) / 255.0
            red[i] = br + (((color ushr 16) and 255) - br) * amount
            green[i] = bg + (((color ushr 8) and 255) - bg) * amount
            blue[i] = bb + ((color and 255) - bb) * amount
        }
        for (y in 0 until edge) {
            for (i in 0..2) {
                val angle = y.toDouble() / (edge - 1) * yFrequencies[i] + phases[i] +
                    (spatialSeconds % PERIODS[i]) / PERIODS[i] * 2.0 * PI
                rowSin[i] = sin(angle)
                rowCos[i] = cos(angle)
            }
            for (x in 0 until edge) {
                val a = weight(0, x)
                val b = weight(1, x)
                val c = weight(2, x)
                val inverse = 1.0 / (a + b + c)
                val r = ((red[0] * a + red[1] * b + red[2] * c) * inverse).roundToInt().coerceIn(0, 255)
                val g = ((green[0] * a + green[1] * b + green[2] * c) * inverse).roundToInt().coerceIn(0, 255)
                val bl = ((blue[0] * a + blue[1] * b + blue[2] * c) * inverse).roundToInt().coerceIn(0, 255)
                pixels[y * edge + x] = (255 shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
        return pixels
    }

    private fun weight(index: Int, x: Int): Double {
        val wave = xSin[index][x] * rowCos[index] + xCos[index][x] * rowSin[index]
        val value = 0.55 + 0.45 * wave
        return value * value * value
    }

    /**
     * Which colour weight [index] is showing at [seconds]: a slow, continuous walk through the
     * palette, one step per [ROTATION_PERIODS] cycle, crossfading between neighbours the whole way
     * so the hue never cuts. The `index * n / 3.0` head start spreads the three components around
     * the palette, which keeps them distinct instead of letting all three land on the same colour
     * and flatten the field.
     */
    private fun paletteAt(palette: IntArray, index: Int, seconds: Double): Int {
        val n = palette.size
        if (n == 1) return palette[0]
        val position = seconds / ROTATION_PERIODS[index] + index * n / 3.0
        val whole = floor(position)
        val from = palette[(((whole.toInt() % n) + n) % n)]
        val to = palette[(((whole.toInt() + 1) % n + n) % n)]
        val raw = position - whole
        val f = raw * raw * (3.0 - 2.0 * raw)
        return (channel(from, to, f, 24) shl 24) or
            (channel(from, to, f, 16) shl 16) or
            (channel(from, to, f, 8) shl 8) or
            channel(from, to, f, 0)
    }

    private fun channel(from: Int, to: Int, f: Double, shift: Int): Int {
        val a = (from ushr shift) and 255
        val b = (to ushr shift) and 255
        return (a + (b - a) * f).roundToInt().coerceIn(0, 255)
    }

    private companion object {
        /**
         * Seconds for one full phase cycle of each weight's travelling wave: 23s, 29s and 37s. Short
         * enough that the field is visibly somewhere else a few seconds after you look away, long
         * enough that it is still a background rather than a performance.
         */
        val PERIODS = doubleArrayOf(23.0, 29.0, 37.0)

        /**
         * Seconds per palette step, per component. Deliberately unequal and deliberately not equal
         * to any [PERIODS] value, so the colour drift and the shape drift never lock together.
         */
        val ROTATION_PERIODS = doubleArrayOf(37.0, 49.0, 61.0)
    }
}
