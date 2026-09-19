package com.lucent.app.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A continuous, opaque colour field; its cost depends on the texture, never the display size. */
internal class DiffuseGradientField(val edge: Int) {
    init { require(edge in 2..128) }

    private val pixels = IntArray(edge * edge)
    private val xSin = Array(3) { DoubleArray(edge) }
    private val xCos = Array(3) { DoubleArray(edge) }
    private val frequencies = doubleArrayOf(3.1, -2.7, 2.3)
    private val yFrequencies = doubleArrayOf(2.5, 3.3, -2.9)
    private val periods = doubleArrayOf(47.0, 61.0, 79.0)
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
    fun render(seconds: Double, palette: IntArray, backdrop: Int, dark: Boolean): IntArray {
        require(seconds.isFinite() && seconds >= 0.0)
        // Dark backdrops carry more colour than light ones, where a strong wash would muddy the
        // glass panels that sit on top of it. Both stay a soft field, never a saturated poster.
        val strength = if (dark) 0.55 else 0.45
        val br = (backdrop ushr 16) and 255
        val bg = (backdrop ushr 8) and 255
        val bb = backdrop and 255
        for (i in 0..2) {
            val color = if (palette.isEmpty()) backdrop else palette[i % palette.size]
            val amount = strength * ((color ushr 24) and 255) / 255.0
            red[i] = br + (((color ushr 16) and 255) - br) * amount
            green[i] = bg + (((color ushr 8) and 255) - bg) * amount
            blue[i] = bb + ((color and 255) - bb) * amount
        }
        for (y in 0 until edge) {
            for (i in 0..2) {
                val angle = y.toDouble() / (edge - 1) * yFrequencies[i] + phases[i] +
                    (seconds % periods[i]) / periods[i] * 2.0 * PI
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
}
