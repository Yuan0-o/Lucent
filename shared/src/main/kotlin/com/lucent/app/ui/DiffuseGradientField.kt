package com.lucent.app.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

internal class DiffuseGradientField(val edge: Int) {
    init { require(edge in 2..128) }

    private val pixels = IntArray(edge * edge)
    private val positionX = DoubleArray(edge)
    private val bandCosX = DoubleArray(edge)
    private val bandSinX = DoubleArray(edge)
    private val washX = DoubleArray(edge)
    private val zoneCentre = DoubleArray(3)
    private val zoneCos = DoubleArray(3)
    private val zoneSin = DoubleArray(3)
    private val red = DoubleArray(3)
    private val green = DoubleArray(3)
    private val blue = DoubleArray(3)
    private var baseRed = 0
    private var baseGreen = 0
    private var baseBlue = 0

    init {
        for (x in 0 until edge) {
            val u = x.toDouble() / (edge - 1)
            positionX[x] = u
            washX[x] = u * WASH_X
            val angle = PI * u / BAND
            bandCosX[x] = cos(angle)
            bandSinX[x] = sin(angle)
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
        prepare(spatialSeconds, colourSeconds, palette, backdrop, strength)
        val drift = WASH_DRIFT * sin(2.0 * PI * (spatialSeconds % WASH_PERIOD) / WASH_PERIOD)
        for (y in 0 until edge) {
            val v = y.toDouble() / (edge - 1)
            val rowAngle = PI * v / BAND
            val bandCosY = cos(rowAngle)
            val bandSinY = sin(rowAngle)
            val washY = v * WASH_Y
            for (x in 0 until edge) {
                val cosA = bandCosX[x] * bandCosY - bandSinX[x] * bandSinY
                val sinA = bandSinX[x] * bandCosY + bandCosX[x] * bandSinY
                val position = positionX[x] + v
                var weightSum = 0.0
                var rSum = 0.0
                var gSum = 0.0
                var bSum = 0.0
                for (i in 0..2) {
                    val weight = zoneWeight(position - zoneCentre[i], cosA, sinA, i)
                    weightSum += weight
                    rSum += weight * red[i]
                    gSum += weight * green[i]
                    bSum += weight * blue[i]
                }
                val ramp = (washX[x] + washY + drift) / WASH_SPAN
                val eased = if (ramp <= 0.0) 0.0 else if (ramp >= 1.0) 1.0 else ramp * ramp * (3.0 - 2.0 * ramp)
                val wash = 1.0 - WASH_DEPTH * eased
                val scale = wash / weightSum
                val r = shade(baseRed, rSum, scale, wash)
                val g = shade(baseGreen, gSum, scale, wash)
                val b = shade(baseBlue, bSum, scale, wash)
                pixels[y * edge + x] = (255 shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return pixels
    }

    private fun prepare(
        spatialSeconds: Double,
        colourSeconds: Double,
        palette: IntArray,
        backdrop: Int,
        strength: Double
    ) {
        baseRed = (backdrop ushr 16) and 255
        baseGreen = (backdrop ushr 8) and 255
        baseBlue = backdrop and 255
        for (i in 0..2) {
            val color = if (palette.isEmpty()) backdrop else paletteAt(palette, i, colourSeconds)
            val amount = strength * ((color ushr 24) and 255) / 255.0
            red[i] = baseRed + (((color ushr 16) and 255) - baseRed) * amount
            green[i] = baseGreen + (((color ushr 8) and 255) - baseGreen) * amount
            blue[i] = baseBlue + ((color and 255) - baseBlue) * amount
            val centre = ZONE_CENTRES[i] + ZONE_SWING *
                sin(2.0 * PI * (spatialSeconds % ZONE_PERIODS[i]) / ZONE_PERIODS[i] + ZONE_PHASES[i])
            zoneCentre[i] = centre
            val angle = PI * centre / BAND
            zoneCos[i] = cos(angle)
            zoneSin[i] = sin(angle)
        }
    }

    private fun zoneWeight(offset: Double, cosA: Double, sinA: Double, index: Int): Double {
        if (offset <= -BAND || offset >= BAND) return 0.0
        return 0.5 + 0.5 * (cosA * zoneCos[index] + sinA * zoneSin[index])
    }

    private fun shade(base: Int, sum: Double, scale: Double, wash: Double): Int =
        (base * (1.0 - wash) + sum * scale).roundToInt().coerceIn(0, 255)

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

        const val BAND = 0.96

        const val ZONE_SWING = 0.11

        const val WASH_X = 0.34

        const val WASH_Y = 0.94

        const val WASH_SPAN = WASH_X + WASH_Y

        const val WASH_DEPTH = 0.70

        const val WASH_DRIFT = 0.08

        val ZONE_CENTRES = doubleArrayOf(0.14, 1.02, 1.90)

        val ZONE_PHASES = doubleArrayOf(0.0, 2.1, 4.2)

        val ZONE_PERIODS = doubleArrayOf(13.0 / SPEED, 17.0 / SPEED, 23.0 / SPEED)

        val ROTATION_PERIODS = doubleArrayOf(23.0 / SPEED, 31.0 / SPEED, 41.0 / SPEED)

        val WASH_PERIOD = 47.0 / SPEED
    }
}
