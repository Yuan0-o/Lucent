package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "update_plan" -> updatePlan(args)
        "plan_status" -> planStatus()
        "task_note" -> note(args)
        "ask_user" -> ask(args)
        else -> null
    }

    private suspend fun ask(args: JSONObject): ToolExecResult {
        val question = args.optString("question", "").trim()
        if (question.isEmpty()) return ToolExecResult("What should I ask?", success = false)
        val host = HarnessRuntime.host
            ?: return ToolExecResult("This build cannot ask the user anything.", success = false)
        val options = mutableListOf<String>()
        val array = args.optJSONArray("options")
        if (array != null) {
            for (i in 0 until array.length()) {
                val value = array.optString(i, "")
                if (value.isNotBlank()) options.add(value)
            }
        }
        val answer = host.askUser(question, options.take(3))
        return if (answer.isBlank()) ToolExecResult("The user did not answer.", success = false)
        else ToolExecResult(answer)
    }

    private fun updatePlan(args: JSONObject): ToolExecResult {
        val array = args.optJSONArray("steps") ?: JSONArray()
        if (array.length() == 0) return ToolExecResult("Give me the steps.", success = false)
        val steps = mutableListOf<PlanStep>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            when (item) {
                is String -> steps.add(PlanStep(item, "pending"))
                is JSONObject -> steps.add(
                    PlanStep(
                        item.optString("title", item.optString("step", "step ${i + 1}")),
                        item.optString("status", "pending").lowercase()
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

    private fun note(args: JSONObject): ToolExecResult {
        val text = args.optString("text", "").trim()
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

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "remember" -> remember(ctx, args)
        "recall" -> recall(ctx, args)
        "forget" -> forget(ctx, args)
        "project_notes" -> projectNotes(ctx, args)
        else -> null
    }

    private fun fileFor(ctx: HarnessCtx, scope: String): File {
        val dir = HarnessRuntime.subDir("memory")
        return when (scope.lowercase()) {
            "user" -> File(dir, "user.json")
            "project" -> File(dir, "project-" + HarnessRuntime.workspace().name.lowercase().replace(Regex("[^a-z0-9]+"), "-") + ".json")
            else -> File(dir, "session-" + HarnessRuntime.conversationId + ".json")
        }
    }

    private fun load(ctx: HarnessCtx, scope: String): JSONObject {
        val text = HarnessVault.read(ctx.context, fileFor(ctx, scope))
        return try { JSONObject(text) } catch (e: Exception) { JSONObject() }
    }

    private fun save(ctx: HarnessCtx, scope: String, json: JSONObject) {
        HarnessVault.write(ctx.context, fileFor(ctx, scope), json.toString())
    }

    private fun remember(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val scope = args.optString("scope", "project").lowercase()
        val key = args.optString("key", "").trim()
        val value = args.optString("value", "").trim()
        if (key.isEmpty() || value.isEmpty()) return ToolExecResult("Give me both a key and a value.", success = false)
        val json = load(ctx, scope)
        json.put(key, value)
        json.put("__updated", System.currentTimeMillis())
        save(ctx, scope, json)
        return ToolExecResult("Remembered ($scope) $key = $value")
    }

    private fun recall(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val scope = args.optString("scope", "").lowercase()
        val query = args.optString("query", "").lowercase()
        val scopes = if (scope.isBlank()) listOf("user", "project", "session") else listOf(scope)
        val sb = StringBuilder()
        scopes.forEach { name ->
            val json = load(ctx, name)
            val keys = json.keys()
            val lines = mutableListOf<String>()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith("__")) continue
                val value = json.optString(key, "")
                if (query.isNotEmpty() && !key.lowercase().contains(query) && !value.lowercase().contains(query)) continue
                lines.add("- $key: $value")
            }
            if (lines.isNotEmpty()) sb.append("[$name]\n").append(lines.joinToString("\n")).append('\n')
        }
        return if (sb.isEmpty()) ToolExecResult("Nothing remembered${if (query.isNotEmpty()) " about \"$query\"" else ""}.")
        else ToolExecResult(sb.toString().trimEnd())
    }

    private fun forget(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val scope = args.optString("scope", "project").lowercase()
        val key = args.optString("key", "").trim()
        if (key.isEmpty()) return ToolExecResult("Which key?", success = false)
        val json = load(ctx, scope)
        if (!json.has(key)) return ToolExecResult("Nothing stored under $key in $scope.", success = false)
        json.remove(key)
        save(ctx, scope, json)
        return ToolExecResult("Forgot ($scope) $key.")
    }

    private fun projectNotes(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val file = File(HarnessRuntime.workspace(), "LUCENT.md")
        val action = args.optString("action", "read").lowercase()
        if (action == "append") {
            val text = args.optString("text", "").trim()
            if (text.isEmpty()) return ToolExecResult("Nothing to append.", success = false)
            if (ctx.config.snapshots && file.exists()) Snapshots.capture(ctx, file)
            file.appendText((if (file.exists() && file.length() > 0) "\n" else "") + text + "\n")
            return ToolExecResult("Appended to ${Workspace.display(ctx, file)}.")
        }
        if (!file.exists()) return ToolExecResult("There are no project notes yet (no LUCENT.md in the workspace).")
        return ToolExecResult(Workspace.readText(file, 64 * 1024))
    }
}
