package com.lucent.app.harness

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class LucentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    private fun rootNode(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (t: Throwable) {
        null
    }

    private fun collect(node: AccessibilityNodeInfo?, depth: Int, index: IntArray, out: StringBuilder) {
        if (node == null || depth > 40 || out.length > 60000) return
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        val label = when {
            text.isNotEmpty() && description.isNotEmpty() && text != description -> "$text ($description)"
            text.isNotEmpty() -> text
            else -> description
        }
        if (label.isNotEmpty()) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            index[0]++
            out.append('[').append(index[0]).append("] \"").append(label.replace('\n', ' ')).append('"')
            if (node.isClickable) out.append(" clickable")
            if (node.isEditable) out.append(" editable")
            out.append(" center=(").append(bounds.centerX()).append(',').append(bounds.centerY()).append(')')
            out.append(" bounds=").append(bounds.left).append(',').append(bounds.top).append(',')
                .append(bounds.right).append(',').append(bounds.bottom)
            out.append('\n')
        }
        for (i in 0 until node.childCount) {
            collect(node.getChild(i), depth + 1, index, out)
        }
    }

    private fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = rootNode() ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 4000) {
            val node = queue.removeFirst()
            visited++
            if (predicate(node)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    companion object {

        @Volatile private var instance: LucentAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        fun dump(): String {
            val service = instance ?: return ""
            val out = StringBuilder()
            service.collect(service.rootNode(), 0, IntArray(1), out)
            return out.toString().trimEnd()
        }

        fun findCenter(label: String): Pair<Int, Int>? {
            val service = instance ?: return null
            val node = service.findNode { candidate ->
                val text = candidate.text?.toString().orEmpty()
                val description = candidate.contentDescription?.toString().orEmpty()
                text.contains(label, ignoreCase = true) || description.contains(label, ignoreCase = true)
            } ?: return null
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            return bounds.centerX() to bounds.centerY()
        }

        fun tap(x: Int, y: Int): Boolean {
            val service = instance ?: return false
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.dispatchGesture(gesture, null, null)
        }

        fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, millis: Int): Boolean {
            val service = instance ?: return false
            val path = Path().apply {
                moveTo(x1.toFloat(), y1.toFloat())
                lineTo(x2.toFloat(), y2.toFloat())
            }
            val duration = millis.coerceIn(60, 4000).toLong()
            val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.dispatchGesture(gesture, null, null)
        }

        fun type(text: String): Boolean {
            val service = instance ?: return false
            val node = service.findNode { it.isEditable && it.isFocused }
                ?: service.findNode { it.isEditable }
                ?: return false
            val arguments = android.os.Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                (node.text?.toString().orEmpty()) + text
            )
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        fun key(name: String): Boolean {
            val service = instance ?: return false
            val action = when (name.lowercase()) {
                "back" -> AccessibilityService.GLOBAL_ACTION_BACK
                "home" -> AccessibilityService.GLOBAL_ACTION_HOME
                "recents", "recent" -> AccessibilityService.GLOBAL_ACTION_RECENTS
                "notifications", "notification" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                "quicksettings", "settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                "lock", "lockscreen" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                } else -1
                else -> -1
            }
            if (action < 0) return false
            return service.performGlobalAction(action)
        }

        suspend fun screenshot(): ByteArray? {
            val service = instance ?: return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            return suspendCancellableCoroutine { continuation ->
                try {
                    service.takeScreenshot(
                        android.view.Display.DEFAULT_DISPLAY,
                        service.mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(result: ScreenshotResult) {
                                val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                val out = ByteArrayOutputStream()
                                val software = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                result.hardwareBuffer.close()
                                val ok = software != null && software.compress(Bitmap.CompressFormat.PNG, 90, out)
                                software?.recycle()
                                continuation.resume(if (ok) out.toByteArray() else null)
                            }

                            override fun onFailure(errorCode: Int) {
                                continuation.resume(null)
                            }
                        }
                    )
                } catch (t: Throwable) {
                    continuation.resume(null)
                }
            }
        }
    }
}
