import re

with open("shared/src/commonMain/kotlin/com/lucent/app/harness/GitHubTools.kt", "r") as f:
    content = f.read()

# For array.optJSONObject(i)
content = re.sub(r'array\.optJSONObject\(i\)', r'(array[i] as? JsonObject)', content)

# For any .optJSONObject("key")
content = re.sub(r'\.optJSONObject\("([^"]+)"\)', r'["\1"]?.jsonObject', content)

with open("shared/src/commonMain/kotlin/com/lucent/app/harness/GitHubTools.kt", "w") as f:
    f.write(content)
