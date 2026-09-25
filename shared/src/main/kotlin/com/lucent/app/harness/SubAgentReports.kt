package com.lucent.app.harness

import android.content.Context
import java.io.File

object SubAgentReports {

    const val FOLDER = "sub-agents"

    fun folder(): File = File(HarnessRuntime.workspace(), FOLDER).apply { mkdirs() }

    fun write(context: Context, agent: SubAgent): File {
        val file = File(folder(), agent.id + ".md")
        file.writeText(markdown(agent))
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
                detail = file.path,
                millis = 0L,
                files = listOf(file.path)
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
