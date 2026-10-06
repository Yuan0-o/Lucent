package com.lucent.app.harness

import kotlin.io.encoding.Base64
import com.lucent.app.network.ToolExecResult
import com.lucent.app.network.ToolImage
import kotlinx.serialization.json.*

object DeviceTools : HarnessGroupTools {

    override val group = HarnessGroup.DEVICE

    private fun tool(
        name: String,
        description: String,
        params: List<com.lucent.app.network.ToolParam> = emptyList()
    ) = HarnessTool(
        name = name,
        group = group,
        permission = HarnessPermission.DEVICE,
        description = description,
        params = params,
        androidOnly = true
    )

    override val tools: List<HarnessTool> = listOf(
        tool("device_info", "Describe this phone: model, Android version, battery, storage, network and screen."),
        tool("read_screen", "Read what is on screen now as a list of elements with their text and positions. Use it " +
            "before tapping anything, and read it again after every tap."),
        tool("tap", "Tap the screen, either by matching text or by coordinates. Prefer text: positions move when the " +
            "screen scrolls.", listOf(
            HarnessSchema.text("text", "Visible text to tap", false),
            HarnessSchema.number("x", "Horizontal position", false),
            HarnessSchema.number("y", "Vertical position", false)
        )),
        tool("input_text", "Type text into the field that currently has focus. Tap the field first.", listOf(
            HarnessSchema.text("text", "Text to type")
        )),
        tool("swipe", "Swipe or scroll from one point to another.", listOf(
            HarnessSchema.number("x1", "Start x"),
            HarnessSchema.number("y1", "Start y"),
            HarnessSchema.number("x2", "End x"),
            HarnessSchema.number("y2", "End y"),
            HarnessSchema.number("millis", "Duration in milliseconds", false)
        )),
        tool("press_key", "Press a system key: back, home, recents, notifications or lock.", listOf(
            HarnessSchema.text("key", "back, home, recents, notifications or lock")
        )),
        tool("screenshot", "Take a screenshot and hand it back as an image you can look at."),
        tool("list_apps", "List installed apps, user apps first. Pass a search term to narrow it down.", listOf(
            HarnessSchema.text("query", "Part of a name or package", false)
        )),
        tool("launch_app", "Open an app by its package name.", listOf(
            HarnessSchema.text("package_name", "Package name such as com.example.app")
        )),
        tool("stop_app", "Force stop a user app by package name.", listOf(
            HarnessSchema.text("package_name", "Package name")
        )),
        tool("notifications", "Read the current notifications: app, title and text."),
        tool("clipboard", "Read or write the clipboard. action is get or set.", listOf(
            HarnessSchema.text("action", "get or set"),
            HarnessSchema.text("text", "Text to put on the clipboard", false)
        )),
        tool("share_text", "Hand text to another app through the share sheet.", listOf(
            HarnessSchema.text("text", "Text to share"),
            HarnessSchema.text("subject", "Subject line", false)
        )),
        tool("open_link_on_device", "Open a link in the phone's browser.", listOf(
            HarnessSchema.text("url", "Link to open")
        )),
        tool("notify_user", "Post a notification on the phone, for a long job that has finished.", listOf(
            HarnessSchema.text("title", "Title"),
            HarnessSchema.text("text", "Body text")
        )),
        tool("vibrate", "Buzz the phone once, to draw attention to a finished job.", listOf(
            HarnessSchema.number("millis", "How long to buzz", false)
        )),
        tool("torch", "Switch the flashlight on or off.", listOf(
            HarnessSchema.flag("on", "True for on, false for off")
        )),
        tool("location", "Report the current location. Requires the location permission and a fresh fix may take a " +
            "few seconds."),
        tool("sensors", "List the sensors on this phone and their latest values."),
        tool("export_file", "Copy a file from the workspace into the phone's Downloads folder so the user can open it " +
            "in another app.", listOf(
            HarnessSchema.text("path", "File to export")
        ))
    )

    override suspend fun execute(ctx: HarnessCtx, name: String, args: JsonObject): ToolExecResult? {
        val host = HarnessRuntime.host ?: return ToolExecResult("Device control is not available here.", success = false)
        if (!ctx.android) return ToolExecResult("Device control only works on Android.", success = false)
        return when (name) {
            "device_info" -> text(host.deviceInfo(), "No device information came back.")
            "read_screen" -> text(host.screenText(), "The screen could not be read. The accessibility service may be off.")
            "tap" -> tap(host, args)
            "input_text" -> flag(host.typeText((args["text"]?.jsonPrimitive?.content ?: "")), "The text could not be typed.")
            "swipe" -> flag(
                host.swipe(
                    (args["x1"]?.jsonPrimitive?.intOrNull ?: 0), (args["y1"]?.jsonPrimitive?.intOrNull ?: 0),
                    (args["x2"]?.jsonPrimitive?.intOrNull ?: 0), (args["y2"]?.jsonPrimitive?.intOrNull ?: 0),
                    (args["millis"]?.jsonPrimitive?.intOrNull ?: 300)
                ),
                "The swipe did not go through."
            )
            "press_key" -> flag(host.pressKey((args["key"]?.jsonPrimitive?.content ?: "back")), "The key press did not go through.")
            "screenshot" -> screenshot(host)
            "list_apps" -> text(host.installedApps((args["query"]?.jsonPrimitive?.content ?: "")), "No apps came back.")
            "launch_app" -> flag(host.launchApp((args["package_name"]?.jsonPrimitive?.content ?: "")), "That app could not be opened.")
            "stop_app" -> flag(host.stopApp((args["package_name"]?.jsonPrimitive?.content ?: "")), "That app could not be stopped.")
            "notifications" -> text(host.notifications(), "No notifications, or notification access is off.")
            "clipboard" -> clipboard(host, args)
            "share_text" -> flag(
                host.shareText((args["text"]?.jsonPrimitive?.content ?: ""), (args["subject"]?.jsonPrimitive?.content ?: "")),
                "Sharing did not open."
            )
            "open_link_on_device" -> flag(host.openUrl((args["url"]?.jsonPrimitive?.content ?: "")), "The link could not be opened.")
            "notify_user" -> flag(
                host.notify((args["title"]?.jsonPrimitive?.content ?: "Lucent"), (args["text"]?.jsonPrimitive?.content ?: "")),
                "The notification could not be posted."
            )
            "vibrate" -> flag(host.vibrate((args["millis"]?.jsonPrimitive?.intOrNull ?: 250).toLong()), "The phone would not buzz.")
            "torch" -> flag(host.setTorch((args["on"]?.jsonPrimitive?.booleanOrNull ?: true)), "The flashlight is not available.")
            "location" -> text(host.location(), "No location fix yet. Try again in a moment.")
            "sensors" -> text(host.sensors(), "No sensor readings came back.")
            "export_file" -> export(ctx, host, args)
            else -> null
        }
    }

    private suspend fun tap(host: HarnessHost, args: JsonObject): ToolExecResult {
        val label = (args["text"]?.jsonPrimitive?.content ?: "")
        if (label.isNotBlank()) {
            val lines = host.screenText().lines()
            val hit = lines.firstOrNull { it.contains(label, ignoreCase = true) && it.contains("center=(") }
                ?: return ToolExecResult("Nothing on screen says \"$label\". Read the screen again.", success = false)
            val coords = Regex("center=\\((\\d+),(\\d+)\\)").find(hit)
                ?: return ToolExecResult("Found \"$label\" but not where to tap it.", success = false)
            val x = coords.groupValues[1].toIntOrNull() ?: 0
            val y = coords.groupValues[2].toIntOrNull() ?: 0
            return flag(host.tap(x, y), "The tap did not go through at ($x,$y).")
        }
        if (!args.containsKey("x") || !args.containsKey("y")) {
            return ToolExecResult("Give me either text to tap or both x and y.", success = false)
        }
        return flag(host.tap((args["x"]?.jsonPrimitive?.intOrNull ?: 0), (args["y"]?.jsonPrimitive?.intOrNull ?: 0)), "The tap did not go through.")
    }

    private suspend fun screenshot(host: HarnessHost): ToolExecResult {
        val bytes = host.screenshot() ?: return ToolExecResult("No screenshot came back.", success = false)
        val encoded = Base64.Default.encode(bytes)
        return ToolExecResult(
            "Screenshot taken (${bytes.size / 1024} KiB).",
            images = listOf(ToolImage("image/png", encoded, "screenshot.png"))
        )
    }

    private fun clipboard(host: HarnessHost, args: JsonObject): ToolExecResult {
        val action = (args["action"]?.jsonPrimitive?.content ?: "get").lowercase()
        return if (action == "set") {
            flag(host.writeClipboard((args["text"]?.jsonPrimitive?.content ?: "")), "The clipboard could not be written.")
        } else {
            val value = host.readClipboard()
            if (value.isBlank()) ToolExecResult("The clipboard is empty, or Lucent is in the background.")
            else ToolExecResult(value.take(4000))
        }
    }

    private suspend fun ask(host: HarnessHost, args: JsonObject): ToolExecResult {
        val question = (args["question"]?.jsonPrimitive?.content ?: "").trim()
        if (question.isEmpty()) return ToolExecResult("What should I ask?", success = false)
        val options = mutableListOf<String>()
        val array = args["options"]?.jsonArray
        if (array != null) {
            for (i in 0 until array.size) {
                val value = array[i].jsonPrimitive.content
                if (value.isNotBlank()) options.add(value)
            }
        }
        val answer = host.askUser(question, options.take(3))
        return if (answer.isBlank()) ToolExecResult("The user did not answer.", success = false)
        else ToolExecResult("The user answered: $answer")
    }

    private fun export(ctx: HarnessCtx, host: HarnessHost, args: JsonObject): ToolExecResult {
        val file = try {
            Workspace.forReadFile(ctx, (args["path"]?.jsonPrimitive?.content ?: ""))
        } catch (e: HarnessError) {
            return ToolExecResult(e.message ?: "That path cannot be read", success = false)
        }
        return if (host.exportFile(file.toString())) ToolExecResult("Exported ${Workspace.display(ctx, file)} to Downloads.")
        else ToolExecResult("The file could not be exported.", success = false)
    }

    private fun text(value: String, empty: String): ToolExecResult =
        if (value.isBlank()) ToolExecResult(empty, success = false) else ToolExecResult(value)

    private fun flag(ok: Boolean, failure: String): ToolExecResult =
        if (ok) ToolExecResult("Done.") else ToolExecResult(failure, success = false)
}
