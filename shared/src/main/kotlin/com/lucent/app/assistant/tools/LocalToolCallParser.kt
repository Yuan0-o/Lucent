package com.lucent.app.assistant.tools

/**
 * Parsing helpers for the local (on-device) model's text tool-call protocol.
 *
 * Small GGUF models have no native function-calling channel, so tools run over a small text
 * protocol: the model emits a single JSON object to call a tool, [parseLocalToolCall] extracts it.
 * All logic here was moved verbatim out of AssistantController so it can be unit-tested on the JVM;
 * behaviour is unchanged.
 */
object LocalToolCallParser {

    data class LocalToolCall(val name: String, val argsJson: String)

    /**
     * Re-serialise a parsed call as compact, well-formed JSON for the assistant turn we feed back.
     */
    fun renderLocalToolCall(call: LocalToolCall): String {
        val args = try { org.json.JSONObject(call.argsJson) } catch (e: Exception) { org.json.JSONObject() }
        return org.json.JSONObject().put("tool", call.name).put("arguments", args).toString()
    }

    /**
     * Pull a tool call out of a local model's raw output, tolerantly. Small GGUF models phrase tool
     * calls every which way, so this copes with: a bare JSON object, one wrapped in ``` fences, one
     * inside <tool_call>…</tool_call>, arguments under any of several key names, and arguments that
     * arrive double-encoded as a JSON string. A candidate only counts as a call when its tool name
     * is one that actually exists ([valid]) — so incidental JSON in a normal prose answer is never
     * mistaken for a call, and plain chat falls straight through to being the final reply.
     */
    fun parseLocalToolCall(raw: String, valid: Set<String>): LocalToolCall? {
        if (raw.isBlank()) return null
        val s = stripToolWrappers(raw)

        for (candidate in jsonObjectCandidates(s)) {
            var obj = try { org.json.JSONObject(candidate) } catch (e: Exception) { continue }
            // Some models nest the call one level deep ({"tool_call": {"name": …}}); unwrap it.
            // optJSONObject is null when the key holds a string, so flat forms pass unchanged.
            for (k in TOOL_WRAPPER_KEYS) obj.optJSONObject(k)?.let { obj = it }
            val rawName = firstJsonString(obj, "tool", "name", "function", "action", "tool_name")?.trim()
            if (rawName.isNullOrBlank()) continue
            // Exact match first; then alias mapping, because small on-device models routinely
            // invent near-miss names — the reported bug was Qwen2.5-0.5B emitting "add_task"
            // for create_task and the raw JSON landing on screen as the reply.
            val name = resolveToolName(rawName, valid) ?: continue
            val argsObj = firstJsonObject(obj, "arguments", "args", "parameters", "input", "params")
            return LocalToolCall(name, stripBlankArguments(argsObj).toString())
        }
        return null
    }

    /**
     * The name a model TRIED to call when its output is shaped like a tool call but doesn't parse
     * into a valid one — snake_case name plus an arguments object, or an arguments object alone.
     * Null for plain prose. Deliberately stricter than the parser about what counts as "shaped
     * like a call", so an ordinary answer that happens to contain a JSON example isn't flagged.
     */
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

    // Wrapper keys some chat templates put around the call object itself.
    private val TOOL_WRAPPER_KEYS = arrayOf("tool_call", "function_call", "call", "tool", "function", "action")

    // Compiled once: shared by resolveToolName and attemptedToolName below.
    private val NAME_SEPARATORS = Regex("[\\s\\-]+")
    private val SNAKE_CASE_NAME = Regex("^[a-z0-9]+(_[a-z0-9]+)+$")

    // Compiled once: this scan runs on every reply a local model produces with tools enabled.
    private val TOOL_CALL_TAG = Regex("(?s)<tool_call>(.*?)</tool_call>")
    private val CODE_FENCE = Regex("(?s)```(?:json|tool_call)?\\s*(.*?)```")

    /** Peel `<tool_call>` tags and code fences off a model's output before scanning it for JSON. */
    private fun stripToolWrappers(raw: String): String {
        var s = raw.trim()
        TOOL_CALL_TAG.find(s)?.let { s = it.groupValues[1].trim() }
        CODE_FENCE.find(s)?.let { s = it.groupValues[1].trim() }
        return s
    }

    /**
     * Map a model-emitted tool name onto a real one. Exact (after snake_case normalisation) wins;
     * otherwise the leading verb is swapped through its synonym group and the trailing noun through
     * singular/plural, and the first candidate that names a real tool is taken ("add_task" →
     * create_task, "edit_note" → update_note, "list_task" → list_tasks). As a last resort a UNIQUE
     * containment match is accepted. Anything still unresolved is null — the caller decides whether
     * to feed an error back to the model rather than guessing at an action on the user's data.
     */
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

        // e.g. "task_search" or "notes" alone: accept only when exactly ONE real tool matches.
        val containment = valid.filter { it.contains(n) || n.contains(it) }
        return containment.singleOrNull()
    }

    /**
     * Drop arguments a weak model filled with "" / null instead of omitting (the reported call
     * carried due:"", priority:"", repeat:"" …). Empty means "not provided" for every tool here —
     * update_* tools in particular treat an absent field as "leave unchanged", which is exactly
     * what an empty string was meant to say.
     */
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

    /** Every balanced `{…}` object in [s], scanned so braces inside string literals don't fool it. */
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
                    }
                } else when (c) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) { out.add(s.substring(i, j + 1)); break } }
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
