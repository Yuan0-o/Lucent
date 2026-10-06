import re

with open('shared/src/commonMain/kotlin/com/lucent/app/harness/plugins/PluginDownload.kt', 'r') as f:
    content = f.read()

content = content.replace('import java.io.File\n', '')
content = content.replace('import java.io.FileOutputStream\n', '')
content = content.replace('import java.security.MessageDigest\n', '')
content = content.replace('import okhttp3.Request\n', 'import okhttp3.Request\nimport okio.FileSystem\nimport okio.Path\nimport okio.Path.Companion.toPath\nimport com.lucent.app.data.CryptoPlatform\n')

content = content.replace('target: File,', 'target: Path,')
content = content.replace('body.byteStream().use { input ->', 'body.source().use { input ->')
content = content.replace('target.parentFile?.mkdirs()', 'target.parent?.let { FileSystem.SYSTEM.createDirectories(it) }')
content = content.replace('target.parentFile?.usableSpace', 'target.parent?.let { java.io.File(it.toString()).usableSpace }')
content = content.replace('val partFile = File(target.path + ".part")', 'val partFile = "$target.part".toPath()')
content = content.replace('if (partFile.exists()) partFile.length() else 0L', 'FileSystem.SYSTEM.metadataOrNull(partFile)?.size ?: 0L')
content = content.replace('partFile.delete()', 'FileSystem.SYSTEM.delete(partFile)')
content = content.replace('target.delete()', 'FileSystem.SYSTEM.delete(target)')
content = content.replace('partFile.writeBytes(ByteArray(0))', 'FileSystem.SYSTEM.sink(partFile).use { }')
content = content.replace('target.path', 'target.toString()')

# file output stream replacement
regex_fos = r"FileOutputStream\(partFile,\s*append\)\.use\s*\{\s*output\s*->"
replacement_fos = """val sink = if (append) FileSystem.SYSTEM.appendingSink(partFile) else FileSystem.SYSTEM.sink(partFile)\n                    sink.buffer().use { output ->"""
content = re.sub(regex_fos, replacement_fos, content)

# digest replacement
regex_digest = r"val digest = MessageDigest\.getInstance\(\"SHA-256\"\)\s*partFile\.inputStream\(\)\.use \{ input ->\s*val mdBuf = ByteArray\(1024 \* 1024\)\s*while \(true\) \{\s*val read = input\.read\(mdBuf\)\s*if \(read <= 0\) break\s*digest\.update\(mdBuf, 0, read\)\s*\}\s*\}\s*val hex = digest\.digest\(\)\.joinToString\(\"\"\) \{ \"%02x\"\.format\(it\) \}"
replacement_digest = """val bytes = FileSystem.SYSTEM.read(partFile) { readByteArray() }
                val hex = CryptoPlatform.sha256(bytes).joinToString("") {
                    it.toUByte().toString(16).padStart(2, '0')
                }"""
content = re.sub(regex_digest, replacement_digest, content)

# renameTo replacement
regex_rename = r"if \(\!partFile\.renameTo\(target\)\) \{\s*partFile\.copyTo\(target, overwrite = true\)\s*FileSystem\.SYSTEM\.delete\(partFile\)\s*\}"
regex_rename2 = r"if \(\!partFile\.renameTo\(target\)\) \{\s*partFile\.copyTo\(target, overwrite = true\)\s*partFile\.delete\(\)\s*\}"
replacement_rename = """try {
                    FileSystem.SYSTEM.atomicMove(partFile, target)
                } catch (_: Exception) {
                    FileSystem.SYSTEM.read(partFile) {
                        FileSystem.SYSTEM.write(target) {
                            writeAll(this@read)
                        }
                    }
                    FileSystem.SYSTEM.delete(partFile)
                }"""
content = re.sub(regex_rename, replacement_rename, content)
content = re.sub(regex_rename2, replacement_rename, content)


with open('shared/src/commonMain/kotlin/com/lucent/app/harness/plugins/PluginDownload.kt', 'w') as f:
    f.write(content)
