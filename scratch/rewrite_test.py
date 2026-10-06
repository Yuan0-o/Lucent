import re

with open('shared/src/commonTest/kotlin/com/lucent/app/harness/PluginPipelineTest.kt', 'r') as f:
    content = f.read()

# I will replace `File(HarnessRuntime.downloadsDirPath(), "short.bin")`
# with `(HarnessRuntime.downloadsDirPath().toPath() / "short.bin")`
content = content.replace('val target = File(HarnessRuntime.downloadsDirPath(), "short.bin")', 'val target = HarnessRuntime.downloadsDirPath().toPath() / "short.bin"')
content = content.replace('val target = File(HarnessRuntime.downloadsDirPath(), "checksum.bin")', 'val target = HarnessRuntime.downloadsDirPath().toPath() / "checksum.bin"')

content = content.replace('assertFalse(target.exists())', 'assertFalse(FileSystem.SYSTEM.exists(target))')
content = content.replace('assertTrue(target.exists())', 'assertTrue(FileSystem.SYSTEM.exists(target))')

with open('shared/src/commonTest/kotlin/com/lucent/app/harness/PluginPipelineTest.kt', 'w') as f:
    f.write(content)
