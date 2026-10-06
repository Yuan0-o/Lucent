package com.lucent.app.harness

import com.lucent.app.network.ToolExecResult
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

object SkillTools : HarnessGroupTools {

    override val group = HarnessGroup.SKILLS

    private const val BUILT_IN = "built-in"

    override val tools: List<HarnessTool> = listOf(
        HarnessTool(
            name = "list_skills",
            group = group,
            permission = HarnessPermission.READ,
            description = "List the skills available here. A skill is a short instruction file that explains how to do " +
                "a particular job well in this workspace. Read the matching skill before starting a specialised task."
        ),
        HarnessTool(
            name = "read_skill",
            group = group,
            permission = HarnessPermission.READ,
            description = "Read one skill in full. Arguments: name (as listed by list_skills).",
            params = listOf(HarnessSchema.text("name", "Skill name"))
        ),
        HarnessTool(
            name = "save_skill",
            group = group,
            permission = HarnessPermission.WRITE,
            description = "Create or update a skill so the knowledge survives this conversation. Use it after working " +
                "out the right way to do something here. Arguments: name, description, body (markdown instructions).",
            params = listOf(
                HarnessSchema.text("name", "Short skill name"),
                HarnessSchema.text("description", "One line saying when to use it"),
                HarnessSchema.text("body", "Markdown instructions")
            )
        )
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JsonObject): ToolExecResult? = when (name) {
        "list_skills" -> list(ctx)
        "read_skill" -> read(ctx, args)
        "save_skill" -> save(ctx, args)
        else -> null
    }

    fun skillDirs(ctx: HarnessCtx): List<Path> {
        val dirs = mutableListOf(HarnessRuntime.workspacePath().toPath() / ".lucent" / "skills")
        ctx.config.skillDirs.forEach { dirs.add(it.toPath()) }
        return dirs
    }

    fun discover(ctx: HarnessCtx): List<Path> =
        skillDirs(ctx).filter { FileSystem.SYSTEM.metadataOrNull(it)?.isDirectory == true }
            .flatMap { dir -> 
                try {
                    FileSystem.SYSTEM.list(dir).filter { FileSystem.SYSTEM.metadataOrNull(it)?.isRegularFile == true && it.name.endsWith(".md") }
                } catch (e: Exception) {
                    emptyList()
                }
            }

    private fun list(ctx: HarnessCtx): ToolExecResult {
        val files = discover(ctx)
        if (files.isEmpty()) {
            return ToolExecResult(
                "No skills yet. Write one with save_skill, or drop markdown files into ${Workspace.display(ctx, HarnessRuntime.workspacePath() + "/.lucent/skills")}."
            )
        }
        val sb = StringBuilder("${files.size} skill(s):\n")
        files.forEach { file ->
            val content = FileSystem.SYSTEM.read(file) { readUtf8() }
            val description = content.lineSequence().firstOrNull { it.startsWith("description:") }?.removePrefix("description:")?.trim() ?: ""
            sb.append("- ").append(file.name.removeSuffix(".md"))
            if (description.isNotEmpty()) sb.append(": ").append(description)
            sb.append('\n')
        }
        return ToolExecResult(sb.toString().trimEnd())
    }

    private fun read(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val wanted = args["name"]?.jsonPrimitive?.content?.trim() ?: ""
        if (wanted.isEmpty()) return ToolExecResult("Which skill?", success = false)
        val normalised = wanted.removeSuffix(".md").lowercase()
        val file = discover(ctx).firstOrNull { it.name.removeSuffix(".md").lowercase() == normalised }
            ?: return ToolExecResult("No skill called $wanted. ${list(ctx).summary}", success = false)
        return ToolExecResult(Workspace.readText(file.toString(), 128 * 1024))
    }

    private fun save(ctx: HarnessCtx, args: JsonObject): ToolExecResult {
        val raw = args["name"]?.jsonPrimitive?.content?.trim()?.removeSuffix(".md") ?: ""
        if (raw.isEmpty()) return ToolExecResult("Name the skill.", success = false)
        val slug = raw.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        if (slug.isEmpty()) return ToolExecResult("That name has no usable characters.", success = false)
        val dir = HarnessRuntime.workspacePath().toPath() / ".lucent" / "skills"
        FileSystem.SYSTEM.createDirectories(dir)
        val file = dir / "$slug.md"
        val description = args["description"]?.jsonPrimitive?.content?.replace("\n", " ")?.trim() ?: ""
        val body = args["body"]?.jsonPrimitive?.content?.trim() ?: ""
        val text = "---\nname: $slug\ndescription: $description\n---\n\n$body\n"
        if (ctx.config.snapshots && FileSystem.SYSTEM.exists(file)) Snapshots.capture(ctx, file.toString())
        FileSystem.SYSTEM.write(file) { writeUtf8(text) }
        return ToolExecResult("Saved skill ${Workspace.display(ctx, file.toString())} (${text.length} characters).")
    }
}
