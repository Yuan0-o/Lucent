package com.lucent.app.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

internal class DiffuseGradientField(val edge: Int) {
    init { require(edge in 2..128) }

    private val pixels = IntArray(edge * edge)
    private val xSin = Array(3) { DoubleArray(edge) }
    private val xCos = Array(3) { DoubleArray(edge) }
    private val fineXSin = Array(3) { DoubleArray(edge) }
    private val fineXCos = Array(3) { DoubleArray(edge) }
    private val frequencies = doubleArrayOf(3.1, -2.7, 2.3)
    private val yFrequencies = doubleArrayOf(2.5, 3.3, -2.9)
    private val phases = doubleArrayOf(0.2, 2.3, 4.5)
    private val fineFrequencies = doubleArrayOf(7.1, -6.3, 6.6)
    private val fineYFrequencies = doubleArrayOf(6.3, 7.7, -6.1)
    private val finePhases = doubleArrayOf(1.1, 3.7, 5.3)
    private val rowSin = DoubleArray(3)
    private val rowCos = DoubleArray(3)
    private val fineRowSin = DoubleArray(3)
    private val fineRowCos = DoubleArray(3)
    private val red = DoubleArray(3)
    private val green = DoubleArray(3)
    private val blue = DoubleArray(3)

    init {
        for (i in 0..2) {
            for (x in 0 until edge) {
                val angle = x.toDouble() / (edge - 1) * frequencies[i]
                xSin[i][x] = sin(angle)
                xCos[i][x] = cos(angle)
                val fineAngle = x.toDouble() / (edge - 1) * fineFrequencies[i]
                fineXSin[i][x] = sin(fineAngle)
                fineXCos[i][x] = cos(fineAngle)
            }
        }
    }

    fun render(seconds: Double, palette: IntArray, backdrop: Int, dark: Boolean): IntArray =
        render(seconds, seconds, palette, backdrop, dark)

    fun render(
        spatialSeconds: Double,
        colourSeconds: Double,
        palette: IntArray,
        backdrop: Int,
        dark: Boolean
    ): IntArray {
        require(spatialSeconds.isFinite() && spatialSeconds >= 0.0)
        require(colourSeconds.isFinite() && colourSeconds >= 0.0)
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
        val fusion = fusionFactor(spatialSeconds)
        for (y in 0 until edge) {
            for (i in 0..2) {
                val angle = y.toDouble() / (edge - 1) * yFrequencies[i] + phases[i] +
                    (spatialSeconds % PERIODS[i]) / PERIODS[i] * 2.0 * PI
                rowSin[i] = sin(angle)
                rowCos[i] = cos(angle)
                val fineAngle = y.toDouble() / (edge - 1) * fineYFrequencies[i] + finePhases[i] +
                    (spatialSeconds % FINE_PERIODS[i]) / FINE_PERIODS[i] * 2.0 * PI
                fineRowSin[i] = sin(fineAngle)
                fineRowCos[i] = cos(fineAngle)
            }
            for (x in 0 until edge) {
                val a = weight(0, x, fusion)
                val b = weight(1, x, fusion)
                val c = weight(2, x, fusion)
                val inverse = 1.0 / (a + b + c)
                val r = ((red[0] * a + red[1] * b + red[2] * c) * inverse).roundToInt().coerceIn(0, 255)
                val g = ((green[0] * a + green[1] * b + green[2] * c) * inverse).roundToInt().coerceIn(0, 255)
                val bl = ((blue[0] * a + blue[1] * b + blue[2] * c) * inverse).roundToInt().coerceIn(0, 255)
                pixels[y * edge + x] = (255 shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
        return pixels
    }

    private fun fusionFactor(spatialSeconds: Double): Double {
        val cycle = (spatialSeconds % FUSION_PERIOD) / FUSION_PERIOD
        val raw = 0.5 + 0.5 * sin(cycle * 2.0 * PI + FUSION_PHASE)
        return raw * raw * (3.0 - 2.0 * raw)
    }

    private fun weight(index: Int, x: Int, fusion: Double): Double {
        val wave = xSin[index][x] * rowCos[index] + xCos[index][x] * rowSin[index]
        val detail = fineXSin[index][x] * fineRowCos[index] + fineXCos[index][x] * fineRowSin[index]
        val value = 0.55 + 0.45 * wave
        val cubic = value * value * value
        val square = value * value
        return (cubic + (square - cubic) * fusion) * (1.0 + DETAIL_DEPTH * detail)
    }

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
        const val SPEED = 1.38

        const val DETAIL_DEPTH = 0.16

        const val FUSION_PHASE = 1.9

        val PERIODS = doubleArrayOf(23.0 / SPEED, 29.0 / SPEED, 37.0 / SPEED)

        val ROTATION_PERIODS = doubleArrayOf(37.0 / SPEED, 49.0 / SPEED, 61.0 / SPEED)

        val FINE_PERIODS = doubleArrayOf(11.0 / SPEED, 15.0 / SPEED, 19.0 / SPEED)

        val FUSION_PERIOD = 53.0 / SPEED
    }
}
