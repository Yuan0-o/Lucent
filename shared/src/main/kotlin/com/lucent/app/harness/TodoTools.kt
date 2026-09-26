package com.lucent.app.harness

import android.content.Context
import com.lucent.app.network.ToolExecResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TodoItem(val title: String, val status: String)

object TodoTools : HarnessGroupTools {

    override val group = HarnessGroup.PLAN

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "todo_write",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Replace the durable todo list for this conversation with the whole list you send, exactly " +
                "like the DeepSeek Harness todo_write: every item is {title, status} with status pending, active or " +
                "done, and anything you leave out is removed. Keep one item active at a time, tick items off as you " +
                "finish them, and send the complete list on every call rather than one item at a time.",
            params = listOf(
                HarnessSchema.list("todos", "Every item as {title, status}; status is pending, active or done")
            )
        ),
        HarnessTool(
            name = "todo_read",
            group = group,
            permission = HarnessPermission.READ,
            description = "Read back the durable todo list for this conversation together with how many items are " +
                "done. The list survives across turns, so call it after a break to see what is still outstanding."
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "todo_write" -> write(ctx, args)
        "todo_read" -> read(ctx)
        else -> null
    }

    private fun write(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val array = args.optJSONArray("todos") ?: JSONArray()
        val items = mutableListOf<TodoItem>()
        for (i in 0 until array.length()) {
            when (val entry = array.opt(i)) {
                is String -> if (entry.isNotBlank()) items.add(TodoItem(entry.trim(), "pending"))
                is JSONObject -> parse(entry, items)
                else -> {
                }
            }
        }
        if (items.isEmpty()) {
            return ToolExecResult("Give me the todo items as {title, status} or as strings.", success = false)
        }
        val conversation = conversationOf(args)
        TodoBoard.save(ctx.context, conversation, items)
        return ToolExecResult("Todo list (${items.size}):\n${TodoBoard.render(items)}")
    }

    private fun parse(entry: JSONObject, items: MutableList<TodoItem>) {
        val title = entry.optString("title", entry.optString("task", "")).trim()
        if (title.isEmpty()) return
        items.add(TodoItem(title, todoStatus(entry.optString("status", "pending"))))
    }

    private fun read(ctx: HarnessCtx): ToolExecResult {
        val items = TodoBoard.todos()
        if (items.isEmpty()) return ToolExecResult("The todo list is empty.")
        val done = items.count { it.status == "done" }
        return ToolExecResult("Todo list ($done/${items.size} done):\n${TodoBoard.render(items)}")
    }

    private fun conversationOf(args: JSONObject): Long {
        val stated = args.optLong("conversation_id", 0L)
        if (stated > 0L) return stated
        val current = HarnessRuntime.conversationId
        return if (current > 0L) current else 1L
    }
}

object TodoBoard {

    private val changeFlow = MutableStateFlow(0L)

    @Volatile private var items: List<TodoItem> = emptyList()

    val changes: StateFlow<Long> = changeFlow.asStateFlow()

    fun todos(): List<TodoItem> = items

    fun save(context: Context, conversationId: Long, list: List<TodoItem>) {
        items = list
        TodoFiles.write(context, conversationId, list)
        publish()
    }

    fun load(context: Context, conversationId: Long) {
        items = TodoFiles.read(context, conversationId)
        publish()
    }

    fun clear() {
        items = emptyList()
        publish()
    }

    fun render(list: List<TodoItem>): String = list.joinToString("\n") { item ->
        val mark = when (item.status) {
            "done" -> "[x]"
            "active" -> "[~]"
            else -> "[ ]"
        }
        "$mark ${item.title}"
    }

    fun summary(): String {
        val list = items
        if (list.isEmpty()) return ""
        val done = list.count { it.status == "done" }
        return "$done/${list.size} — " + list.joinToString("; ") { "${it.status}: ${it.title}" }
    }

    private fun publish() {
        changeFlow.value = changeFlow.value + 1L
    }
}

object TodoFiles {

    fun fileFor(conversationId: Long): File =
        File(HarnessRuntime.subDir("todos"), "conv-" + conversationId.coerceAtLeast(1L) + ".json")

    fun read(context: Context, conversationId: Long): List<TodoItem> {
        val text = HarnessVault.read(context, fileFor(conversationId))
        if (text.isBlank()) return emptyList()
        return try {
            parse(JSONArray(text))
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun write(context: Context, conversationId: Long, list: List<TodoItem>) {
        val array = JSONArray()
        list.forEach { item ->
            array.put(JSONObject().put("title", item.title).put("status", item.status))
        }
        HarnessVault.write(context, fileFor(conversationId), array.toString())
    }

    private fun parse(array: JSONArray): List<TodoItem> {
        val out = mutableListOf<TodoItem>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val title = entry.optString("title", "").trim()
            if (title.isEmpty()) continue
            out.add(TodoItem(title, todoStatus(entry.optString("status", "pending"))))
        }
        return out
    }
}

internal fun todoStatus(raw: String): String = when (raw.trim().lowercase()) {
    "active", "running", "doing", "in_progress", "current" -> "active"
    "done", "complete", "completed", "finished" -> "done"
    else -> "pending"
}
