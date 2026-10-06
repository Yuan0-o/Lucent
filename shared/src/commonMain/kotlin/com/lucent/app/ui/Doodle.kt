package com.lucent.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.*

object Doodle {

    val COLORS: List<Color> = listOf(
        Color(0xFF1B1B1F),
        Color(0xFFE53935),
        Color(0xFF1E88E5),
        Color(0xFF43A047),
        Color(0xFFFFB300)
    )

    val WIDTHS: List<Float> = listOf(0.004f, 0.010f, 0.022f)

    data class Stroke(
        val color: Int,
        val width: Float,
        val points: List<Offset>
    )

    fun parse(json: String?): List<Stroke> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = Json.parseToJsonElement(json).jsonArray
            (0 until arr.size).mapNotNull { i ->
                val o = try { arr[i].jsonObject } catch (e: Exception) { return@mapNotNull null }
                val pts = try { o["p"]?.jsonArray } catch (e: Exception) { return@mapNotNull null } ?: return@mapNotNull null
                val points = ArrayList<Offset>(pts.size / 2)
                var k = 0
                while (k + 1 < pts.size) {
                    points.add(Offset((pts[k].jsonPrimitive.doubleOrNull ?: 0.0).toFloat(), (pts[k + 1].jsonPrimitive.doubleOrNull ?: 0.0).toFloat()))
                    k += 2
                }
                if (points.isEmpty()) null
                else Stroke(o["c"]?.jsonPrimitive?.intOrNull ?: 0xFF1B1B1F.toInt(), (o["w"]?.jsonPrimitive?.doubleOrNull ?: 0.01).toFloat(), points)
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun serialize(strokes: List<Stroke>): String {
        return buildJsonArray {
            strokes.forEach { s ->
                val pts = buildJsonArray {
                    s.points.forEach { add(it.x.toDouble()); add(it.y.toDouble()) }
                }
                add(buildJsonObject { put("c", s.color); put("w", s.width.toDouble()); put("p", pts) })
            }
        }.toString()
    }

    fun isEmpty(json: String?): Boolean = parse(json).isEmpty()
}

object DoodlePages {

    fun parse(json: String?): List<String> {
        val raw = json?.trim().orEmpty()
        if (raw.isEmpty()) return listOf("")
        if (raw.startsWith("[")) return listOf(raw)
        return try {
            val arr = Json.parseToJsonElement(raw).jsonObject["pages"]?.jsonArray ?: return listOf("")
            val out = ArrayList<String>(arr.size)
            for (i in 0 until arr.size) out.add(arr[i].jsonPrimitive.content)
            if (out.isEmpty()) listOf("") else out
        } catch (_: Throwable) {
            listOf(raw)
        }
    }

    fun serialize(pages: List<String>): String {
        val kept = if (pages.isEmpty()) listOf("") else pages
        if (kept.size == 1 && Doodle.isEmpty(kept.first())) return ""
        if (kept.size == 1) return kept.first()
        val arr = buildJsonArray { kept.forEach { add(it) } }
        return buildJsonObject { put("pages", arr) }.toString()
    }

    fun drawnCount(pages: List<String>): Int = pages.count { !Doodle.isEmpty(it) }

    fun isEmpty(json: String?): Boolean = drawnCount(parse(json)) == 0

    fun pruneEmpty(json: String?): String = serialize(parse(json).filter { !Doodle.isEmpty(it) })
}
