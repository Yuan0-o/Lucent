import sys

file_path = "shared/src/commonMain/kotlin/com/lucent/app/harness/ooxml/Pptx.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("import java.text.SimpleDateFormat\n", "")
content = content.replace("import java.util.Date\n", "")

old_timestamp = """private fun pptxTimestamp(): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    format.timeZone = TimeZone.of("UTC")
    return format.format(Date())
}"""

new_timestamp = """private fun pptxTimestamp(): String {
    val now = kotlinx.datetime.Clock.System.now().toString()
    val dot = now.indexOf('.')
    return if (dot > 0) now.substring(0, dot) + "Z" else now
}"""

content = content.replace(old_timestamp, new_timestamp)

with open(file_path, "w") as f:
    f.write(content)

