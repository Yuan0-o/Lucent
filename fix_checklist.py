import sys

file_path = "shared/src/commonMain/kotlin/com/lucent/app/data/Checklist.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("import java.util.UUID", "import kotlin.uuid.Uuid\nimport kotlin.uuid.ExperimentalUuidApi")
content = content.replace("UUID.randomUUID()", "Uuid.random()")
content = content.replace("UUID.fromString", "Uuid.parse")
# Also the ExperimentalUuidApi opt-in
content = content.replace("object Checklist {", "@OptIn(ExperimentalUuidApi::class)\nobject Checklist {")

with open(file_path, "w") as f:
    f.write(content)

