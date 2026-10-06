import sys

file_path = "shared/src/commonMain/kotlin/com/lucent/app/harness/ooxml/Pptx.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("import kotlinx.datetime.Clock\n", "import kotlinx.datetime.Clock\nimport kotlinx.datetime.TimeZone\nimport kotlinx.datetime.toLocalDateTime\n")

old_timestamp = """private fun pptxTimestamp(): String {
    val now = kotlinx.datetime.Clock.System.now().toString()
    val dot = now.indexOf('.')
    return if (dot > 0) now.substring(0, dot) + "Z" else now
}"""

new_timestamp = """private fun pptxTimestamp(): String {
    val now = kotlinx.datetime.Clock.System.now()
    val local = now.toLocalDateTime(TimeZone.UTC)
    val string = local.toString()
    val dot = string.indexOf('.')
    return (if (dot > 0) string.substring(0, dot) else string) + "Z"
}"""

content = content.replace(old_timestamp, new_timestamp)

with open(file_path, "w") as f:
    f.write(content)

