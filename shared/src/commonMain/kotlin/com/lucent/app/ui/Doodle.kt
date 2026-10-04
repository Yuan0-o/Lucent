package com.lucent.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

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
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val pts = o.optJSONArray("p") ?: return@mapNotNull null
                val points = ArrayList<Offset>(pts.length() / 2)
                var k = 0
                while (k + 1 < pts.length()) {
                    points.add(Offset(pts.optDouble(k).toFloat(), pts.optDouble(k + 1).toFloat()))
                    k += 2
                }
                if (points.isEmpty()) null
                else Stroke(o.optInt("c", 0xFF1B1B1F.toInt()), o.optDouble("w", 0.01).toFloat(), points)
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun serialize(strokes: List<Stroke>): String {
        val arr = JSONArray()
        strokes.forEach { s ->
            val pts = JSONArray()
            s.points.forEach { pts.put(it.x.toDouble()); pts.put(it.y.toDouble()) }
            arr.put(JSONObject().put("c", s.color).put("w", s.width.toDouble()).put("p", pts))
        }
        return arr.toString()
    }

    fun isEmpty(json: String?): Boolean = parse(json).isEmpty()
}

object DoodlePages {

    fun parse(json: String?): List<String> {
        val raw = json?.trim().orEmpty()
        if (raw.isEmpty()) return listOf("")
        if (raw.startsWith("[")) return listOf(raw)
        return try {
            val arr = JSONObject(raw).optJSONArray("pages") ?: return listOf("")
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) out.add(arr.optString(i, ""))
            if (out.isEmpty()) listOf("") else out
        } catch (_: Throwable) {
            listOf(raw)
        }
    }

    fun serialize(pages: List<String>): String {
        val kept = if (pages.isEmpty()) listOf("") else pages
        if (kept.size == 1 && Doodle.isEmpty(kept.first())) return ""
        if (kept.size == 1) return kept.first()
        val arr = JSONArray()
        kept.forEach { arr.put(it) }
        return JSONObject().put("pages", arr).toString()
    }

    fun drawnCount(pages: List<String>): Int = pages.count { !Doodle.isEmpty(it) }

    fun isEmpty(json: String?): Boolean = drawnCount(parse(json)) == 0

    fun pruneEmpty(json: String?): String = serialize(parse(json).filter { !Doodle.isEmpty(it) })
}
