package com.lucent.app.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val marker: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lines: List<String>) : MdBlock
    data object Blank : MdBlock
    data object Rule : MdBlock
}

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^\s*[-*+]\s+(.*)$""")
private val NUMBERED = Regex("""^\s*(\d+)[.)]\s+(.*)$""")
private val QUOTE = Regex("""^\s*>\s?(.*)$""")
private val RULE = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
private const val FENCE = "```"

private fun parseBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.lines()
    val paragraph = StringBuilder()
    var blankRun = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.toString().trimEnd())
            paragraph.clear()
        }
    }

    fun flushBlankRun() {
        repeat((blankRun - 1).coerceAtLeast(0)) { blocks += MdBlock.Blank }
        blankRun = 0
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        if (line.trimStart().startsWith(FENCE)) {
            flushParagraph()
            flushBlankRun()
            val code = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith(FENCE)) {
                code += lines[i]
                i++
            }
            i++
            blocks += MdBlock.Code(code)
            continue
        }

        when {
            line.isBlank() -> {
                flushParagraph()
                blankRun++
            }

            RULE.matches(line) -> {
                flushParagraph()
                flushBlankRun()
                blocks += MdBlock.Rule
            }

            HEADING.matches(line) -> {
                flushParagraph()
                flushBlankRun()
                val m = HEADING.find(line)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
            }

            BULLET.matches(line) -> {
                flushParagraph()
                flushBlankRun()
                blocks += MdBlock.Bullet(BULLET.find(line)!!.groupValues[1])
            }

            NUMBERED.matches(line) -> {
                flushParagraph()
                flushBlankRun()
                val m = NUMBERED.find(line)!!
                blocks += MdBlock.Numbered(m.groupValues[1], m.groupValues[2])
            }

            QUOTE.matches(line) -> {
                flushParagraph()
                flushBlankRun()
                blocks += MdBlock.Quote(QUOTE.find(line)!!.groupValues[1])
            }

            else -> {
                if (paragraph.isEmpty()) flushBlankRun()
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

private val INLINE = Regex(
    """\[\[([^\[\]]+)]]""" +
        """|\[([^\]]+)]\(([^)\s]+)\)""" +
        """|`([^`]+)`""" +
        """|\*\*([^*]+)\*\*""" +
        """|__([^_]+)__""" +
        """|~~([^~]+)~~""" +
        """|\*([^*\n]+)\*""" +
        """|_([^_\n]+)_"""
)

@Composable
private fun inlineAnnotated(
    line: String,
    textColor: Color,
    accent: Color,
    brokenLinks: Set<String>,
    onWikiLink: (String) -> Unit,
    linksEnabled: Boolean
): AnnotatedString {
    val brokenStyle = SpanStyle(color = OverdueColor, textDecoration = TextDecoration.Underline)
    val linkStyle = SpanStyle(color = accent, textDecoration = TextDecoration.Underline)

    return buildAnnotatedString {
        var cursor = 0
        for (match in INLINE.findAll(line)) {
            if (match.range.first > cursor) {
                append(line.substring(cursor, match.range.first))
            }
            val g = match.groupValues
            when {
                g[1].isNotEmpty() -> {
                    val target = g[1].trim()
                    if (!linksEnabled) {
                        append(target)
                    } else {
                        val broken = target.lowercase() in brokenLinks
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "note:$target",
                                styles = TextLinkStyles(style = if (broken) brokenStyle else linkStyle)
                            ) { onWikiLink(target) }
                        ) {
                            append(target)
                        }
                    }
                }
                g[2].isNotEmpty() && g[3].isNotEmpty() -> {
                    if (!linksEnabled) {
                        append(g[2])
                    } else {
                        withLink(LinkAnnotation.Url(url = g[3], styles = TextLinkStyles(style = linkStyle))) {
                            append(g[2])
                        }
                    }
                }
                g[4].isNotEmpty() -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = textColor.copy(alpha = 0.10f))
                ) { append(g[4]) }
                g[5].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[5]) }
                g[6].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[6]) }
                g[7].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[7]) }
                g[8].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[8]) }
                g[9].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[9]) }
            }
            cursor = match.range.last + 1
        }
        if (cursor < line.length) append(line.substring(cursor))
    }
}

private val LINKS_ONLY = Regex("""\[\[([^\[\]]+)]]|\[([^\]]+)]\(([^)\s]+)\)""")

@Composable
fun LinkedPlainText(
    text: String,
    modifier: Modifier = Modifier,
    brokenLinks: Set<String> = emptySet(),
    onWikiLink: (String) -> Unit = {}
) {
    val onGradient = LocalOnGradient.current
    val brokenStyle = SpanStyle(color = OverdueColor, textDecoration = TextDecoration.Underline)
    val linkStyle = SpanStyle(color = onGradient, textDecoration = TextDecoration.Underline)

    val annotated = buildAnnotatedString {
        var cursor = 0
        for (match in LINKS_ONLY.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            val g = match.groupValues
            when {
                g[1].isNotEmpty() -> {
                    val target = g[1].trim()
                    val broken = target.lowercase() in brokenLinks
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "note:$target",
                            styles = TextLinkStyles(style = if (broken) brokenStyle else linkStyle)
                        ) { onWikiLink(target) }
                    ) {
                        append(target)
                    }
                }
                g[2].isNotEmpty() && g[3].isNotEmpty() -> {
                    withLink(LinkAnnotation.Url(url = g[3], styles = TextLinkStyles(style = linkStyle))) {
                        append(g[2])
                    }
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }

    Text(annotated, color = onGradient, modifier = modifier)
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    brokenLinks: Set<String> = emptySet(),
    onWikiLink: (String) -> Unit = {},
    linksEnabled: Boolean = true
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val blocks = remember(text) { parseBlocks(text) }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(modifier = Modifier.height(6.dp))
            when (block) {
                is MdBlock.Heading -> Text(
                    inlineAnnotated(block.text, onGradient, onGradient, brokenLinks, onWikiLink, linksEnabled),
                    color = onGradient,
                    fontSize = when (block.level) {
                        1 -> 22.sp
                        2 -> 19.sp
                        3 -> 17.sp
                        else -> 15.sp
                    },
                    fontWeight = FontWeight.SemiBold
                )

                is MdBlock.Paragraph -> Text(
                    inlineAnnotated(block.text, onGradient, onGradient, brokenLinks, onWikiLink, linksEnabled),
                    color = onGradient
                )

                is MdBlock.Bullet -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text("•", color = onGradientMuted, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        inlineAnnotated(block.text, onGradient, onGradient, brokenLinks, onWikiLink, linksEnabled),
                        color = onGradient,
                        modifier = Modifier.weight(1f)
                    )
                }

                is MdBlock.Numbered -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text("${block.marker}.", color = onGradientMuted, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        inlineAnnotated(block.text, onGradient, onGradient, brokenLinks, onWikiLink, linksEnabled),
                        color = onGradient,
                        modifier = Modifier.weight(1f)
                    )
                }

                is MdBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(onGradientMuted)
                    )
                    Text(
                        inlineAnnotated(block.text, onGradientMuted, onGradient, brokenLinks, onWikiLink, linksEnabled),
                        color = onGradientMuted,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                is MdBlock.Code -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(onGradient.copy(alpha = 0.08f))
                        .padding(10.dp)
                ) {
                    Text(
                        block.lines.joinToString("\n"),
                        color = onGradient,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }

                MdBlock.Blank -> Spacer(modifier = Modifier.height(12.dp))

                MdBlock.Rule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(onGradientMuted.copy(alpha = 0.4f))
                )
            }
        }
    }
}
