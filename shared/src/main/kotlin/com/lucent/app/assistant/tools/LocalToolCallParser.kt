package com.lucent.app.assistant.tools

object LocalToolCallParser {

    data class LocalToolCall(val name: String, val argsJson: String)

    fun renderLocalToolCall(call: LocalToolCall): String {
        val args = try { org.json.JSONObject(call.argsJson) } catch (e: Exception) { org.json.JSONObject() }
        return org.json.JSONObject().put("tool", call.name).put("arguments", args).toString()
    }

    fun parseLocalToolCall(raw: String, valid: Set<String>): LocalToolCall? {
        if (raw.isBlank()) return null
        val s = stripToolWrappers(raw)

        for (candidate in jsonObjectCandidates(s)) {
            var obj = try { org.json.JSONObject(candidate) } catch (e: Exception) { continue }
            for (k in TOOL_WRAPPER_KEYS) obj.optJSONObject(k)?.let { obj = it }
            val rawName = firstJsonString(obj, "tool", "name", "function", "action", "tool_name")?.trim()
            if (rawName.isNullOrBlank()) continue
            val name = resolveToolName(rawName, valid) ?: continue
            val argsObj = firstJsonObject(obj, "arguments", "args", "parameters", "input", "params")
            return LocalToolCall(name, stripBlankArguments(argsObj).toString())
        }
        return null
    }

    fun attemptedToolCallName(raw: String): String? {
        if (raw.isBlank()) return null
        val s = stripToolWrappers(raw)
        for (candidate in jsonObjectCandidates(s)) {
            var obj = try { org.json.JSONObject(candidate) } catch (e: Exception) { continue }
            for (k in TOOL_WRAPPER_KEYS) obj.optJSONObject(k)?.let { obj = it }
            val name = firstJsonString(obj, "tool", "name", "function", "action", "tool_name")?.trim()
            val hasArgs = firstJsonObject(obj, "arguments", "args", "parameters", "input", "params") != null
            if (!name.isNullOrBlank() && (hasArgs || SNAKE_CASE_NAME.matches(name))) return name
            if (name.isNullOrBlank() && hasArgs) return "(unnamed)"
        }
        return null
    }

    private val TOOL_WRAPPER_KEYS = arrayOf("tool_call", "function_call", "call", "tool", "function", "action")

    private val NAME_SEPARATORS = Regex("[\\s\\-]+")
    private val SNAKE_CASE_NAME = Regex("^[a-z0-9]+(_[a-z0-9]+)+$")

    private val TOOL_CALL_TAG = Regex("(?s)<tool_call>(.*?)</tool_call>")
    private val CODE_FENCE = Regex("(?s)```(?:json|tool_call)?\\s*(.*?)```")

    private fun stripToolWrappers(raw: String): String {
        var s = raw.trim()
        TOOL_CALL_TAG.find(s)?.let { s = it.groupValues[1].trim() }
        CODE_FENCE.find(s)?.let { s = it.groupValues[1].trim() }
        return s
    }

    private fun resolveToolName(rawName: String, valid: Set<String>): String? {
        val n = rawName.trim().lowercase().replace(NAME_SEPARATORS, "_").trim('_')
        if (n.isBlank()) return null
        if (n in valid) return n

        val tokens = n.split('_').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        val verbGroups = listOf(
            setOf("create", "add", "new", "make", "insert"),
            setOf("delete", "remove", "del", "erase", "discard"),
            setOf("update", "edit", "modify", "change", "rename"),
            setOf("complete", "finish", "done", "check", "close"),
            setOf("read", "get", "show", "view", "open"),
            setOf("list", "show", "view", "get"),
            setOf("reopen", "uncomplete", "undone", "uncheck"),
            setOf("search", "find", "query", "lookup")
        )
        val firstVariants = linkedSetOf(tokens.first())
        for (g in verbGroups) if (tokens.first() in g) firstVariants.addAll(g)
        val last = tokens.last()
        val lastVariants = linkedSetOf(last, if (last.endsWith("s")) last.dropLast(1) else last + "s")

        val candidates = linkedSetOf<String>()
        if (tokens.size == 1) {
            candidates.addAll(firstVariants)
            candidates.addAll(lastVariants)
        } else {
            val mid = tokens.subList(1, tokens.size - 1)
            for (fv in firstVariants) for (lv in lastVariants) {
                candidates.add((listOf(fv) + mid + lv).joinToString("_"))
            }
        }
        candidates.firstOrNull { it in valid }?.let { return it }

        val containment = valid.filter { it.contains(n) || n.contains(it) }
        return containment.singleOrNull()
    }

    private fun stripBlankArguments(argsObj: org.json.JSONObject?): org.json.JSONObject {
        val cleaned = org.json.JSONObject()
        if (argsObj == null) return cleaned
        for (k in argsObj.keys()) {
            val v = argsObj.opt(k)
            val keep = when (v) {
                null, org.json.JSONObject.NULL -> false
                is String -> v.isNotBlank()
                else -> true
            }
            if (keep) cleaned.put(k, v)
        }
        return cleaned
    }

    private fun jsonObjectCandidates(s: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            if (s[i] != '{') { i++; continue }
            var depth = 0; var inStr = false; var esc = false; var j = i
            while (j < s.length) {
                val c = s[j]
                if (inStr) {
                    when {
                        esc -> esc = false
                        c == '\\' -> esc = true
                        c == '"' -> inStr = false
                        else -> {}
                    }
                } else when (c) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) { out.add(s.substring(i, j + 1)); break } }
                    else -> {}
                }
                j++
            }
            i = if (j > i) j + 1 else i + 1
        }
        return out
    }

    private fun firstJsonString(o: org.json.JSONObject, vararg keys: String): String? {
        for (k in keys) { val v = o.opt(k); if (v is String && v.isNotBlank()) return v }
        return null
    }

    private fun firstJsonObject(o: org.json.JSONObject, vararg keys: String): org.json.JSONObject? {
        for (k in keys) {
            val v = o.opt(k)
            if (v is org.json.JSONObject) return v
            if (v is String && v.trim().startsWith("{")) {
                try { return org.json.JSONObject(v) } catch (_: Exception) {}
            }
        }
        return null
    }
}
