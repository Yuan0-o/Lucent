package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import kotlinx.serialization.json.*
import okio.Path
import okio.Path.Companion.toPath
import okio.FileSystem
import okio.buffer

object PlanTools : HarnessGroupTools {

    override val group = HarnessGroup.PLAN

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "update_plan",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Publish the step-by-step plan for the current task so the user can watch progress. Send the " +
                "whole list each time, with each step marked pending, active or done. Use three to eight short steps; " +
                "update it as you go rather than once at the end.",
            params = listOf(
                HarnessSchema.list("steps", "Every step: either a string, or an object {title, status}")
            )
        ),
        HarnessTool(
            name = "plan_status",
            group = group,
            permission = HarnessPermission.READ,
            description = "Read back the current plan and its progress."
        ),
        HarnessTool(
            name = "ask_user",
            group = group,
            permission = HarnessPermission.READ,
            description = "Ask the user a question and wait for the answer. Give up to three short options; the answer " +
                "comes back as the chosen option or as free text. Use it when a choice genuinely needs the user.",
            params = listOf(
                HarnessSchema.text("question", "Question to ask"),
                HarnessSchema.list("options", "Up to three short options", false)
            )
        ),
        HarnessTool(
            name = "task_note",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Record a note the user will see in the agent trace while you work: a decision, a finding or " +
                "a warning. Keep it to one sentence; put long detail in a file instead.",
            params = listOf(HarnessSchema.text("text", "One sentence to show"))
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JsonObject): ToolExecResult? = when (name) {
        "update_plan" -> updatePlan(args)
        "plan_status" -> planStatus()
        "task_note" -> note(args)
        "ask_user" -> ask(args)
        else -> null
    }

    private suspend fun ask(args: JsonObject): ToolExecResult {
        val question = args["question"]?.jsonPrimitive?.content?.trim() ?: ""
        if (question.isEmpty()) return ToolExecResult("What should I ask?", success = false)
        val host = HarnessRuntime.host
            ?: return ToolExecResult("This build cannot ask the user anything.", success = false)
        val options = mutableListOf<String>()
        val array = args["options"]?.jsonArray
        if (array != null) {
            for (i in 0 until array.size) {
                val value = (array[i] as? JsonPrimitive)?.content ?: ""
                if (value.isNotBlank()) options.add(value)
            }
        }
        val answer = host.askUser(question, options.take(3))
        return if (answer.isBlank()) ToolExecResult("The user did not answer.", success = false)
        else ToolExecResult(answer)
    }

    private fun updatePlan(args: JsonObject): ToolExecResult {
        val array = args["steps"]?.jsonArray ?: JsonArray(emptyList())
        if (array.size == 0) return ToolExecResult("Give me the steps.", success = false)
        val steps = mutableListOf<PlanStep>()
        for (i in 0 until array.size) {
            val item = array[i]
            when (item) {
                is JsonPrimitive -> if (item.isString) steps.add(PlanStep(item.content, "pending"))
                is JsonObject -> steps.add(
                    PlanStep(
                        item["title"]?.jsonPrimitive?.content ?: item["step"]?.jsonPrimitive?.content ?: "step ${i + 1}",
                        item["status"]?.jsonPrimitive?.content?.lowercase() ?: "pending"
                    )
                )
                else -> {
                }
            }
        }
        if (steps.isEmpty()) return ToolExecResult("Give me the steps as strings or objects.", success = false)
        PlanBoard.publish(steps)
        val rendered = steps.joinToString("\n") { step ->
            val mark = when (step.status) {
                "done", "complete", "completed" -> "[x]"
                "active", "running", "doing" -> "[~]"
                "failed", "blocked" -> "[!]"
                else -> "[ ]"
            }
            "$mark ${step.title}"
        }
        return ToolExecResult("Plan updated:\n$rendered")
    }

    private fun planStatus(): ToolExecResult {
        val steps = PlanBoard.current()
        if (steps.isEmpty()) return ToolExecResult("No plan has been set for this task yet.")
        val done = steps.count { it.status.startsWith("done") || it.status.startsWith("complete") }
        return ToolExecResult(
            "Plan (${done}/${steps.size} done):\n" +
                steps.joinToString("\n") { "${it.status}  ${it.title}" }
        )
    }

    private fun note(args: JsonObject): ToolExecResult {
        val text = args["text"]?.jsonPrimitive?.content?.trim() ?: ""
        if (text.isEmpty()) return ToolExecResult("Nothing to note.", success = false)
        HarnessRuntime.note(text)
        return ToolExecResult("Noted.")
    }
}

data class PlanStep(val title: String, val status: String)

object PlanBoard {

    @Volatile private var steps: List<PlanStep> = emptyList()

    fun publish(list: List<PlanStep>) {
        steps = list
        HarnessRuntime.note(render(list))
    }

    fun current(): List<PlanStep> = steps

    fun clear() {
        steps = emptyList()
    }

    fun render(list: List<PlanStep>): String {
        val done = list.count { it.status.startsWith("done") || it.status.startsWith("complete") }
        return "$done/${list.size} — " + list.joinToString("; ") { "${it.status}: ${it.title}" }
    }

    fun summary(): String {
        val list = current()
        if (list.isEmpty()) return ""
        return render(list)
    }
}

object MemoryTools : HarnessGroupTools {

    override val group = HarnessGroup.MEMORY

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "remember",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Store a durable fact. scope is user (a lasting preference about the person), project (a fact " +
                "about the current workspace and its conventions) or session (only this conversation). Keep values " +
                "short; store long content in a file and remember the path.",
            params = listOf(
                HarnessSchema.text("scope", "user, project or session"),
                HarnessSchema.text("key", "Short label, for example preferred_report_style"),
                HarnessSchema.text("value", "What to remember")
            )
        ),
        HarnessTool(
            name = "recall",
            group = group,
            permission = HarnessPermission.READ,
            description = "Look up remembered facts by keyword, or list everything in one scope. Call it before asking " +
                "the user something they may already have told you.",
            params = listOf(
                HarnessSchema.text("query", "Keyword to look for", false),
                HarnessSchema.text("scope", "user, project or session", false)
            )
        ),
        HarnessTool(
            name = "forget",
            group = group,
            permission = HarnessPermission.DELETE,
            description = "Delete a remembered fact. Arguments: scope, key.",
            params = listOf(
                HarnessSchema.text("scope", "user, project or session"),
                HarnessSchema.text("key", "Key to delete")
            )
        ),
        HarnessTool(
            name = "project_notes",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Read or append the running notes for this workspace: conventions, build commands, what not " +
                "to touch. action is read or append.",
            params = listOf(
                HarnessSchema.text("action", "read or append"),
                HarnessSchema.text("text", "Text to append", false)
            )
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JsonObject): ToolExecResult? = when (name) {
        "remember" -> remember(ctx, args)
        "recall" -> recall(ctx, args)
        "forget" -> forget(ctx, args)
        "project_notes" -> projectNotes(ctx, args)
        else -> null
    }

    private fun fileFor(ctx: HarnessCtx, scope: String): Path {
        val dir = HarnessRuntime.subDirPath("memory").toPath()
        return when (scope.lowercase()) {
            "user" -> dir / "user.json"
            "project" -> dir / ("project-" + HarnessRuntime.workspacePath().toPath().name.lowercase().replace(Regex("[^a-z0-9]+"), "-") + ".json")
            else -> dir / ("session-" + HarnessRuntime.conversationId + ".json")
        }
    }

    private fun load(ctx: HarnessCtx, scope: String): JsonObject {
        val text = HarnessVault.read(ctx.context, fileFor(ctx, scope))
        return try { Json.parseToJsonElement(text).jsonObject } catch (e: Exception) { JsonObject(emptyMap()) }
    }

    private fun save(ctx: HarnessCtx, scope: String, json: JsonObject) {
        HarnessVault.write(ctx.context, fileFor(ctx, scope), json.toString())
    }

    private fun remember(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val scope = args["scope"]?.jsonPrimitive?.content?.lowercase() ?: "project"
        val key = args["key"]?.jsonPrimitive?.content?.trim() ?: ""
        val value = args["value"]?.jsonPrimitive?.content?.trim() ?: ""
        if (key.isEmpty() || value.isEmpty()) return ToolExecResult("Give me both a key and a value.", success = false)
        val json = load(ctx, scope)
        val newJson = buildJsonObject {
            json.forEach { entry -> put(entry.key, entry.value) }
            put(key, value)
            put("__updated", System.currentTimeMillis())
        }
        save(ctx, scope, newJson)
        return ToolExecResult("Remembered ($scope) $key = $value")
    }

    private fun recall(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val scope = args["scope"]?.jsonPrimitive?.content?.lowercase() ?: ""
        val query = args["query"]?.jsonPrimitive?.content?.lowercase() ?: ""
        val scopes = if (scope.isBlank()) listOf("user", "project", "session") else listOf(scope)
        val sb = StringBuilder()
        scopes.forEach { name ->
            val json = load(ctx, name)
            val keys = json.keys.iterator()
            val lines = mutableListOf<String>()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith("__")) continue
                val value = json[key]?.jsonPrimitive?.content ?: ""
                if (query.isNotEmpty() && !key.lowercase().contains(query) && !value.lowercase().contains(query)) continue
                lines.add("- $key: $value")
            }
            if (lines.isNotEmpty()) sb.append("[$name]\n").append(lines.joinToString("\n")).append('\n')
        }
        return if (sb.isEmpty()) ToolExecResult("Nothing remembered${if (query.isNotEmpty()) " about \"$query\"" else ""}.")
        else ToolExecResult(sb.toString().trimEnd())
    }

    private fun forget(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val scope = args["scope"]?.jsonPrimitive?.content?.lowercase() ?: "project"
        val key = args["key"]?.jsonPrimitive?.content?.trim() ?: ""
        if (key.isEmpty()) return ToolExecResult("Which key?", success = false)
        val json = load(ctx, scope)
        if (!json.containsKey(key)) return ToolExecResult("Nothing stored under $key in $scope.", success = false)
        val newJson = buildJsonObject {
            json.forEach { entry ->
                if (entry.key != key) put(entry.key, entry.value)
            }
        }
        save(ctx, scope, newJson)
        return ToolExecResult("Forgot ($scope) $key.")
    }

    private fun projectNotes(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val file = HarnessRuntime.workspacePath().toPath() / "LUCENT.md"
        val action = args["action"]?.jsonPrimitive?.content?.lowercase() ?: "read"
        if (action == "append") {
            val text = args["text"]?.jsonPrimitive?.content?.trim() ?: ""
            if (text.isEmpty()) return ToolExecResult("Nothing to append.", success = false)
            if (ctx.config.snapshots && FileSystem.SYSTEM.exists(file)) Snapshots.capture(ctx, file.toString())
            val exists = FileSystem.SYSTEM.exists(file)
            val len = if (exists) FileSystem.SYSTEM.metadataOrNull(file)?.size ?: -1 else -1
            val prefix = if (exists && len > 0) "\n" else ""
            FileSystem.SYSTEM.appendingSink(file).buffer().use { it.writeUtf8(prefix + text + "\n") }
            return ToolExecResult("Appended to ${Workspace.display(ctx, file.toString())}.")
        }
        if (!FileSystem.SYSTEM.exists(file)) return ToolExecResult("There are no project notes yet (no LUCENT.md in the workspace).")
        return ToolExecResult(Workspace.readText(file.toString(), 64 * 1024))
    }
}
