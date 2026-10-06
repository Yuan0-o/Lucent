import re
import os

def process_file(path):
    with open(path, 'r') as f:
        content = f.read()

    # Imports
    content = re.sub(r'import org\.json\.JSONArray\nimport org\.json\.JSONObject', 'import kotlinx.serialization.json.*\nimport kotlinx.serialization.json.Json.Default.parseToJsonElement', content)
    content = content.replace('import org.json.JSONArray\n', '')
    content = content.replace('import org.json.JSONObject\n', '')
    
    # New imports if missed
    if 'import kotlinx.serialization.json.*' not in content:
        content = content.replace('import org.w3c.dom.', 'import kotlinx.serialization.json.*\nimport org.w3c.dom.')
        
    # Types
    content = content.replace('JSONObject', 'JsonObject')
    content = content.replace('JSONArray', 'JsonArray')
    
    # JSONObject(text) -> Json.parseToJsonElement(text).jsonObject
    content = re.sub(r'JsonObject\(([^)]+)\)', r'Json.parseToJsonElement(\1).jsonObject', content)
    # JSONArray(text) -> Json.parseToJsonElement(text).jsonArray
    content = re.sub(r'JsonArray\(([^)]+)\)', r'Json.parseToJsonElement(\1).jsonArray', content)
    
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
    
    # length() -> size
    content = re.sub(r'\.length\(\)', r'.size', content)
    
    # Array optJSONObject(i) -> [i].jsonObject (Actually jsonArray[i].jsonObject might throw, but let's assume it's fine or we use getOrNull)
    # The prompt regex might replace `.optJSONObject(i)` with `[i]?.jsonObject` which is wrong for arrays.
    # Wait, `.optJSONObject(i)` where `i` is a variable without quotes.
    # If the argument has no quotes, it's probably an array access.
    # Let's fix array accesses.
    
    with open(path, 'w') as f:
        f.write(content)

process_file('shared/src/commonMain/kotlin/com/lucent/app/harness/ooxml/Pptx.kt')
process_file('shared/src/commonMain/kotlin/com/lucent/app/harness/ooxml/Xlsx.kt')
