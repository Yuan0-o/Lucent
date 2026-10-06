import sys

file_path = "shared/src/commonMain/kotlin/com/lucent/app/harness/PdfTools.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("import java.util.Locale\n", "")
content = content.replace(".lowercase(Locale.US)", ".lowercase()")
content = content.replace("String.format(Locale.US, ", "String.format(")

with open(file_path, "w") as f:
    f.write(content)

