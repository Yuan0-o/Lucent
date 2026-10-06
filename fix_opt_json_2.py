import re

with open("shared/src/commonMain/kotlin/com/lucent/app/harness/GitHubTools.kt", "r") as f:
    content = f.read()

# Replace ANY .optString("key", default)
content = re.sub(r'\.optString\("([^"]+)",\s*([^)]+)\)', r'["\1"]?.jsonPrimitive?.content ?: \2', content)

# Replace ANY .optString("key")
content = re.sub(r'\.optString\("([^"]+)"\)', r'["\1"]?.jsonPrimitive?.content ?: ""', content)

# Replace ANY .optInt("key", default)
content = re.sub(r'\.optInt\("([^"]+)",\s*([^)]+)\)', r'["\1"]?.jsonPrimitive?.intOrNull ?: \2', content)

# Replace ANY .optInt("key")
content = re.sub(r'\.optInt\("([^"]+)"\)', r'["\1"]?.jsonPrimitive?.intOrNull ?: 0', content)

# Replace ANY .optBoolean("key", default)
content = re.sub(r'\.optBoolean\("([^"]+)",\s*([^)]+)\)', r'["\1"]?.jsonPrimitive?.booleanOrNull ?: \2', content)

# Replace ANY .optBoolean("key")
content = re.sub(r'\.optBoolean\("([^"]+)"\)', r'["\1"]?.jsonPrimitive?.booleanOrNull ?: false', content)

# Replace ANY .optLong("key", default)
content = re.sub(r'\.optLong\("([^"]+)",\s*([^)]+)\)', r'["\1"]?.jsonPrimitive?.longOrNull ?: \2', content)

# Replace ANY .optLong("key")
content = re.sub(r'\.optLong\("([^"]+)"\)', r'["\1"]?.jsonPrimitive?.longOrNull ?: 0L', content)

# Replace ANY .optJSONArray("key")
content = re.sub(r'\.optJSONArray\("([^"]+)"\)', r'["\1"]?.jsonArray', content)

with open("shared/src/commonMain/kotlin/com/lucent/app/harness/GitHubTools.kt", "w") as f:
    f.write(content)
