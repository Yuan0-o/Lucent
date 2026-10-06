import sys

file_path = "shared/src/commonMain/kotlin/com/lucent/app/harness/ooxml/Pptx.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("import java.util.Locale\n", "")
content = content.replace("import java.util.TimeZone\n", "import kotlinx.datetime.TimeZone\n")
content = content.replace(".lowercase(Locale.US)", ".lowercase()")
content = content.replace(".uppercase(Locale.US)", ".uppercase()")
content = content.replace("String.format(Locale.US, ", "String.format(")
content = content.replace("SimpleDateFormat(\"yyyy-MM-dd'T'HH:mm:ss'Z'\", Locale.US)", "SimpleDateFormat(\"yyyy-MM-dd'T'HH:mm:ss'Z'\")")

# What about TimeZone?
content = content.replace("TimeZone.getTimeZone(\"UTC\")", "TimeZone.of(\"UTC\")")

with open(file_path, "w") as f:
    f.write(content)

