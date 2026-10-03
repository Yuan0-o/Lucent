package com.lucent.app.harness

import android.content.Context
import com.lucent.app.data.AppDatabase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class GitToolsTest {

    private val permissions = mapOf(
        "git_status" to HarnessPermission.READ,
        "git_diff" to HarnessPermission.READ,
        "git_log" to HarnessPermission.READ,
        "git_show" to HarnessPermission.READ,
        "git_blame" to HarnessPermission.READ,
        "git_branch" to HarnessPermission.GIT,
        "git_checkout" to HarnessPermission.GIT,
        "git_add" to HarnessPermission.GIT,
        "git_commit" to HarnessPermission.GIT,
        "git_restore" to HarnessPermission.GIT,
        "git_stash" to HarnessPermission.GIT,
        "git_merge" to HarnessPermission.GIT,
        "git_rebase" to HarnessPermission.GIT,
        "git_remote" to HarnessPermission.GIT,
        "git_clone" to HarnessPermission.GIT,
        "git_fetch" to HarnessPermission.GIT,
        "git_pull" to HarnessPermission.GIT,
        "git_push" to HarnessPermission.GIT,
        "git_reset" to HarnessPermission.GIT,
        "git_init" to HarnessPermission.GIT,
        "git_apply_patch" to HarnessPermission.WRITE
    )

    @Test
    fun toolsAreUniqueSnakeCaseAndCarryTheirPermission() {
        val names = GitTools.tools.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertEquals(permissions.keys, names.toSet())
        for (tool in GitTools.tools) {
            assertTrue(tool.name.matches(Regex("[a-z][a-z0-9_]*")), "${tool.name} is not snake_case")
            assertEquals(expected = permissions[tool.name], actual = tool.permission, message = tool.name)
            assertEquals(expected = HarnessPermission.READ == tool.permission, actual = tool.readOnly, message = tool.name)
            assertEquals(expected = HarnessGroup.GIT, actual = tool.group, message = tool.name)
            assertTrue(tool.description.isNotBlank(), tool.name)
            assertTrue(tool.description.length <= 420, "${tool.name} has ${tool.description.length} description characters")
        }
    }

    @Test
    fun quoteSurvivesQuotesAndSpaces() {
        assertEquals("'main'", quote("main"))
        assertEquals("'my file.txt'", quote("my file.txt"))
        assertEquals("'it'\\''s'", quote("it's"))
        assertEquals("''", quote(""))
        assertEquals("'a'\\''b'\\''c'", quote("a'b'c"))
        assertFalse(quote("two words").startsWith("\""))
    }

    @Test
    fun blockedRejectsDangerousFlags() {
        assertNotNull(blocked("push --force origin main"))
        assertNotNull(blocked("push -f origin main"))
        assertNotNull(blocked("push --force-with-lease origin main"))
        assertNotNull(blocked("reset --hard HEAD~1"))
        assertNotNull(blocked("clean -fdx"))
        assertNotNull(blocked("clean -fd"))
        assertNotNull(blocked("filter-branch --tree-filter ls HEAD"))
        assertNotNull(blocked("update-ref -d refs/heads/main"))
        assertNotNull(blocked("push --delete origin main"))
        assertNotNull(blocked("push -d origin main"))
        assertNotNull(blocked("reflog expire --expire=now --all"))
        assertNotNull(blocked("gc --prune=now"))
        assertNull(blocked("status --porcelain=v1 -b"))
        assertNull(blocked("log --oneline --decorate -n 20"))
        assertNull(blocked("branch -D old-feature"))
        assertNull(blocked("clean -n"))
        assertNull(blocked("commit -m 'a normal message'"))
    }

    @Test
    fun blockedRejectsShellSyntax() {
        assertNotNull(blocked("status; rm -rf build"))
        assertNotNull(blocked("status && rm -rf build"))
        assertNotNull(blocked("status || true"))
        assertNotNull(blocked("status | cat"))
        assertNotNull(blocked("log `whoami`"))
        assertNotNull(blocked("log \$(whoami)"))
        assertNotNull(blocked("log > out.txt"))
        assertNotNull(blocked("log < in.txt"))
        assertNotNull(blocked(""))
    }

    @Test
    fun relativePathsStayInsideTheRepository() {
        assertNull(relativePathProblem("src/main/kotlin/App.kt"))
        assertNull(relativePathProblem("app/build.gradle.kts"))
        assertNull(relativePathProblem("."))
        assertNotNull(relativePathProblem("/etc/passwd"))
        assertNotNull(relativePathProblem("../../secrets"))
        assertNotNull(relativePathProblem("src/../../outside"))
        assertNotNull(relativePathProblem("~/.ssh/id_rsa"))
        assertNotNull(relativePathProblem("C:/Windows/System32"))
        assertNotNull(relativePathProblem("--upload-pack=evil"))
        assertNotNull(relativePathProblem("a; rm -rf b"))
        assertNotNull(relativePathProblem(""))
        assertNotNull(pathListProblem(emptyList()))
        assertNotNull(pathListProblem(listOf("src/ok.kt", "../bad.kt")))
        assertNull(pathListProblem(listOf("src/ok.kt", "app/main.kt")))
    }

    @Test
    fun revisionsUrlsAndPathsAreSplitForTheCommandBuilders() {
        assertNull(revisionProblem("HEAD~2", "ref"))
        assertNull(revisionProblem("origin/main", "ref"))
        assertNotNull(revisionProblem("", "ref"))
        assertNotNull(revisionProblem("--force", "ref"))
        assertNotNull(revisionProblem("two words", "ref"))
        assertNull(urlProblem("https://github.com/lucent/app.git"))
        assertNotNull(urlProblem(""))
        assertNotNull(urlProblem("--upload-pack=evil"))
        assertNotNull(urlProblem("https://example.com/a b"))
        assertNotNull(urlProblem("https://example.com/a;rm"))
        assertEquals(listOf("a.txt", "b/c.txt"), splitPathList("  a.txt   b/c.txt "))
        assertEquals("'a.txt' 'b/c.txt'", quotedPaths(listOf("a.txt", "b/c.txt")))
    }

    @Test
    fun readOnlyDetectionMatchesTheCommandsTheToolsBuild() {
        assertTrue(readOnlyArguments("status --porcelain=v1 -b"))
        assertTrue(readOnlyArguments("diff --staged"))
        assertTrue(readOnlyArguments("log --oneline --decorate -n 20"))
        assertTrue(readOnlyArguments("show --stat --patch 'HEAD'"))
        assertTrue(readOnlyArguments("blame -- 'src/main.kt'"))
        assertFalse(readOnlyArguments("commit -m 'x'"))
        assertFalse(readOnlyArguments("push"))
        assertFalse(readOnlyArguments("init"))
        assertFalse(readOnlyArguments(""))
    }

    @Test
    fun executeIgnoresNamesFromOtherGroups() = runBlocking {
        val ctx = blankCtx()
        assertNull(GitTools.execute(ctx, "read_file", JSONObject()))
        assertNull(GitTools.execute(ctx, "run_command", JSONObject()))
        assertNull(GitTools.execute(ctx, "git_push_force", JSONObject()))
        assertNull(GitTools.execute(ctx, "", JSONObject()))
    }

    @Test
    fun executeRefusesDangerousRequestsBeforeItLooksForAShell() = runBlocking {
        val ctx = blankCtx()
        val hard = assertNotNull(GitTools.execute(ctx, "git_reset", JSONObject().put("mode", "hard")))
        assertFalse(hard.success)
        assertTrue(hard.summary.contains("--hard"), hard.summary)
        val injected = assertNotNull(GitTools.execute(ctx, "git_commit", JSONObject().put("message", "ship it; rm -rf /")))
        assertFalse(injected.success)
        assertTrue(injected.summary.contains("Blocked"), injected.summary)
        val escaping = assertNotNull(GitTools.execute(ctx, "git_add", JSONObject().put("paths", "../../etc/passwd")))
        assertFalse(escaping.success)
        val dashed = assertNotNull(GitTools.execute(ctx, "git_push", JSONObject().put("remote", "-f")))
        assertFalse(dashed.success)
        val noMessage = assertNotNull(GitTools.execute(ctx, "git_commit", JSONObject()))
        assertFalse(noMessage.success)
        val noPatch = assertNotNull(GitTools.execute(ctx, "git_apply_patch", JSONObject()))
        assertFalse(noPatch.success)
        val badAction = assertNotNull(GitTools.execute(ctx, "git_stash", JSONObject().put("action", "burn")))
        assertFalse(badAction.success)
    }

    @Test
    fun executeReportsAMissingShellInsteadOfThrowing() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "lucent-git-tools-test-${System.nanoTime()}")
        dir.mkdirs()
        val savedShell = HarnessRuntime.shell
        val savedAndroid = HarnessRuntime.android
        val savedConfig = HarnessRuntime.config()
        HarnessRuntime.shell = null
        HarnessRuntime.android = false
        HarnessRuntime.install(HarnessConfig(workspace = dir.path))
        try {
            val ctx = blankCtx(dir)
            val status = assertNotNull(GitTools.execute(ctx, "git_status", JSONObject()))
            assertFalse(status.success)
            assertTrue(status.summary.contains("needs a shell"), status.summary)
            val rebase = assertNotNull(
                GitTools.execute(ctx, "git_rebase", JSONObject().put("action", "abort"))
            )
            assertFalse(rebase.success)
            assertTrue(rebase.summary.contains("needs a shell"), rebase.summary)
        } finally {
            HarnessRuntime.shell = savedShell
            HarnessRuntime.android = savedAndroid
            HarnessRuntime.install(savedConfig)
            dir.deleteRecursively()
        }
    }

    private fun blankCtx(dir: File = File(System.getProperty("java.io.tmpdir"))): HarnessCtx {
        val context = allocate(Context::class.java) as? Context
        val db = allocate(AppDatabase::class.java) as? AppDatabase
        if (context != null && db != null) {
            return HarnessCtx(context, db, HarnessConfig(workspace = dir.path), emptySet(), false, dir)
        }
        val ctx = allocate(HarnessCtx::class.java) as HarnessCtx
        fill(ctx, "config", HarnessConfig(workspace = dir.path))
        fill(ctx, "capabilities", emptySet<String>())
        fill(ctx, "android", false)
        fill(ctx, "workspace", dir)
        return ctx
    }

    private fun allocate(type: Class<*>): Any? = unsafeAllocate(type) ?: serializationAllocate(type)

    private fun unsafeAllocate(type: Class<*>): Any? = try {
        val unsafe = Class.forName("sun.misc.Unsafe")
        val field = unsafe.getDeclaredField("theUnsafe")
        field.isAccessible = true
        unsafe.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), type)
    } catch (t: Throwable) {
        null
    }

    private fun serializationAllocate(type: Class<*>): Any? = try {
        val factoryType = Class.forName("sun.reflect.ReflectionFactory")
        val factory = factoryType.getMethod("getReflectionFactory").invoke(null)
        val marker = Any::class.java.getDeclaredConstructor()
        val creator = factoryType.getMethod(
            "newConstructorForSerialization",
            Class::class.java,
            java.lang.reflect.Constructor::class.java
        ).invoke(factory, type, marker) as java.lang.reflect.Constructor<*>
        creator.isAccessible = true
        creator.newInstance()
    } catch (t: Throwable) {
        null
    }

    private fun fill(target: HarnessCtx, name: String, value: Any) {
        try {
            val field = HarnessCtx::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.set(target, value)
        } catch (_: Throwable) {
        }
    }
}
