package com.lucent.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * INTEGRATION — C-group task 20, the rich-text switch, implemented against group A's editor
 * surface. C-group stopped at a handoff comment in `ui/Markdown.kt` because the controls belong in
 * A's bottom-right ring button; this is the other half.
 *
 * ### Sidecar storage, not inline markup — C's point 1, followed exactly
 *
 * `notes.body` and `tasks.notes` stay PLAIN TEXT. The formatting lives beside them in a separate
 * column as a JSON array of ranges. That is not a stylistic preference, it is the only option that
 * does not break things that already work: the assistant tools read those columns as strings, so do
 * `MarkdownExport` and `DocumentExport`, so does `BackupManager`, and so does the `[[wiki]]` link
 * scanner in `NoteLinks`. Inline markup would change what every one of them sees, silently, on
 * existing data.
 *
 * The practical consequence to keep in mind: an edit that changes the body outside this file (the
 * assistant rewriting a note, a version restore, a template) does NOT know about spans. [reconcile]
 * is what keeps that honest — see its comment.
 *
 * ### The model
 *
 * A span is a half-open character range plus one attribute. Ranges may overlap: bold and a
 * highlight over the same words are two spans, not one combined style, so removing the highlight
 * cannot accidentally take the bold with it.
 */
data class RichSpan(
    /** Inclusive start index into the plain body. */
    val start: Int,
    /** Exclusive end index. */
    val end: Int,
    val kind: Kind,
    /**
     * Which of the five highlighter colours, 0..4. Meaningless (and stored as 0) for every kind
     * other than [Kind.HIGHLIGHT].
     */
    val color: Int = 0
) {
    enum class Kind {
        /** Task 20's three weights. REGULAR is not stored — it is the absence of the other two. */
        LIGHT,
        BOLD,
        ITALIC,
        HIGHLIGHT,
        /** Task 10 — the letters' own colour, as an index into [RichText.TEXT_COLOR_ARGB]. */
        COLOR;

        companion object {
            fun fromName(name: String): Kind? = entries.firstOrNull { it.name == name }
        }
    }

    val length: Int get() = end - start

    /** True when this span covers nothing — the state an edit can leave behind. */
    val isEmpty: Boolean get() = end <= start
}

object RichText {

    /** How many highlighter colours the toolbar offers (task 20 specifies five). */
    const val HIGHLIGHT_COLORS = 5

    /** How many text colours the toolbar offers (task 10). */
    const val TEXT_COLORS = 5

    /**
     * The five text colours, as ARGB.
     *
     * Index 0 is the SENTINEL for "whatever the theme's own text colour is" and is deliberately not
     * a real colour: a fixed black would be invisible on a dark palette and a fixed white invisible
     * on a light one, and the option the user asked for is literally "black (or white)" — i.e. the
     * default, whichever that currently means. Every renderer resolves index 0 to the ambient text
     * colour and 1..4 to the numbers below.
     */
    const val TEXT_COLOR_DEFAULT = 0
    val TEXT_COLOR_ARGB = intArrayOf(
        0,                  // sentinel: follow the theme
        0xFF43A047.toInt(), // green
        0xFFF9A825.toInt(), // yellow
        0xFF1E88E5.toInt(), // blue
        0xFFE53935.toInt()  // red
    )

    /** The empty sidecar value. Stored rather than null so the column can be NOT NULL. */
    const val EMPTY = ""

    // ---------------------------------------------------------------------------------------
    // Codec
    // ---------------------------------------------------------------------------------------

    /**
     * Parse a sidecar column. Anything unparseable yields an empty list rather than throwing: the
     * body is the document and the spans are decoration, so a corrupt sidecar must cost the user
     * their formatting, never their text. A note that will not open is a much worse failure than a
     * note that opens unstyled.
     */
    fun decode(json: String): List<RichSpan> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<RichSpan>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = RichSpan.Kind.fromName(o.optString("k")) ?: continue
                val span = RichSpan(
                    start = o.optInt("s", 0),
                    end = o.optInt("e", 0),
                    kind = kind,
                    color = o.optInt("c", 0).coerceIn(0, HIGHLIGHT_COLORS - 1)
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

    // ---------------------------------------------------------------------------------------
    // Editing
    // ---------------------------------------------------------------------------------------

    /**
     * Sort, drop empties, and merge spans that touch or overlap AND carry the same attribute.
     *
     * Merging matters more than it looks: bolding a word, then bolding the word next to it, then
     * bolding the space between them would otherwise leave three spans where one will do, and after
     * a few minutes of editing the sidecar is larger than the note.
     */
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

    /** True when every character in [start, end) already carries [kind] (with [color] if relevant). */
    fun covers(spans: List<RichSpan>, start: Int, end: Int, kind: RichSpan.Kind, color: Int = 0): Boolean {
        if (end <= start) return false
        var cursor = start
        for (s in normalise(spans)) {
            if (s.kind != kind) continue
            if (kind == RichSpan.Kind.HIGHLIGHT && s.color != color) continue
            if (s.start > cursor) break
            if (s.end > cursor) cursor = s.end
            if (cursor >= end) return true
        }
        return false
    }

    /**
     * Toggle [kind] over [start, end): applied if the range is not fully covered, removed if it is.
     *
     * Toggling rather than always-applying is what makes one button do both jobs, which is what
     * keeps the ring small enough to stay usable — see QuickActionFab.
     */
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
            // LIGHT and BOLD are the same axis (weight): applying one must clear the other over the
            // same range, or the renderer has to invent a rule for "light and bold at once".
            val cleared = when (kind) {
                RichSpan.Kind.LIGHT -> remove(spans, start, end, RichSpan.Kind.BOLD, null)
                RichSpan.Kind.BOLD -> remove(spans, start, end, RichSpan.Kind.LIGHT, null)
                // Only one highlighter colour can win over a character, so clear all five first.
                RichSpan.Kind.HIGHLIGHT -> remove(spans, start, end, RichSpan.Kind.HIGHLIGHT, null)
                // One colour per character, exactly like the highlighter: clear all of them first,
                // or a range could end up carrying two and the renderer would have to invent a rule.
                RichSpan.Kind.COLOR -> remove(spans, start, end, RichSpan.Kind.COLOR, null)
                RichSpan.Kind.ITALIC -> spans
            }
            normalise(cleared + RichSpan(start, end, kind, color))
        }
    }

    /** Cut [start, end) out of every span of [kind] (and [color], when non-null). */
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
            // The removed range can split one span into two — bolding a sentence then un-bolding a
            // word in the middle of it.
            if (s.start < start) out.add(s.copy(end = start))
            if (s.end > end) out.add(s.copy(start = end))
        }
        return normalise(out)
    }

    /**
     * Shift spans to follow a text edit that replaced [oldLength] characters at [at] with
     * [newLength] characters.
     *
     * Characters typed at the exact boundary of a span extend it — type at the end of a bold word
     * and the new letters are bold too, which is what every editor does and what people expect.
     */
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

    /**
     * Clamp spans to a body of [length] characters, dropping anything that no longer lands on text.
     *
     * This is the safety valve for every writer that does NOT go through [afterEdit]: the assistant
     * rewriting a note, a version restore replacing the whole body, an import. Those change the text
     * without telling anyone, and stale ranges would then highlight whatever happens to sit at those
     * offsets now — formatting that points at the wrong words is worse than no formatting, and
     * unlike a crash it is easy not to notice.
     *
     * Called on every read path. It is O(spans) and spans are few.
     */
    fun reconcile(spans: List<RichSpan>, length: Int): List<RichSpan> =
        normalise(spans.mapNotNull { s ->
            if (s.start >= length) null
            else s.copy(end = minOf(s.end, length)).takeIf { !it.isEmpty }
        })

    /** Which attributes cover EVERY character of [start, end) — what the toolbar lights up. */
    fun kindsCovering(spans: List<RichSpan>, start: Int, end: Int): Set<RichSpan.Kind> =
        RichSpan.Kind.entries
            .filter { it != RichSpan.Kind.HIGHLIGHT && it != RichSpan.Kind.COLOR && covers(spans, start, end, it) }
            .toSet()

    /** Which text colour covers all of [start, end), or null. */
    fun colorCovering(spans: List<RichSpan>, start: Int, end: Int): Int? =
        (0 until TEXT_COLORS).firstOrNull { covers(spans, start, end, RichSpan.Kind.COLOR, it) }

    /** Which highlighter colour covers all of [start, end), or null. */
    fun highlightCovering(spans: List<RichSpan>, start: Int, end: Int): Int? =
        (0 until HIGHLIGHT_COLORS).firstOrNull { covers(spans, start, end, RichSpan.Kind.HIGHLIGHT, it) }

    /**
     * Follow a text edit, and apply the styles the user armed BEFORE typing (task 1).
     *
     * ### Why the diff is computed here rather than taken from the field
     *
     * The composers own a plain `String`; they are told the new text and nothing about how it got
     * that way. A longest-common-prefix/suffix diff recovers the one thing the spans actually need —
     * where characters were removed and where they were inserted — and it recovers it correctly for
     * every writer, including the ones that never touch the keyboard (undo, a template, the
     * assistant rewriting a note). Anything smarter would have to be told, and the writers that
     * would have to do the telling are exactly the ones that do not know they are editing a styled
     * document.
     *
     * ### Pending styles
     *
     * Before this existed, formatting could only be applied to text that already existed: select it,
     * then style it. Every editor people actually use also works the other way round — press bold,
     * then type — and that is what [pendingKinds] and [pendingHighlight] are. They are handed in by
     * the composer, and any characters this edit INSERTED are given those attributes.
     */
    fun applyEdit(
        spans: List<RichSpan>,
        old: String,
        new: String,
        pendingKinds: Set<RichSpan.Kind> = emptySet(),
        pendingHighlight: Int? = null,
        pendingColor: Int? = null,
        /**
         * True once the user has pressed something on the toolbar with no selection — at which point
         * what they armed is the WHOLE truth about the characters they type next.
         *
         * This exists because of [afterEdit]'s boundary rule: type at the end of a bold word and the
         * new letters are bold too, which is what every editor does and what people expect. But it is
         * also why "clear formatting" appeared to do nothing — the pending set was emptied correctly
         * and then the very next keystroke inherited the bold from the character in front of it. When
         * this flag is set the inserted range is stripped to exactly what was armed, so clearing
         * really does return the text to plain.
         */
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
            // Everything the user did not arm comes off the new characters first.
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
        }
        if (inserted > 0) {
            pendingKinds.forEach { kind ->
                // Same one-axis rule toggle() enforces: a weight replaces the other weight.
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
        }
        return reconcile(out, new.length)
    }

    /** Convenience for the read path: decode and clamp in one call. */
    fun load(json: String, body: String): List<RichSpan> = reconcile(decode(json), body.length)

    // ---------------------------------------------------------------------------------------
    // Rendering support — shared by every consumer so they cannot disagree
    // ---------------------------------------------------------------------------------------

    /**
     * The five highlighter colours as ARGB, defined HERE rather than in the UI layer so the editor,
     * the DOCX writer and both PDF writers all read the same numbers. A highlight that is one yellow
     * on screen and a different yellow in the export is the kind of thing nobody reports and
     * everybody notices.
     *
     * Chosen at highlighter-pen saturation and kept apart in hue, so all five stay tellable apart on
     * the light and dark themes alike — the same "a user must be able to see the difference" test
     * group C applied to the new backgrounds.
     */
    val HIGHLIGHT_ARGB = intArrayOf(
        0xFFFFF176.toInt(), // yellow
        0xFF81C784.toInt(), // green
        0xFF4FC3F7.toInt(), // blue
        0xFFF48FB1.toInt(), // pink
        0xFFFFB74D.toInt()  // orange
    )

    /** One stretch of text over which every attribute is constant. */
    data class StyledRun(
        val text: String,
        val bold: Boolean,
        val light: Boolean,
        val italic: Boolean,
        /** Index into [HIGHLIGHT_ARGB], or -1 for no highlight. */
        val highlight: Int,
        /**
         * Index into [TEXT_COLOR_ARGB]. [TEXT_COLOR_DEFAULT] means "whatever the document's own body
         * colour is" — the writers leave it alone rather than substituting a black of their own.
         */
        val color: Int = TEXT_COLOR_DEFAULT
    ) {
        val plain: Boolean
            get() = !bold && !light && !italic && highlight < 0 && color == TEXT_COLOR_DEFAULT
    }

    /**
     * The ARGB for a [StyledRun.color], or null when it is the default and the writer should not
     * set a colour at all.
     *
     * Null rather than a black constant on purpose: a PDF or a Word document has its own idea of
     * body text colour, and forcing #000000 onto every unstyled run would be a change no one asked
     * for — and would fight any future dark export theme.
     */
    fun textColorArgb(index: Int): Int? =
        if (index <= TEXT_COLOR_DEFAULT || index >= TEXT_COLOR_ARGB.size) null
        else TEXT_COLOR_ARGB[index]

    /**
     * Split [line] into runs of constant style. [lineStart] is the line's offset within the whole
     * body, because spans are stored against the body and a writer works line by line.
     *
     * Deliberately computed per character and then coalesced. Tracking span boundaries directly
     * would be faster and is wrong in the presence of overlaps — bold underneath a highlight is two
     * spans covering the same characters, and the boundary list alone does not say which
     * combination applies between any two boundaries. A note body is a few thousand characters, so
     * the difference is not measurable and the correctness is not arguable.
     */
    fun runsFor(line: String, lineStart: Int, spans: List<RichSpan>): List<StyledRun> {
        if (line.isEmpty()) return listOf(StyledRun("", false, false, false, -1))
        if (spans.isEmpty()) return listOf(StyledRun(line, false, false, false, -1))
        // (both shortcuts leave `color` at its default, which is what "no styling" means)

        data class CharStyle(
            val bold: Boolean,
            val light: Boolean,
            val italic: Boolean,
            val highlight: Int,
            val color: Int
        )

        fun styleAt(abs: Int): CharStyle {
            var bold = false; var light = false; var italic = false; var hl = -1
            var col = TEXT_COLOR_DEFAULT
            spans.forEach { s ->
                if (abs >= s.start && abs < s.end) when (s.kind) {
                    RichSpan.Kind.BOLD -> bold = true
                    RichSpan.Kind.LIGHT -> light = true
                    RichSpan.Kind.ITALIC -> italic = true
                    RichSpan.Kind.HIGHLIGHT -> hl = s.color
                    RichSpan.Kind.COLOR -> col = s.color
                }
            }
            // LIGHT and BOLD are one axis and toggle() already clears the other, but a hand-edited
            // or restored sidecar can still carry both. Bold wins, so the text is never rendered in
            // a weight nobody asked for.
            if (bold) light = false
            return CharStyle(bold, light, italic, hl, col)
        }

        val out = ArrayList<StyledRun>()
        var i = 0
        while (i < line.length) {
            val style = styleAt(lineStart + i)
            var j = i + 1
            while (j < line.length && styleAt(lineStart + j) == style) j++
            out.add(StyledRun(line.substring(i, j), style.bold, style.light, style.italic, style.highlight, style.color))
            i = j
        }
        return out
    }

    /**
     * Line-by-line runs for a whole body: each entry is one source line's runs, in order.
     * Handles the offset arithmetic once, so no writer has to get it right twice.
     */
    fun lineRuns(body: String, spansJson: String): List<List<StyledRun>> {
        val spans = load(spansJson, body)
        val out = ArrayList<List<StyledRun>>()
        var offset = 0
        body.split("\n").forEach { line ->
            out.add(runsFor(line, offset, spans))
            offset += line.length + 1 // + the newline itself
        }
        return out
    }
}
