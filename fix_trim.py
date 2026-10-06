import re

with open('shared/src/commonMain/kotlin/com/lucent/app/harness/ooxml/Pptx.kt', 'r') as f:
    content = f.read()

# Replace `obj["key"]?.jsonPrimitive?.content ?: "".trim()`
# with `(obj["key"]?.jsonPrimitive?.content ?: "").trim()`

# Regex: ([a-zA-Z0-9_\[\]]+\[[^\]]+\]\?\.jsonPrimitive\?\.content)\s*\?:\s*""(\.[a-zA-Z0-9_(.)]+)+
# Example: op["op"]?.jsonPrimitive?.content ?: "".trim().lowercase(Locale.US)

def repl(m):
    left = m.group(1)
    right_methods = m.group(2)
    return f"({left} ?: \"\"){right_methods}"

content = re.sub(r'([a-zA-Z0-9_\[\]]+\[[^\]]+\]\?\.jsonPrimitive\?\.content)\s*\?:\s*""((?:\.[a-zA-Z0-9_(.)]+)+)', repl, content)

with open('shared/src/commonMain/kotlin/com/lucent/app/harness/ooxml/Pptx.kt', 'w') as f:
    f.write(content)

