import re

def migrate_github(content):
    content = content.replace("import org.json.JSONArray\n", "")
    content = content.replace("import org.json.JSONObject\n", "import kotlinx.serialization.json.*\n")
    
    content = content.replace("execute(ctx: HarnessCtx, name: String, args: JSONObject)", "execute(ctx: HarnessCtx, name: String, args: JsonObject)")
    content = content.replace("body: JSONObject? = null", "body: JsonObject? = null")
    
    # Replace parameter types
    content = re.sub(r'args:\s*JSONObject', r'args: JsonObject', content)
    content = re.sub(r':\s*JSONArray', r': JsonArray', content)
    
    # Replace opt methods with defaults
    # .optString("key", "default")
    content = re.sub(r'(\w+(?:\??))\.optString\("([^"]+)",\s*("[^"]*")\)', r'(\1["\2"]?.jsonPrimitive?.content ?: \3)', content)
    # .optString("key")
    content = re.sub(r'(\w+(?:\??))\.optString\("([^"]+)"\)', r'(\1["\2"]?.jsonPrimitive?.content ?: "")', content)
    
    # .optBoolean("key", default)
    content = re.sub(r'(\w+(?:\??))\.optBoolean\("([^"]+)",\s*(true|false)\)', r'(\1["\2"]?.jsonPrimitive?.booleanOrNull ?: \3)', content)
    # .optBoolean("key")
    content = re.sub(r'(\w+(?:\??))\.optBoolean\("([^"]+)"\)', r'(\1["\2"]?.jsonPrimitive?.booleanOrNull ?: false)', content)
    
    # .optInt("key", default)
    content = re.sub(r'(\w+(?:\??))\.optInt\("([^"]+)",\s*(-?\d+)\)', r'(\1["\2"]?.jsonPrimitive?.intOrNull ?: \3)', content)
    # .optInt("key")
    content = re.sub(r'(\w+(?:\??))\.optInt\("([^"]+)"\)', r'(\1["\2"]?.jsonPrimitive?.intOrNull ?: 0)', content)
    
    # .optLong("key", default)
    content = re.sub(r'(\w+(?:\??))\.optLong\("([^"]+)",\s*(-?\d+L?)\)', r'(\1["\2"]?.jsonPrimitive?.longOrNull ?: \3)', content)
    # .optLong("key")
    content = re.sub(r'(\w+(?:\??))\.optLong\("([^"]+)"\)', r'(\1["\2"]?.jsonPrimitive?.longOrNull ?: 0L)', content)
    
    # .optJSONObject("key")
    content = re.sub(r'(\w+(?:\??))\.optJSONObject\("([^"]+)"\)', r'\1["\2"]?.jsonObject', content)
    
    # .optJSONArray("key")
    content = re.sub(r'(\w+(?:\??))\.optJSONArray\("([^"]+)"\)', r'\1["\2"]?.jsonArray', content)
    
    # .has("key")
    content = re.sub(r'(\w+(?:\??))\.has\("([^"]+)"\)', r'\1.containsKey("\2")', content)
    
    # .length() -> .size
    content = re.sub(r'(\w+(?:\??))\.length\(\)', r'\1.size', content)
    
    return content

with open("shared/src/commonMain/kotlin/com/lucent/app/harness/GitHubTools.kt", "r") as f:
    text = f.read()

text = migrate_github(text)

with open("shared/src/commonMain/kotlin/com/lucent/app/harness/GitHubTools.kt", "w") as f:
    f.write(text)

