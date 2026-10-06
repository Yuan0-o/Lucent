package com.lucent.app.harness

import com.lucent.app.platform.PlatformContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

object SubAgentReports {

    const val FOLDER = "sub-agents"

    fun folder(): Path {
        val p = HarnessRuntime.workspacePath().toPath() / FOLDER
        FileSystem.SYSTEM.createDirectories(p)
        return p
    }

    fun write(context: PlatformContext, agent: SubAgent): Path {
        val file = folder() / (agent.id + ".md")
        FileSystem.SYSTEM.write(file) { writeUtf8(markdown(agent)) }
        AuditTrail.record(
            context,
            AuditEntry(
                at = System.currentTimeMillis(),
                tool = "sub_agent_report",
                group = "agent",
                permission = "write",
                approval = "allow",
                arguments = agent.id,
                outcome = "saved",
                detail = file.toString(),
                millis = 0L,
                files = listOf(file.toString())
            )
        )
        return file
    }

    private fun markdown(agent: SubAgent): String = buildString {
        append("# ").append(agent.id).append('\n').append('\n')
        append("- status: ").append(agent.status).append('\n')
        append("- rounds: ").append(agent.rounds).append('\n')
        append("- started: ").append(agent.startedAt).append('\n')
        append("- tools: ").append(agent.tools.joinToString(", ")).append('\n').append('\n')
        append("## Task").append('\n').append('\n').append(agent.task).append('\n').append('\n')
        append("## Work").append('\n').append('\n')
        agent.transcriptLines().forEach { line -> append("- ").append(line.replace('\n', ' ')).append('\n') }
        append('\n').append("## Result").append('\n').append('\n').append(agent.result).append('\n')
    }
}
