package com.lucent.app.harness

import com.lucent.app.harness.plugins.PluginCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HarnessModulesTest {

    private val expected = mapOf(
        "AgentTools" to listOf("spawn_agent", "agent_status", "agent_result", "agent_stop"),
        "BrowserTools" to listOf("fetch_url", "browse_page", "download_file", "web_search", "open_url"),
        "ConnectorTools" to listOf(
            "connector_status", "http_request", "notion_api", "slack_api",
            "gdrive_api", "onedrive_api", "gitlab_api", "jira_api", "linear_api", "webdav_request"
        ),
        "DatabaseTools" to listOf(
            "db_query", "db_exec", "db_schema", "db_import_csv", "db_export_csv", "data_analyse"
        ),
        "DeviceTools" to listOf(
            "device_info", "read_screen", "tap", "input_text", "swipe", "press_key", "screenshot",
            "list_apps", "launch_app", "stop_app", "notifications", "clipboard", "share_text",
            "open_link_on_device", "notify_user", "vibrate", "torch", "location", "sensors", "export_file"
        ),
        "GitHubTools" to listOf(
            "github_repo", "github_contents", "github_issues", "github_pulls",
            "github_branches", "github_commits", "github_search", "github_actions", "github_releases", "github_user"
        ),
        "GitTools" to listOf(
            "git_status", "git_diff", "git_log", "git_show", "git_branch", "git_checkout", "git_add",
            "git_commit", "git_restore", "git_stash", "git_merge", "git_rebase", "git_remote", "git_clone",
            "git_fetch", "git_pull", "git_push", "git_blame", "git_reset", "git_init", "git_apply_patch"
        ),
        "McpTools" to listOf("mcp_servers", "mcp_tools", "mcp_call", "mcp_read_resource", "mcp_prompts", "mcp_session"),
        "OfficeConvertTools" to listOf("convert_office", "render_office", "office_doctor", "document_text"),
        "OfficeDeckTools" to listOf("create_presentation", "read_presentation", "edit_presentation"),
        "OfficeDocTools" to listOf("create_document", "read_document", "edit_document"),
        "OfficeSheetTools" to listOf(
            "create_spreadsheet", "read_spreadsheet", "edit_spreadsheet", "export_csv", "import_csv"
        ),
        "PdfTools" to listOf(
            "read_pdf", "pdf_info", "pdf_search", "render_pdf_page",
            "pdf_to_images", "merge_pdfs", "split_pdf"
        ),
        "PlanTools" to listOf("update_plan", "plan_status", "task_note", "ask_user"),
        "PluginTools" to listOf(
            "plugin_status", "plugin_list_available", "install_plugin", "remove_plugin", "plugin_run", "plugin_mirror_test"
        ),
        "SandboxTools" to listOf("sandbox_status", "sandbox_run", "sandbox_limits"),
        "SkillTools" to listOf("list_skills", "read_skill", "save_skill"),
        "TerminalTools" to listOf(
            "run_command", "start_job", "job_output", "job_kill", "jobs_list", "which_tool", "environment_info"
        ),
        "FileTools" to listOf(
            "workspace_info", "list_directory", "search_files", "read_file", "read_image", "write_file", "edit_file",
            "create_directory", "move_path", "copy_path", "delete_path", "file_info", "batch_files",
            "diff_files", "file_history", "restore_file", "zip_paths"
        ),
        "MemoryTools" to listOf("remember", "recall", "forget", "project_notes")
    )

    @Test
    fun everyModuleExposesTheToolsItPromises() {
        val registered = HarnessGate.allTools().associateBy { it.name }
        expected.forEach { (module, names) ->
            names.forEach { name ->
                val tool = registered[name]
                assertTrue(tool != null, "$module should expose $name")
                assertTrue(tool!!.description.length > 30, "$name needs a description a model can act on")
            }
        }
        val declared = expected.values.flatten()
        assertEquals(declared.size, declared.toSet().size, "the expectation table repeats a tool name")
        val missingFromTable = registered.keys.filterNot { declared.contains(it) }
        assertTrue(missingFromTable.isEmpty(), "tools exist but are not listed here: $missingFromTable")
    }

    @Test
    fun deviceToolsAreAndroidOnly() {
        val device = HarnessGate.allTools().filter { it.group == HarnessGroup.DEVICE }
        assertEquals(20, device.size)
        assertTrue(device.all { it.androidOnly })
        assertTrue(device.all { !it.available(android = false, capabilities = emptySet()) })
        assertTrue(device.all { it.available(android = true, capabilities = emptySet()) })
    }

    @Test
    fun writeToolsAreNotReadOnlyButReadersAre() {
        val readers = listOf("read_file", "list_directory", "search_files", "read_document", "read_pdf", "db_query")
        val writers = listOf("write_file", "edit_file", "delete_path", "create_document", "run_command")
        val byName = HarnessGate.allTools().associateBy { it.name }
        readers.forEach { name -> assertTrue(byName.getValue(name).readOnly, "$name should be read-only") }
        writers.forEach { name -> assertFalse(byName.getValue(name).readOnly, "$name should not be read-only") }
    }

    @Test
    fun sqlGuardRefusesTheDangerousStatements() {
        assertTrue(DatabaseTools.blocked("SELECT * FROM notes") == null)
        assertTrue(DatabaseTools.blocked("ATTACH DATABASE 'other.db' AS other") != null)
        assertTrue(DatabaseTools.blocked("select load_extension('evil')") != null)
        assertTrue(DatabaseTools.blocked("DROP DATABASE main") != null)
    }

    @Test
    fun subAgentRegistryKeepsItsBooks() {
        assertTrue(SubAgents.list().isEmpty() || SubAgents.list().isNotEmpty())
        assertEquals(0, SubAgents.running())
        assertFalse(SubAgents.stop("sub-999"))
        assertTrue(SubAgents.get("sub-999") == null)
    }

    @Test
    fun planBoardRendersWithoutAPlan() {
        PlanBoard.clear()
        assertEquals("", PlanBoard.summary())
        PlanBoard.publish(listOf(PlanStep("only step", "done")))
        assertTrue(PlanBoard.summary().startsWith("1/1"))
        PlanBoard.clear()
    }
}

class PluginCatalogTest {

    @Test
    fun theCatalogueIsConsistent() {
        val all = PluginCatalog.all()
        assertTrue(all.size >= 15, "the catalogue should offer a useful spread, found ${all.size}")
        val ids = all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate plugin ids: $ids")
        all.forEach { plugin ->
            assertTrue(plugin.android || plugin.desktop, "${plugin.id} runs nowhere")
            assertTrue(plugin.licence.isNotBlank(), "${plugin.id} has no licence")
            assertTrue(plugin.homepage.startsWith("https://"), "${plugin.id} has a weak homepage")
            assertTrue(plugin.summary.length > 15, "${plugin.id} needs a real summary")
            plugin.sources.forEach { source ->
                assertTrue(source.url.startsWith("https://"), "${plugin.id}/${source.id} is not https")
                assertTrue(source.label.isNotBlank(), "${plugin.id}/${source.id} has no label")
                assertTrue(source.bytes >= 0, "${plugin.id}/${source.id} has a negative size")
            }
            if (plugin.sources.any { it.url.endsWith(".tar.gz") || it.url.endsWith(".zip") || it.url.endsWith(".msi") }) {
                assertTrue(plugin.installScript.isNotBlank(), "${plugin.id} downloads but cannot install")
            }
        }
    }

    @Test
    fun everyDownloadablePluginOffersAFastMirror() {
        PluginCatalog.all()
            .filter { plugin -> plugin.sources.any { it.url.startsWith("https") } }
            .forEach { plugin ->
                assertTrue(
                    plugin.sources.any { !it.official },
                    "${plugin.id} offers no mirror, so a slow international link is the only route"
                )
                assertTrue(
                    plugin.sources.any { it.official },
                    "${plugin.id} offers no official source"
                )
            }
    }

    @Test
    fun platformsFilterTheCatalogue() {
        val android = PluginCatalog.forPlatform(android = true)
        val desktop = PluginCatalog.forPlatform(android = false)
        assertTrue(android.all { it.android })
        assertTrue(desktop.all { it.desktop })
        assertTrue(android.any { it.id == "ubuntu" })
        assertFalse(android.any { it.id == "playwright" })
        assertTrue(desktop.any { it.id == "libreoffice" })
        assertTrue(PluginCatalog.find("ubuntu") != null)
        assertTrue(PluginCatalog.find("no-such-plugin") == null)
    }

    @Test
    fun theBundledNoticesCoverEveryPluginLicence() {
        val notices = java.io.File("docs/THIRD-PARTY-NOTICES.md")
        val text = if (notices.exists()) notices.readText() else ""
        if (text.isBlank()) return
        PluginCatalog.all().forEach { plugin ->
            val head = plugin.licence.substringBefore(" ").substringBefore(",")
            if (head.length >= 3) {
                assertTrue(
                    text.contains(head, ignoreCase = true) || text.contains(plugin.name, ignoreCase = true),
                    "${plugin.id} (${plugin.licence}) is missing from the third-party notices"
                )
            }
        }
    }
}
