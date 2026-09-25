package com.lucent.app.harness

import java.io.File
import java.util.zip.Inflater

object SimplePdfText {

    fun extract(file: File): String {
        if (!file.exists()) return ""
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            return ""
        }
        val out = StringBuilder()
        var index = 0
        while (index < bytes.size) {
            val start = indexOf(bytes, "stream", index)
            if (start < 0) break
            var dataStart = start + "stream".length
            if (dataStart < bytes.size && bytes[dataStart] == 13.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == 10.toByte()) dataStart++
            val end = indexOf(bytes, "endstream", dataStart)
            if (end < 0) break
            val raw = bytes.copyOfRange(dataStart, end)
            val decoded = inflate(raw) ?: raw
            val text = textOf(decoded)
            if (text.isNotBlank()) out.append(text).append('\n')
            index = end + "endstream".length
            if (out.length > 400000) break
        }
        return out.toString().trim()
    }

    private fun inflate(raw: ByteArray): ByteArray? {
        val attempts = listOf(raw, if (raw.size > 2) raw.copyOfRange(2, raw.size) else raw)
        attempts.forEach { candidate ->
            try {
                val inflater = Inflater()
                inflater.setInput(candidate)
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                while (!inflater.finished()) {
                    val read = inflater.inflate(buffer)
                    if (read == 0 && inflater.needsInput()) break
                    out.write(buffer, 0, read)
                }
                inflater.end()
                val result = out.toByteArray()
                if (result.isNotEmpty()) return result
            } catch (t: Throwable) {
            }
        }
        return null
    }

    private fun textOf(content: ByteArray): String {
        val text = String(content, Charsets.ISO_8859_1)
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '(' && isTextOperatorBefore(text, i) -> {
                    val closing = matchingParen(text, i)
                    if (closing > i) out.append(unescape(text.substring(i + 1, closing)))
                    i = if (closing > i) closing + 1 else i + 1
                }
                ch == '[' && text.getOrNull(i + 1) == '(' -> {
                    val closing = text.indexOf(']', i)
                    if (closing > i) {
                        out.append(arrayText(text.substring(i + 1, closing)))
                        i = closing + 1
                    } else i++
                }
                ch == '<' && isHexString(text, i) -> {
                    val closing = text.indexOf('>', i)
                    if (closing > i) {
                        out.append(hexText(text.substring(i + 1, closing)))
                        i = closing + 1
                    } else i++
                }
                text.startsWith("Td", i) || text.startsWith("TD", i) || text.startsWith("T*", i) -> {
                    if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                    i += 2
                }
                else -> i++
            }
        }
        return out.toString().lines().joinToString("\n") { it.trim() }
    }

    private fun isTextOperatorBefore(text: String, index: Int): Boolean = true

    private fun isHexString(text: String, index: Int): Boolean {
        val closing = text.indexOf('>', index)
        if (closing < 0 || closing - index > 4000) return false
        val body = text.substring(index + 1, closing)
        return body.isNotEmpty() && body.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it.isWhitespace() }
    }

    private fun matchingParen(text: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\\') {
                i += 2
                continue
            }
            if (ch == '(') depth++
            if (ch == ')') {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return -1
    }

    private fun arrayText(body: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < body.length) {
            val ch = body[i]
            if (ch == '(') {
                val closing = matchingParen(body, i)
                if (closing > i) {
                    out.append(unescape(body.substring(i + 1, closing)))
                    i = closing + 1
                    continue
                }
            }
            if (ch == '<') {
                val closing = body.indexOf('>', i)
                if (closing > i) {
                    out.append(hexText(body.substring(i + 1, closing)))
                    i = closing + 1
                    continue
                }
            }
            if (ch == '-' && i > 0 && body[i - 1] == ' ') out.append(' ')
            i++
        }
        return out.toString()
    }

    private fun hexText(hex: String): String {
        val clean = hex.filter { !it.isWhitespace() }
        if (clean.length < 2) return ""
        val bytes = ByteArray(clean.length / 2)
        for (i in bytes.indices) {
            val value = clean.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return ""
            bytes[i] = value.toByte()
        }
        if (bytes.size >= 2 && bytes.size % 2 == 0) {
            val wide = StringBuilder()
            var i = 0
            var plausible = true
            while (i + 1 < bytes.size) {
                if (bytes[i] != 0.toByte()) {
                    plausible = false
                    break
                }
                wide.append(bytes[i + 1].toInt().toChar())
                i += 2
            }
            if (plausible && wide.isNotEmpty()) return wide.toString()
        }
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun unescape(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch != '\\') {
                out.append(ch)
                i++
                continue
            }
            val next = value.getOrNull(i + 1) ?: break
            when (next) {
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                '(', ')', '\\' -> out.append(next)
                else -> {
                    if (next.isDigit()) {
                        var digits = ""
                        var j = i + 1
                        while (j < value.length && digits.length < 3 && value[j].isDigit()) {
                            digits += value[j]
                            j++
                        }
                        val code = digits.toIntOrNull(8) ?: 0
                        out.append(code.toChar())
                        i = j
                        continue
                    }
                    out.append(next)
                }
            }
            i += 2
        }
        return out.toString()
    }

    private fun indexOf(bytes: ByteArray, needle: String, from: Int): Int {
        val target = needle.toByteArray(Charsets.ISO_8859_1)
        if (target.isEmpty()) return -1
        var i = from.coerceAtLeast(0)
        outer@ while (i <= bytes.size - target.size) {
            var j = 0
            while (j < target.size) {
                if (bytes[i + j] != target[j]) {
                    i++
                    continue@outer
                }
                j++
            }
            return i
        }
        return -1
    }
}
