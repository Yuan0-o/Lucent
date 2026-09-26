package com.lucent.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.i18n.S
import com.lucent.app.network.ToolExecResult
import org.json.JSONArray
import org.json.JSONObject

enum class AgentStepKind { REASONING, TOOL, NOTE }

enum class AgentStepStatus { RUNNING, DONE, FAILED, CANCELLED }

data class AgentStep(
    val kind: AgentStepKind,
    val status: AgentStepStatus = AgentStepStatus.RUNNING,
    val toolName: String = "",
    val detail: String = "",
    val variant: String = "",
    val text: String = "",
    val errorText: String = "",
    val millis: Long = 0L
) {
    fun label(): String =
        if (kind == AgentStepKind.TOOL) AgentTraceLabels.labelFor(toolName, variant) else text
}

data class AgentTrace(
    val steps: List<AgentStep> = emptyList(),
    val reasoning: String = "",
    val status: AgentStepStatus = AgentStepStatus.RUNNING,
    val budgetLabel: String = ""
) {

    val hasContent: Boolean get() = steps.isNotEmpty() || reasoning.isNotBlank() || budgetLabel.isNotBlank()

    val isTerminal: Boolean get() = status != AgentStepStatus.RUNNING
}

object AgentTraceLabels {

    const val VARIANT_ON = "on"
    const val VARIANT_OFF = "off"

    fun labelFor(toolName: String, variant: String = ""): String = when (toolName) {
        "create_note" -> S.agentStepCreateNote
        "list_notes" -> S.agentStepListNotes
        "read_note" -> S.agentStepReadNote
        "update_note" -> S.agentStepUpdateNote
        "delete_note", "delete_task", "delete_draft", "delete_notebook" -> S.agentStepDelete
        "pin_note", "pin_task", "pin_notebook" -> if (variant == VARIANT_OFF) S.agentStepUnpin else S.agentStepPin
        "archive_note" -> if (variant == VARIANT_OFF) S.agentStepUnarchive else S.agentStepArchive
        "set_note_color" -> S.agentStepNoteColor
        "add_note_checklist_item", "set_note_checklist_item_done",
        "edit_note_checklist_item", "remove_note_checklist_item" -> S.agentStepNoteChecklist
        "set_note_checklist_mode" -> S.agentStepNoteLayout
        "set_note_attachment", "remove_note_attachment", "attach_upload_to_note" -> S.agentStepNoteAttachment
        "list_note_versions", "restore_note_version" -> S.agentStepNoteVersions
        "create_task" -> S.agentStepCreateTask
        "list_tasks" -> S.agentStepListTasks
        "read_task" -> S.agentStepReadTask
        "complete_task" -> S.agentStepCompleteTask
        "reopen_task" -> S.agentStepReopenTask
        "update_task" -> S.agentStepUpdateTask
        "set_task_priority" -> S.agentStepTaskPriority
        "set_task_due_date" -> S.agentStepTaskDue
        "add_subtask", "set_subtask_done", "edit_subtask", "remove_subtask" -> S.agentStepSubtask
        "set_task_attachment", "remove_task_attachment", "attach_upload_to_task" -> S.agentStepTaskAttachment
        "list_drafts" -> S.agentStepListDrafts
        "list_trash" -> S.agentStepListTrash
        "restore_note_from_trash", "restore_task_from_trash" -> S.agentStepRestoreTrash
        "read_attachment" -> S.agentStepReadAttachment
        "search_items" -> S.agentStepSearch
        "recall_notes" -> S.agentStepRecallNotes
        "web_search" -> S.agentStepWebSearch
        "format_note_text", "format_task_notes" -> S.agentStepFormatText
        "append_to_note", "append_to_task_notes" -> S.agentStepAppendText
        "set_note_format", "set_task_format" -> S.agentStepNoteLayout
        "set_note_hidden", "set_task_hidden" ->
            if (variant == VARIANT_OFF) S.agentStepUnarchive else S.agentStepHideItem
        "move_note", "move_task" -> S.agentStepMoveItem
        "set_notebook_cover" -> S.agentStepNotebookCover
        "move_notebook" -> S.agentStepNotebookOrder
        "list_notebook_trash", "restore_notebook_from_trash" -> S.agentStepNotebookTrash
        else -> S.ccRunGeneric(toolName)
    }

    fun variantFor(toolName: String, argumentsJson: String): String = when (toolName) {
        "pin_note", "pin_task", "pin_notebook" ->
            if (readBool(argumentsJson, "pinned", true)) VARIANT_ON else VARIANT_OFF
        "archive_note" -> if (readBool(argumentsJson, "archived", true)) VARIANT_ON else VARIANT_OFF
        "set_note_hidden", "set_task_hidden" ->
            if (readBool(argumentsJson, "hidden", true)) VARIANT_ON else VARIANT_OFF
        else -> ""
    }

    fun detailFor(toolName: String, argumentsJson: String): String {
        val args = try { JSONObject(argumentsJson) } catch (e: Exception) { JSONObject() }
        fun value(vararg keys: String): String {
            for (k in keys) {
                val v = args.optString(k, "")
                if (v.isNotBlank()) return oneLine(v)
            }
            return ""
        }
        return when (toolName) {
            "update_note", "update_task" -> value("new_title", "title")
            "set_task_priority" -> value("priority")
            "set_task_due_date" -> value("due_at", "due")
            "set_note_color" -> value("color", "colour")
            "list_trash" -> value("type")
            "restore_note_version" -> value("version")
            "delete_draft" -> value("title", "kind")
            else -> value("title", "query", "item", "file_name", "name", "kind")
        }
    }

    fun oneLine(text: String): String = text.replace(WHITESPACE, " ").trim()

    fun summarize(text: String, max: Int = 90): String {
        val flat = oneLine(text)
        if (flat.length <= max) return flat
        return flat.take(max - 1).trimEnd() + "\u2026"
    }

    private val WHITESPACE = Regex("\\s+")

    private fun readBool(argumentsJson: String, key: String, fallback: Boolean): Boolean = try {
        JSONObject(argumentsJson).optBoolean(key, fallback)
    } catch (e: Exception) {
        fallback
    }
}

object AgentTraceCodec {

    const val MAX_REASONING_CHARS = 6000

    private const val VERSION = 1

    fun encode(trace: AgentTrace?): String? {
        if (trace == null || !trace.hasContent) return null
        val steps = JSONArray()
        trace.steps.forEach { step ->
            steps.put(
                JSONObject()
                    .put("k", step.kind.name)
                    .put("s", step.status.name)
                    .put("t", step.toolName)
                    .put("d", step.detail)
                    .put("v", step.variant)
                    .put("x", step.text)
                    .put("e", step.errorText)
                    .put("m", step.millis)
            )
        }
        return JSONObject()
            .put("v", VERSION)
            .put("status", trace.status.name)
            .put("reasoning", trace.reasoning.take(MAX_REASONING_CHARS))
            .put("budget", trace.budgetLabel)
            .put("steps", steps)
            .toString()
    }

    fun decode(text: String?): AgentTrace? {
        if (text.isNullOrBlank()) return null
        val root = try { JSONObject(text) } catch (e: Exception) { return null }
        val array = root.optJSONArray("steps") ?: JSONArray()
        val steps = ArrayList<AgentStep>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            steps.add(
                AgentStep(
                    kind = kindOf(o.optString("k")),
                    status = statusOf(o.optString("s")),
                    toolName = o.optString("t"),
                    detail = o.optString("d"),
                    variant = o.optString("v"),
                    text = o.optString("x"),
                    errorText = o.optString("e"),
                    millis = o.optLong("m", 0L)
                )
            )
        }
        val trace = AgentTrace(
            steps = steps,
            reasoning = root.optString("reasoning"),
            status = statusOf(root.optString("status")),
            budgetLabel = root.optString("budget")
        )
        return if (trace.hasContent) trace else null
    }

    private fun kindOf(value: String): AgentStepKind =
        AgentStepKind.entries.firstOrNull { it.name == value } ?: AgentStepKind.NOTE

    private fun statusOf(value: String): AgentStepStatus =
        AgentStepStatus.entries.firstOrNull { it.name == value } ?: AgentStepStatus.DONE
}

class AgentTraceRecorder {

    private val _steps = mutableStateListOf<AgentStep>()

    private val lock = Any()

    private val toolStarted = HashMap<Int, Long>()

    private val reasoningBuffer = StringBuilder()

    private val lineBuffer = StringBuilder()

    private var reasoningVersion by mutableStateOf(0)

    private var statusValue by mutableStateOf(AgentStepStatus.RUNNING)

    private var budgetValue by mutableStateOf("")

    private var reasoningStepsInBlock = 0

    val steps: List<AgentStep> get() = _steps

    val status: AgentStepStatus get() = statusValue

    val budgetLabel: String get() = budgetValue

    val reasoning: String
        get() {
            reasoningVersion
            return synchronized(lock) { reasoningBuffer.toString() }
        }

    fun snapshot(): AgentTrace = AgentTrace(
        steps = _steps.toList(),
        reasoning = reasoning,
        status = statusValue,
        budgetLabel = budgetValue
    )

    fun setContextBudget(text: String) {
        budgetValue = text
    }

    fun beginBlock() {
        reasoningStepsInBlock = 0
    }

    fun appendReasoning(piece: String) {
        if (piece.isEmpty()) return
        val finished = ArrayList<String>(2)
        synchronized(lock) {
            reasoningBuffer.append(piece)
            lineBuffer.append(piece)
            var text = lineBuffer.toString()
            var consumed = 0
            while (true) {
                val nl = text.indexOf('\n', consumed)
                if (nl < 0) break
                val line = text.substring(consumed, nl).trim()
                if (line.isNotBlank()) finished.add(line)
                consumed = nl + 1
            }
            if (consumed > 0) lineBuffer.delete(0, consumed)
            if (lineBuffer.length > REASONING_LINE_MAX) {
                val pending = lineBuffer.toString()
                var cut = pending.lastIndexOf(' ', REASONING_LINE_MAX)
                if (cut < REASONING_LINE_MIN) cut = REASONING_LINE_MAX
                val line = pending.substring(0, cut).trim()
                if (line.isNotBlank()) finished.add(line)
                lineBuffer.delete(0, cut)
            }
        }
        reasoningVersion++
        finished.forEach { addReasoningLine(it) }
    }

    fun endReasoningBlock() {
        val tail = synchronized(lock) {
            val pending = lineBuffer.toString().trim()
            lineBuffer.setLength(0)
            pending
        }
        if (tail.isNotBlank()) addReasoningLine(tail)
    }

    fun addReasoningLine(text: String) {
        if (reasoningStepsInBlock >= MAX_REASONING_STEPS_PER_BLOCK) return
        val line = AgentTraceLabels.summarize(text, REASONING_SUMMARY_MAX)
        if (line.isBlank()) return
        if (_steps.size >= MAX_STEPS) return
        reasoningStepsInBlock++
        _steps.add(AgentStep(kind = AgentStepKind.REASONING, status = AgentStepStatus.DONE, text = line))
    }

    fun addPlanning(text: String) {
        val line = AgentTraceLabels.summarize(text, PLANNING_SUMMARY_MAX)
        if (line.isBlank()) return
        addNote(S.agentTracePlanning, line)
    }

    fun addNote(text: String, detail: String = "", status: AgentStepStatus = AgentStepStatus.DONE) {
        if (_steps.size >= MAX_STEPS) return
        _steps.add(AgentStep(kind = AgentStepKind.NOTE, status = status, detail = detail, text = text))
    }

    fun startTool(toolName: String, argumentsJson: String): Int {
        endReasoningBlock()
        if (_steps.size >= MAX_STEPS) return -1
        _steps.add(
            AgentStep(
                kind = AgentStepKind.TOOL,
                status = AgentStepStatus.RUNNING,
                toolName = toolName,
                detail = AgentTraceLabels.detailFor(toolName, argumentsJson),
                variant = AgentTraceLabels.variantFor(toolName, argumentsJson)
            )
        )
        val index = _steps.lastIndex
        if (index >= 0) synchronized(lock) { toolStarted[index] = System.currentTimeMillis() }
        return index
    }

    fun beginToolRun(index: Int) {
        if (index < 0) return
        synchronized(lock) { toolStarted[index] = System.currentTimeMillis() }
    }

    fun finishTool(index: Int, result: ToolExecResult) {
        val millis = elapsedSince(index)
        update(index) {
            it.copy(
                status = if (result.success) AgentStepStatus.DONE else AgentStepStatus.FAILED,
                errorText = if (result.success) "" else AgentTraceLabels.summarize(result.summary, ERROR_MAX),
                millis = millis
            )
        }
    }

    fun failTool(index: Int, error: String) {
        val millis = elapsedSince(index)
        update(index) {
            it.copy(
                status = AgentStepStatus.FAILED,
                errorText = AgentTraceLabels.summarize(error, ERROR_MAX),
                millis = millis
            )
        }
    }

    fun cancelTool(index: Int, reason: String = "") {
        synchronized(lock) { toolStarted.remove(index) }
        update(index) {
            it.copy(status = AgentStepStatus.CANCELLED, errorText = AgentTraceLabels.summarize(reason, ERROR_MAX))
        }
    }

    fun finish(status: AgentStepStatus) {
        statusValue = status
    }

    private fun elapsedSince(index: Int): Long = synchronized(lock) {
        val started = toolStarted.remove(index) ?: return 0L
        (System.currentTimeMillis() - started).coerceAtLeast(0L)
    }

    private fun update(index: Int, block: (AgentStep) -> AgentStep) {
        if (index < 0 || index >= _steps.size) return
        _steps[index] = block(_steps[index])
    }

    private companion object {
        const val MAX_STEPS = 60
        const val MAX_REASONING_STEPS_PER_BLOCK = 10
        const val REASONING_SUMMARY_MAX = 110
        const val PLANNING_SUMMARY_MAX = 120
        const val ERROR_MAX = 240
        const val REASONING_LINE_MAX = 220
        const val REASONING_LINE_MIN = 40
    }
}
