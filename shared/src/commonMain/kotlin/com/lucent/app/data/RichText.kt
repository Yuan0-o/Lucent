package com.lucent.app.data

import org.json.JSONArray
import org.json.JSONObject

data class RichSpan(
    val start: Int,
    val end: Int,
    val kind: Kind,
    val color: Int = 0
) {
    enum class Kind {
        LIGHT,
        BOLD,
        ITALIC,
        SIZE,
        HIGHLIGHT,
        COLOR;

        companion object {
            fun fromName(name: String): Kind? = entries.firstOrNull { it.name == name }
        }
    }

    val length: Int get() = end - start

    val isEmpty: Boolean get() = end <= start
}

object RichText {

    const val HIGHLIGHT_COLORS = 8

    const val TEXT_COLORS = 9

    const val TEXT_SIZES = 5

    const val TEXT_SIZE_DEFAULT = 0

    val TEXT_SIZE_SCALES = floatArrayOf(1f, 0.8f, 1.25f, 1.6f, 2.1f)

    fun textSizeScale(index: Int): Float = TEXT_SIZE_SCALES[index.coerceIn(0, TEXT_SIZE_SCALES.size - 1)]

    const val TEXT_COLOR_DEFAULT = 0
    val TEXT_COLOR_ARGB = intArrayOf(
        0,
        0xFF43A047.toInt(),
        0xFFF9A825.toInt(),
        0xFF1E88E5.toInt(),
        0xFFE53935.toInt(),
        0xFF00897B.toInt(),
        0xFF8E24AA.toInt(),
        0xFFF4511E.toInt(),
        0xFFD81B60.toInt()
    )

    const val EMPTY = ""


    fun decode(json: String): List<RichSpan> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<RichSpan>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = RichSpan.Kind.fromName(o.optString("k")) ?: continue
                val maxIndex = when (kind) {
                    RichSpan.Kind.SIZE -> TEXT_SIZES - 1
                    RichSpan.Kind.COLOR -> TEXT_COLORS - 1
                    RichSpan.Kind.HIGHLIGHT -> HIGHLIGHT_COLORS - 1
                    else -> 0
                }
                val span = RichSpan(
                    start = o.optInt("s", 0),
                    end = o.optInt("e", 0),
                    kind = kind,
                    color = o.optInt("c", 0).coerceIn(0, maxIndex)
                )
                if (!span.isEmpty) out.add(span)
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun encode(spans: List<RichSpan>): String {
        val kept = normalise(spans)
        if (kept.isEmpty()) return EMPTY
        val arr = JSONArray()
        kept.forEach { s ->
            arr.put(
                JSONObject()
                    .put("s", s.start)
                    .put("e", s.end)
                    .put("k", s.kind.name)
                    .put("c", s.color)
            )
        }
        return arr.toString()
    }


    fun normalise(spans: List<RichSpan>): List<RichSpan> {
        val sorted = spans.filterNot { it.isEmpty }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.color }, { it.start }))
        val out = ArrayList<RichSpan>(sorted.size)
        for (s in sorted) {
            val last = out.lastOrNull()
            if (last != null && last.kind == s.kind && last.color == s.color && s.start <= last.end) {
                out[out.size - 1] = last.copy(end = maxOf(last.end, s.end))
            } else {
                out.add(s)
            }
        }
        return out
    }

    fun covers(spans: List<RichSpan>, start: Int, end: Int, kind: RichSpan.Kind, color: Int = 0): Boolean {
        if (end <= start) return false
        var cursor = start
        for (s in normalise(spans)) {
            if (s.kind != kind) continue
            if ((kind == RichSpan.Kind.HIGHLIGHT || kind == RichSpan.Kind.SIZE) && s.color != color) continue
            if (s.start > cursor) break
            if (s.end > cursor) cursor = s.end
            if (cursor >= end) return true
        }
        return false
    }

    fun toggle(
        spans: List<RichSpan>,
        start: Int,
        end: Int,
        kind: RichSpan.Kind,
        color: Int = 0
    ): List<RichSpan> {
        if (end <= start) return spans
        return if (covers(spans, start, end, kind, color)) {
            remove(spans, start, end, kind, if (kind == RichSpan.Kind.HIGHLIGHT) color else null)
        } else {
            val cleared = when (kind) {
                RichSpan.Kind.LIGHT -> remove(spans, start, end, RichSpan.Kind.BOLD, null)
                RichSpan.Kind.BOLD -> remove(spans, start, end, RichSpan.Kind.LIGHT, null)
                RichSpan.Kind.HIGHLIGHT -> remove(spans, start, end, RichSpan.Kind.HIGHLIGHT, null)
                RichSpan.Kind.COLOR -> remove(spans, start, end, RichSpan.Kind.COLOR, null)
                RichSpan.Kind.SIZE -> remove(spans, start, end, RichSpan.Kind.SIZE, null)
                RichSpan.Kind.ITALIC -> spans
            }
            normalise(cleared + RichSpan(start, end, kind, color))
        }
    }

    fun remove(
        spans: List<RichSpan>,
        start: Int,
        end: Int,
        kind: RichSpan.Kind,
        color: Int?
    ): List<RichSpan> {
        val out = ArrayList<RichSpan>(spans.size + 2)
        for (s in spans) {
            val applies = s.kind == kind && (color == null || s.color == color)
            if (!applies || s.end <= start || s.start >= end) {
                out.add(s); continue
            }
            if (s.start < start) out.add(s.copy(end = start))
            if (s.end > end) out.add(s.copy(start = end))
        }
        return normalise(out)
    }

    fun afterEdit(spans: List<RichSpan>, at: Int, oldLength: Int, newLength: Int): List<RichSpan> {
        if (oldLength == 0 && newLength == 0) return spans
        val delta = newLength - oldLength
        val removedEnd = at + oldLength
        val out = ArrayList<RichSpan>(spans.size)
        for (s in spans) {
            val start = when {
                s.start >= removedEnd -> s.start + delta
                s.start > at -> at
                else -> s.start
            }
            val end = when {
                s.end >= removedEnd -> s.end + delta
                s.end > at -> at
                else -> s.end
            }
            val moved = s.copy(start = start.coerceAtLeast(0), end = end.coerceAtLeast(0))
            if (!moved.isEmpty) out.add(moved)
        }
        return normalise(out)
    }

    fun reconcile(spans: List<RichSpan>, length: Int): List<RichSpan> =
        normalise(spans.mapNotNull { s ->
            if (s.start >= length) null
            else s.copy(end = minOf(s.end, length)).takeIf { !it.isEmpty }
        })

    fun kindsCovering(spans: List<RichSpan>, start: Int, end: Int): Set<RichSpan.Kind> =
        RichSpan.Kind.entries
            .filter { it != RichSpan.Kind.HIGHLIGHT && it != RichSpan.Kind.SIZE && it != RichSpan.Kind.COLOR && covers(spans, start, end, it) }
            .toSet()

    fun colorCovering(spans: List<RichSpan>, start: Int, end: Int): Int? =
        (0 until TEXT_COLORS).firstOrNull { covers(spans, start, end, RichSpan.Kind.COLOR, it) }

    fun highlightCovering(spans: List<RichSpan>, start: Int, end: Int): Int? =
        (0 until HIGHLIGHT_COLORS).firstOrNull { covers(spans, start, end, RichSpan.Kind.HIGHLIGHT, it) }

    fun sizeCovering(spans: List<RichSpan>, start: Int, end: Int): Int? =
        (0 until TEXT_SIZES).firstOrNull { covers(spans, start, end, RichSpan.Kind.SIZE, it) }

    fun applyEdit(
        spans: List<RichSpan>,
        old: String,
        new: String,
        pendingKinds: Set<RichSpan.Kind> = emptySet(),
        pendingHighlight: Int? = null,
        pendingColor: Int? = null,
        pendingSize: Int? = null,
        pendingExplicit: Boolean = false
    ): List<RichSpan> {
        if (old == new) return reconcile(spans, new.length)
        val limit = minOf(old.length, new.length)
        var prefix = 0
        while (prefix < limit && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < limit - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        val removed = old.length - prefix - suffix
        val inserted = new.length - prefix - suffix
        var out = afterEdit(spans, prefix, removed, inserted)
        if (inserted > 0 && pendingExplicit) {
            RichSpan.Kind.entries.forEach { kind ->
                if (kind !in pendingKinds && kind != RichSpan.Kind.HIGHLIGHT) {
                    out = remove(out, prefix, prefix + inserted, kind, null)
                }
            }
            if (pendingHighlight == null) {
                out = remove(out, prefix, prefix + inserted, RichSpan.Kind.HIGHLIGHT, null)
            }
            if (pendingColor == null) {
                out = remove(out, prefix, prefix + inserted, RichSpan.Kind.COLOR, null)
            }
            if (pendingSize == null) {
                out = remove(out, prefix, prefix + inserted, RichSpan.Kind.SIZE, null)
            }
        }
        if (inserted > 0) {
            pendingKinds.forEach { kind ->
                out = when (kind) {
                    RichSpan.Kind.BOLD -> remove(out, prefix, prefix + inserted, RichSpan.Kind.LIGHT, null)
                    RichSpan.Kind.LIGHT -> remove(out, prefix, prefix + inserted, RichSpan.Kind.BOLD, null)
                    else -> out
                }
                out = normalise(out + RichSpan(prefix, prefix + inserted, kind, 0))
            }
            if (pendingHighlight != null) {
                out = remove(out, prefix, prefix + inserted, RichSpan.Kind.HIGHLIGHT, null)
                out = normalise(out + RichSpan(prefix, prefix + inserted, RichSpan.Kind.HIGHLIGHT, pendingHighlight))
            }
            if (pendingColor != null) {
                out = remove(out, prefix, prefix + inserted, RichSpan.Kind.COLOR, null)
                out = normalise(out + RichSpan(prefix, prefix + inserted, RichSpan.Kind.COLOR, pendingColor))
            }
            if (pendingSize != null) {
                out = remove(out, prefix, prefix + inserted, RichSpan.Kind.SIZE, null)
                out = normalise(out + RichSpan(prefix, prefix + inserted, RichSpan.Kind.SIZE, pendingSize))
            }
        }
        return reconcile(out, new.length)
    }

    fun load(json: String, body: String): List<RichSpan> = reconcile(decode(json), body.length)


    val HIGHLIGHT_ARGB = intArrayOf(
        0xFFFFF176.toInt(),
        0xFF81C784.toInt(),
        0xFF4FC3F7.toInt(),
        0xFFF48FB1.toInt(),
        0xFFFFB74D.toInt(),
        0xFFBA68C8.toInt(),
        0xFF4DB6AC.toInt(),
        0xFFE57373.toInt()
    )

    data class StyledRun(
        val text: String,
        val bold: Boolean,
        val light: Boolean,
        val italic: Boolean,
        val highlight: Int,
        val color: Int = TEXT_COLOR_DEFAULT,
        val sizeScale: Float = 1f
    ) {
        val plain: Boolean
            get() = !bold && !light && !italic && highlight < 0 && color == TEXT_COLOR_DEFAULT && sizeScale == 1f
    }

    fun textColorArgb(index: Int): Int? =
        if (index <= TEXT_COLOR_DEFAULT || index >= TEXT_COLOR_ARGB.size) null
        else TEXT_COLOR_ARGB[index]

    fun runsFor(line: String, lineStart: Int, spans: List<RichSpan>): List<StyledRun> {
        if (line.isEmpty()) return listOf(StyledRun("", false, false, false, -1))
        if (spans.isEmpty()) return listOf(StyledRun(line, false, false, false, -1))

        data class CharStyle(
            val bold: Boolean,
            val light: Boolean,
            val italic: Boolean,
            val highlight: Int,
            val color: Int,
            val size: Int
        )

        fun styleAt(abs: Int): CharStyle {
            var bold = false; var light = false; var italic = false; var hl = -1
            var col = TEXT_COLOR_DEFAULT
            var size = TEXT_SIZE_DEFAULT
            spans.forEach { s ->
                if (abs >= s.start && abs < s.end) when (s.kind) {
                    RichSpan.Kind.BOLD -> bold = true
                    RichSpan.Kind.LIGHT -> light = true
                    RichSpan.Kind.ITALIC -> italic = true
                    RichSpan.Kind.HIGHLIGHT -> hl = s.color
                    RichSpan.Kind.COLOR -> col = s.color
                    RichSpan.Kind.SIZE -> size = s.color
                }
            }
            if (bold) light = false
            return CharStyle(bold, light, italic, hl, col, size)
        }

        val out = ArrayList<StyledRun>()
        var i = 0
        while (i < line.length) {
            val style = styleAt(lineStart + i)
            var j = i + 1
            while (j < line.length && styleAt(lineStart + j) == style) j++
            out.add(StyledRun(line.substring(i, j), style.bold, style.light, style.italic, style.highlight, style.color, textSizeScale(style.size)))
            i = j
        }
        return out
    }

    fun lineRuns(body: String, spansJson: String): List<List<StyledRun>> {
        val spans = load(spansJson, body)
        val out = ArrayList<List<StyledRun>>()
        var offset = 0
        body.split("\n").forEach { line ->
            out.add(runsFor(line, offset, spans))
            offset += line.length + 1
        }
        return out
    }
}
