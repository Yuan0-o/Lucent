import sys

file_path = "shared/src/commonMain/kotlin/com/lucent/app/data/AppLock.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("import java.util.Base64", "import kotlin.io.encoding.Base64\nimport kotlin.io.encoding.ExperimentalEncodingApi")
content = content.replace("Base64.getEncoder().withoutPadding().encodeToString(bytes)", "Base64.encode(bytes)")
content = content.replace("Base64.getDecoder().decode(s)", "Base64.decode(s)")
# AppLock uses `private fun b64(bytes: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(bytes)`
# Let's ensure the class/object can use ExperimentalEncodingApi
content = content.replace("object AppLock {", "@OptIn(ExperimentalEncodingApi::class)\nobject AppLock {")

with open(file_path, "w") as f:
    f.write(content)

