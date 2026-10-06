import re

with open('./shared/src/commonMain/kotlin/com/lucent/app/harness/OfficeSheetTools.kt', 'r') as f:
    content = f.read()

# JSONObject -> JsonObject for args
content = re.sub(r'args: JSONObject', 'args: JsonObject', content)

# args.optInt("max_rows", 200) -> (args["max_rows"]?.jsonPrimitive?.intOrNull ?: 200)
content = content.replace('args.optInt("max_rows", 200)', '(args["max_rows"]?.jsonPrimitive?.intOrNull ?: 200)')

# args.opt("spec") -> args["spec"]
content = content.replace('args.opt("spec")', 'args["spec"]')

# args.opt("ops") -> args["ops"]
content = content.replace('args.opt("ops")', 'args["ops"]')

# is JSONObject -> is JsonObject
content = content.replace('is JSONObject ->', 'is JsonObject ->')

# JSONObject(raw) -> Json.parseToJsonElement(raw.content).jsonObject (Wait, if raw is JsonPrimitive, it has .content)
content = content.replace('JSONObject(raw)', 'Json.parseToJsonElement(raw.content).jsonObject')

# But wait, there's also JSONObject(text) in operationsFromText, let's just do text replacements for spreadsheetSpec
# Actually, I'll just write a script that replaces the whole methods to be completely safe

methods_to_replace = """    private fun spreadsheetSpec(args: JsonObject): String {
        val raw = args["spec"]
        val spec = when (raw) {
            is JsonObject -> raw
            is JsonPrimitive -> if (raw.content.isBlank()) {
                null
            } else {
                try {
                    Json.parseToJsonElement(raw.content).jsonObject
                } catch (e: Exception) {
                    throw IllegalArgumentException("The \\"spec\\" argument is not valid JSON: ${e.message ?: "parse error"}")
                }
            }
            else -> null
        } ?: throw IllegalArgumentException("Give the workbook spec in \\"spec\\" with a \\"sheets\\" array.")
        if (!spec.containsKey("sheets")) {
            throw IllegalArgumentException(
                "The \\"spec\\" needs a \\"sheets\\" array, for example {\\"sheets\\":[{\\"name\\":\\"Sheet1\\",\\"rows\\":[[1,2]]}]}."
            )
        }
        return spec.toString()
    }

    private fun operations(args: JsonObject): String {
        val raw = args["ops"]
        val array = when (raw) {
            is JsonArray -> raw
            is JsonObject -> buildJsonArray { add(raw) }
            is JsonPrimitive -> if (raw.content.isBlank()) buildJsonArray {} else operationsFromText(raw.content)
            else -> buildJsonArray {}
        }
        if (array.size == 0) throw IllegalArgumentException("Give at least one edit operation in \\"ops\\".")
        return array.toString()
    }

    private fun operationsFromText(text: String): JsonArray = try {
        Json.parseToJsonElement(text).jsonArray
    } catch (e: Exception) {
        try {
            buildJsonArray { add(Json.parseToJsonElement(text).jsonObject) }
        } catch (e2: Exception) {
            throw IllegalArgumentException("The \\"ops\\" argument is not valid JSON: ${e.message ?: "parse error"}")
        }
    }"""

# regex to replace from spreadsheetSpec to end
import re
content = re.sub(r'    private fun spreadsheetSpec\(args: JsonObject\): String \{.*$', methods_to_replace + '\n}', content, flags=re.DOTALL)

# Now stringOf replacements
def repl_stringof(m):
    # stringOf(args, "key", "fallback") -> (args["key"]?.jsonPrimitive?.content ?: "fallback")
    # stringOf(args, "key") -> (args["key"]?.jsonPrimitive?.content ?: "")
    args_str = m.group(1)
    if len(m.groups()) > 1 and m.group(2):
        fallback = m.group(2).strip()
        if fallback.startswith(', '):
            fallback = fallback[2:]
        return f'(args[{args_str}]?.jsonPrimitive?.content ?: {fallback})'
    return f'(args[{args_str}]?.jsonPrimitive?.content ?: "")'

content = re.sub(r'stringOf\(args,\s*("[^"]+")\)', lambda m: f'(args[{m.group(1)}]?.jsonPrimitive?.content ?: "")', content)
content = re.sub(r'stringOf\(args,\s*("[^"]+"),\s*("[^"]+")\)', lambda m: f'(args[{m.group(1)}]?.jsonPrimitive?.content ?: {m.group(2)})', content)

with open('./shared/src/commonMain/kotlin/com/lucent/app/harness/OfficeSheetTools.kt', 'w') as f:
    f.write(content)
