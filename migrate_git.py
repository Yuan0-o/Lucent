import re

def migrate_git_tools(content):
    content = content.replace("import org.json.JSONObject\n", "import kotlinx.serialization.json.*\n")
    content = content.replace(": JSONObject", ": JsonObject")
    content = content.replace("args.optString(\"repo\", \"\")", '(args["repo"]?.jsonPrimitive?.content ?: "")')
    content = content.replace("args.optString(key, \"\")", '(args[key]?.jsonPrimitive?.content ?: "")')
    content = content.replace("args.optBoolean(\"stat\", false)", '(args["stat"]?.jsonPrimitive?.booleanOrNull ?: false)')
    content = content.replace("args.optBoolean(\"staged\", false)", '(args["staged"]?.jsonPrimitive?.booleanOrNull ?: false)')
    content = content.replace("args.optInt(\"limit\", 20)", '(args["limit"]?.jsonPrimitive?.intOrNull ?: 20)')
    content = content.replace("args.optBoolean(\"force\", false)", '(args["force"]?.jsonPrimitive?.booleanOrNull ?: false)')
    content = content.replace("args.optString(\"message\", \"\")", '(args["message"]?.jsonPrimitive?.content ?: "")')
    content = content.replace("args.optBoolean(\"all\", false)", '(args["all"]?.jsonPrimitive?.booleanOrNull ?: false)')
    content = content.replace("args.optBoolean(\"no_ff\", false)", '(args["no_ff"]?.jsonPrimitive?.booleanOrNull ?: false)')
    content = content.replace("args.optBoolean(\"rebase\", false)", '(args["rebase"]?.jsonPrimitive?.booleanOrNull ?: false)')
    content = content.replace("args.optBoolean(\"set_upstream\", false)", '(args["set_upstream"]?.jsonPrimitive?.booleanOrNull ?: false)')
    content = content.replace("args.optString(\"patch\", \"\")", '(args["patch"]?.jsonPrimitive?.content ?: "")')
    content = content.replace("args.optBoolean(\"reverse\", false)", '(args["reverse"]?.jsonPrimitive?.booleanOrNull ?: false)')
    return content

with open("shared/src/commonMain/kotlin/com/lucent/app/harness/GitTools.kt", "r") as f:
    text = f.read()
text = migrate_git_tools(text)
with open("shared/src/commonMain/kotlin/com/lucent/app/harness/GitTools.kt", "w") as f:
    f.write(text)

