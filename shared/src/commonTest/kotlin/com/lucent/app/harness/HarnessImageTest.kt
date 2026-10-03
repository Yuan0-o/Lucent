package com.lucent.app.harness

import android.content.Context
import com.lucent.app.network.ToolExecResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private class ImageHost(private val root: File) : HarnessHost {
    override val android: Boolean = false
    override fun defaultWorkspace(): File = File(root, "workspace").apply { mkdirs() }
    override fun filesDir(): File = File(root, "files").apply { mkdirs() }
    override fun cacheDir(): File = File(root, "cache").apply { mkdirs() }
}

private val PNG_ONE_PIXEL = byteArrayOf(
    0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
    0x0D, 0x0A, 0x1A, 0x0A,
    0x00, 0x00, 0x00, 0x0D, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte()
)

class HarnessImageTest {

    private fun withImageSandbox(block: (File) -> Unit) {
        val root = java.nio.file.Files.createTempDirectory("lucent-image").toFile()
        val previousHost = HarnessRuntime.host
        val previousConfig = HarnessRuntime.config()
        HarnessRuntime.host = ImageHost(root)
        HarnessRuntime.android = false
        HarnessRuntime.update(HarnessConfig(enabled = true))
        try {
            HarnessRuntime.workspace().mkdirs()
            block(root)
        } finally {
            HarnessRuntime.host = previousHost
            HarnessRuntime.update(previousConfig)
            root.deleteRecursively()
        }
    }

    private fun contextFactory(root: File): HarnessCtx {
        val context = harnessTestContext()
        return HarnessCtx(context, null, HarnessRuntime.config(), emptySet(), false, HarnessRuntime.workspace())
    }

    private fun read(ctx: HarnessCtx, path: String, maxBytes: Long? = null): ToolExecResult {
        val args = JSONObject().put("path", path)
        if (maxBytes != null) args.put("max_bytes", maxBytes)
        return runBlocking { assertNotNull(FileTools.execute(ctx, "read_image", args)) }
    }

    @Test
    fun readImageIsDeclaredAsAReadTool() {
        val tool = HarnessGate.allTools().first { it.name == "read_image" }
        assertEquals(HarnessGroup.FILES, tool.group)
        assertEquals(HarnessPermission.READ, tool.permission)
        assertTrue(tool.readOnly)
        assertFalse(tool.escalatable)
        assertNotNull(tool.params.firstOrNull { it.name == "path" })
    }

    @Test
    fun imageBytesAreRecognisedByTheirMagicNumbers() {
        assertEquals("image/png", FileTools.imageMime(PNG_ONE_PIXEL))
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertEquals("image/jpeg", FileTools.imageMime(jpeg))
        assertEquals("image/gif", FileTools.imageMime("GIF89a".toByteArray()))
        assertEquals(
            "image/webp",
            FileTools.imageMime("RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray())
        )
        assertNull(FileTools.imageMime("just some words".toByteArray()))
        assertNull(FileTools.imageMime("PNG".toByteArray()))
    }

    @Test
    fun aRealImageComesBackAsAnAttachment() = withImageSandbox { root ->
        val ctx = contextFactory(root)
        File(HarnessRuntime.workspace(), "pixel.png").writeBytes(PNG_ONE_PIXEL)
        val result = read(ctx, "pixel.png")
        assertTrue(result.success, result.summary)
        assertEquals(1, result.images.size)
        assertEquals("image/png", result.images.first().mime)
        assertEquals("pixel.png", result.images.first().name)
        assertTrue(result.images.first().data.isNotEmpty())
    }

    @Test
    fun aTextFileIsRefusedEvenWhenItIsCalledAnImage() = withImageSandbox { root ->
        val ctx = contextFactory(root)
        val notAnImage = File(HarnessRuntime.workspace(), "notes.png")
        notAnImage.writeText("this is not a picture")
        val result = read(ctx, "notes.png")
        assertFalse(result.success)
        assertTrue(result.images.isEmpty())
        assertTrue(result.summary.contains("notes.png"), result.summary)
    }

    @Test
    fun aNonImageExtensionIsRefused() = withImageSandbox { root ->
        val ctx = contextFactory(root)
        File(HarnessRuntime.workspace(), "report.txt").writeText("plain text")
        val result = read(ctx, "report.txt")
        assertFalse(result.success)
        assertTrue(result.summary.contains("not an image"), result.summary)
        assertTrue(result.summary.contains("png"), result.summary)
    }

    @Test
    fun aRenamedImageIsRefusedWhenTheBytesDisagree() = withImageSandbox { root ->
        val ctx = contextFactory(root)
        File(HarnessRuntime.workspace(), "picture.png").writeBytes("GIF89a".toByteArray())
        val result = read(ctx, "picture.png")
        assertFalse(result.success)
        assertTrue(result.summary.contains("image/gif"), result.summary)
    }

    @Test
    fun directoriesAndMissingFilesAreRefused() = withImageSandbox { root ->
        val ctx = contextFactory(root)
        File(HarnessRuntime.workspace(), "gallery").mkdirs()
        val directory = read(ctx, "gallery")
        assertFalse(directory.success)
        assertTrue(directory.summary.contains("directory"), directory.summary)
        val missing = read(ctx, "missing.png")
        assertFalse(missing.success)
        assertTrue(missing.summary.contains("does not exist"), missing.summary)
    }

    @Test
    fun oversizedImagesAreRefused() = withImageSandbox { root ->
        val ctx = contextFactory(root)
        File(HarnessRuntime.workspace(), "big.png").writeBytes(PNG_ONE_PIXEL + ByteArray(4096))
        val result = read(ctx, "big.png", maxBytes = 1024L)
        assertFalse(result.success)
        assertTrue(result.summary.contains("limit"), result.summary)
    }

    @Test
    fun imagesOutsideTheWorkspaceFollowTheSandboxPolicy() = withImageSandbox { root ->
        val ctx = contextFactory(root)
        val outside = File(root, "outside/pixel.png")
        outside.parentFile?.mkdirs()
        outside.writeBytes(PNG_ONE_PIXEL)
        val allowed = read(ctx, outside.path)
        assertTrue(allowed.success, allowed.summary)
        HarnessRuntime.update(HarnessConfig(enabled = true, approvals = mapOf("sensitive" to "deny")))
        val refusal = assertFailsWith<HarnessError> {
            runBlocking {
                FileTools.execute(
                    ctx.copy(config = HarnessRuntime.config()),
                    "read_image",
                    JSONObject().put("path", outside.path)
                )
            }
        }
        assertTrue(refusal.blocked, refusal.message ?: "")
        assertTrue(refusal.message.orEmpty().contains("outside"), refusal.message ?: "")
    }
}
