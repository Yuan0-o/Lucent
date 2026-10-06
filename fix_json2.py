import re
import os

def process_file(path):
    with open(path, 'r') as f:
        content = f.read()

    # Imports
    content = content.replace('import org.json.JSONArray\n', '')
    content = content.replace('import org.json.JSONObject\n', '')
    
    # New imports if missed
    if 'import kotlinx.serialization.json.*' not in content:
        content = content.replace('import org.w3c.dom.', 'import kotlinx.serialization.json.*\nimport org.w3c.dom.')
        
    # Types
    content = re.sub(r'\bJSONObject\b', 'JsonObject', content)
    content = re.sub(r'\bJSONArray\b', 'JsonArray', content)
    
    # JsonObject(text) -> Json.parseToJsonElement(text).jsonObject
    content = re.sub(r'\bJsonObject\(([^)]+)\)', r'Json.parseToJsonElement(\1).jsonObject', content)
    # JsonArray(text) -> Json.parseToJsonElement(text).jsonArray
    content = re.sub(r'\bJsonArray\(([^)]+)\)', r'Json.parseToJsonElement(\1).jsonArray', content)
    
    # optString(key, default)
    content = re.sub(r'\.optString\(([^,]+),\s*([^)]+)\)', r'[\1]?.jsonPrimitive?.content ?: \2', content)
    # optString(key)
    content = re.sub(r'\.optString\(([^)]+)\)', r'[\1]?.jsonPrimitive?.content ?: ""', content)
    
    # optBoolean(key, default)
    content = re.sub(r'\.optBoolean\(([^,]+),\s*([^)]+)\)', r'[\1]?.jsonPrimitive?.booleanOrNull ?: \2', content)
    # optBoolean(key)
    content = re.sub(r'\.optBoolean\(([^)]+)\)', r'[\1]?.jsonPrimitive?.booleanOrNull ?: false', content)
    
    # optDouble(key, default)
    content = re.sub(r'\.optDouble\(([^,]+),\s*([^)]+)\)', r'[\1]?.jsonPrimitive?.doubleOrNull ?: \2', content)
    # optDouble(key)
    content = re.sub(r'\.optDouble\(([^)]+)\)', r'[\1]?.jsonPrimitive?.doubleOrNull ?: 0.0', content)
    
    # optInt(key, default)
    content = re.sub(r'\.optInt\(([^,]+),\s*([^)]+)\)', r'[\1]?.jsonPrimitive?.intOrNull ?: \2', content)
    # optInt(key)
    content = re.sub(r'\.optInt\(([^)]+)\)', r'[\1]?.jsonPrimitive?.intOrNull ?: 0', content)
    
    # optJSONObject(key) -> [key]?.jsonObject
    content = re.sub(r'\.optJSONObject\(([^)]+)\)', r'[\1]?.jsonObject', content)
    
    # optJSONArray(key) -> [key]?.jsonArray
    content = re.sub(r'\.optJSONArray\(([^)]+)\)', r'[\1]?.jsonArray', content)
    
    # opt(key) -> [key]
    content = re.sub(r'\.opt\(([^)]+)\)', r'[\1]', content)
    
    # has(key) -> containsKey(key)
    content = re.sub(r'\.has\(([^)]+)\)', r'.containsKey(\1)', content)
    
    # .length() -> .size
    content = re.sub(r'\.length\(\)', r'.size', content)
    
    # Array [i]?.jsonPrimitive?.content ?: ""
    # wait, earlier we did array[i] via replacing `.optString(i, "")` -> `[i]?.jsonPrimitive?.content ?: ""` which is technically correct because `[\1]` handles strings and integers similarly. But wait, in Kotlin jsonArray[i] works and returns JsonElement, so `jsonArray[i].jsonPrimitive.content` works. But wait, `jsonArray` doesn't have `get(Int)` that returns nullable. If `i` is out of bounds, it throws IndexOutOfBoundsException. The original `optString(i)` returns `""` if out of bounds. But let's see if we iterate `0 until size`, it's not out of bounds!
    
    with open(path, 'w') as f:
        f.write(content)

process_file('shared/src/commonMain/kotlin/com/lucent/app/harness/ooxml/Pptx.kt')
process_file('shared/src/commonMain/kotlin/com/lucent/app/harness/ooxml/Xlsx.kt')
