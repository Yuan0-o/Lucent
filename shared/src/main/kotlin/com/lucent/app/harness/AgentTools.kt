package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SubAgent internal constructor(
    val id: String,
    val task: String,
    val tools: Set<String>,
    val startedAt: Long
) {
    @Volatile var status: String = "running"
    @Volatile var result: String = ""
    @Volatile var rounds: Int = 0
    internal val transcript = mutableListOf<String>()
    internal var job: Job? = null

    fun render(withResult: Boolean): String {
        val head = "$id [$status] rounds=$rounds — ${task.take(200)}"
        val tail = transcript.takeLast(6).joinToString("\n")
        return when {
            withResult && result.isNotEmpty() -> "$head\n$tail\n--- result ---\n$result"
            else -> "$head\n$tail"
        }
    }
}

object SubAgents {

    private const val MAX_ROUNDS = 12
    private val counter = AtomicInteger(1)
    private val agents = ConcurrentHashMap<String, SubAgent>()

    fun list(): List<SubAgent> = agents.values.sortedByDescending { it.startedAt }

    fun get(id: String): SubAgent? = agents[id]

    fun running(): Int = agents.values.count { it.status == "running" }

    fun prune() {
        val finished = agents.values.filter { it.status != "running" }.sortedByDescending { it.startedAt }
        finished.drop(6).forEach { agents.remove(it.id) }
    }

    fun start(parent: HarnessCtx, task: String, toolNames: Set<String>, model: String): SubAgent {
        val id = "sub-${counter.getAndIncrement()}"
        val agent = SubAgent(id, task, toolNames, System.currentTimeMillis())
        agents[id] = agent
        prune()
        val llm = HarnessRuntime.llm
        if (llm == null) {
            agent.status = "failed"
            agent.result = "This build cannot run sub-agents."
            return agent
        }
        agent.job = HarnessRuntime.background().launch {
            try {
                val system = buildSystem(parent, toolNames)
                while (agent.rounds < MAX_ROUNDS && agent.status == "running") {
                    agent.rounds++
                    val step = llm.step(system, task, agent.transcript.toList(), toolNames, model)
                    if (step.error.isNotEmpty()) {
                        agent.status = "failed"
                        agent.result = step.error
                        break
                    }
                    if (step.text.isNotBlank()) agent.transcript.add("agent: ${step.text.take(2000)}")
                    if (step.toolCalls.isEmpty()) {
                        agent.result = step.text
                        agent.status = "done"
                        break
                    }
                    for (call in step.toolCalls) {
                        val allowed = toolNames.isEmpty() || toolNames.contains(call.name)
                        val outcome = if (!allowed) {
                            ToolExecResult("${call.name} is not available to a sub-agent.", success = false)
                        } else {
                            HarnessGate.execute(parent.context, parent.db, call.name, call.argumentsJson)
                        }
                        agent.transcript.add(
                            "tool ${call.name} -> ${if (outcome.success) "ok" else "failed"}: ${outcome.summary.take(1200)}"
                        )
                    }
                }
                if (agent.status == "running") {
                    agent.status = "stopped"
                    agent.result = agent.result.ifEmpty { "Stopped after $MAX_ROUNDS rounds." }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                agent.status = "stopped"
                agent.result = "Stopped."
                throw e
            } catch (e: Exception) {
                agent.status = "failed"
                agent.result = e.message ?: e::class.simpleName ?: "failed"
            }
        }
        return agent
    }

    fun stop(id: String): Boolean {
        val agent = agents[id] ?: return false
        agent.job?.cancel()
        agent.status = "stopped"
        if (agent.result.isEmpty()) agent.result = "Stopped by the parent agent."
        return true
    }

    private fun buildSystem(parent: HarnessCtx, toolNames: Set<String>): String {
        val catalogue = if (toolNames.isEmpty()) {
            HarnessGate.enabledTools(parent.android).joinToString(", ") { it.name }
        } else toolNames.joinToString(", ")
        return "You are a focused sub-agent working inside Lucent. Finish the single task you were given and then " +
            "reply with your findings; nothing else. The workspace is ${HarnessRuntime.workspace().path}. " +
            "Tools you may call: $catalogue. Keep your final answer short and factual, and say plainly if you could " +
            "not do something."
    }
}

object AgentTools : HarnessGroupTools {

    override val group = HarnessGroup.AGENT

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "spawn_agent",
            group = group,
            permission = HarnessPermission.EXECUTE,
            description = "Hand a self-contained task to a sub-agent that works in the background with its own context, " +
                "and get an id straight away. Use it for independent research or long searches so the main conversation " +
                "stays short. Poll with agent_status and read the answer with agent_result.",
            params = listOf(
                HarnessSchema.text("task", "Exactly what the sub-agent must do and what to report back"),
                HarnessSchema.list("tools", "Tool names it may use; omit for all of them", false),
                HarnessSchema.text("model", "Model to use for the sub-agent", false)
            )
        ),
        HarnessTool(
            name = "agent_status",
            group = group,
            permission = HarnessPermission.READ,
            description = "Show a sub-agent's state and its recent steps. Omit the id to list every sub-agent.",
            params = listOf(HarnessSchema.text("id", "Sub-agent id", false))
        ),
        HarnessTool(
            name = "agent_result",
            group = group,
            permission = HarnessPermission.READ,
            description = "Read a sub-agent's final answer; waits a little for it to finish when asked.",
            params = listOf(
                HarnessSchema.text("id", "Sub-agent id"),
                HarnessSchema.number("wait_seconds", "How long to wait for it", false)
            )
        ),
        HarnessTool(
            name = "agent_stop",
            group = group,
            permission = HarnessPermission.EXECUTE,
            description = "Stop a running sub-agent. Arguments: id.",
            params = listOf(HarnessSchema.text("id", "Sub-agent id"))
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JSONObject): ToolExecResult? = when (name) {
        "spawn_agent" -> spawn(ctx, args)
        "agent_status" -> status(args)
        "agent_result" -> result(args)
        "agent_stop" -> stop(args)
        else -> null
    }

    private fun spawn(ctx: HarnessCtx, args: JSONObject): ToolExecResult {
        val task = args.optString("task", "").trim()
        if (task.isEmpty()) return ToolExecResult("Tell the sub-agent what to do.", success = false)
        if (!ctx.config.subAgents) return ToolExecResult("Sub-agents are switched off in Settings.", success = false)
        if (SubAgents.running() >= ctx.config.maxSubAgents.coerceIn(1, 8)) {
            return ToolExecResult("Too many sub-agents are already running; wait for one to finish.", success = false)
        }
        val tools = mutableSetOf<String>()
        val array: JSONArray? = args.optJSONArray("tools")
        if (array != null) {
            for (i in 0 until array.length()) {
                val value = array.optString(i, "")
                if (value.isNotBlank()) tools.add(value)
            }
        }
        val agent = SubAgents.start(ctx, task, tools, args.optString("model", ""))
        return ToolExecResult(
            "Started ${agent.id}. Ask for agent_status(id=\"${agent.id}\") or agent_result(id=\"${agent.id}\") later."
        )
    }

    private fun status(args: JSONObject): ToolExecResult {
        val id = args.optString("id", "")
        if (id.isBlank()) {
            val all = SubAgents.list()
            if (all.isEmpty()) return ToolExecResult("No sub-agents have been started.")
            return ToolExecResult(all.joinToString("\n\n") { it.render(withResult = false) })
        }
        val agent = SubAgents.get(id) ?: return ToolExecResult("No sub-agent called $id.", success = false)
        return ToolExecResult(agent.render(withResult = false))
    }

    private suspend fun result(args: JSONObject): ToolExecResult {
        val id = args.optString("id", "")
        val agent = SubAgents.get(id) ?: return ToolExecResult("No sub-agent called $id.", success = false)
        val wait = args.optInt("wait_seconds", 0).coerceIn(0, 300)
        val deadline = System.currentTimeMillis() + wait * 1000L
        while (agent.status == "running" && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(400)
        }
        return ToolExecResult(agent.render(withResult = true), success = agent.status != "failed")
    }

    private fun stop(args: JSONObject): ToolExecResult {
        val id = args.optString("id", "")
        if (id.isBlank()) return ToolExecResult("Which sub-agent?", success = false)
        return if (SubAgents.stop(id)) ToolExecResult("Asked $id to stop.")
        else ToolExecResult("No sub-agent called $id.", success = false)
    }
}
