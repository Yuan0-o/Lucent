import re

with open('shared/src/commonMain/kotlin/com/lucent/app/harness/plugins/PluginCatalogRemote.kt', 'r') as f:
    content = f.read()

content = content.replace('import java.io.File\n', '')
content = content.replace('import java.net.URL\n', '')
content = content.replace('import javax.net.ssl.HttpsURLConnection\n', 'import okio.FileSystem\nimport okio.Path.Companion.toPath\nimport okhttp3.OkHttpClient\nimport okhttp3.Request\nimport kotlin.time.Duration.Companion.seconds\n')

content = content.replace('val cacheFile = File(HarnessRuntime.downloadsDirPath(), "catalog-cache.json")', 'val cacheFile = HarnessRuntime.downloadsDirPath().toPath() / "catalog-cache.json"')
content = content.replace('cacheFile.exists()', 'FileSystem.SYSTEM.exists(cacheFile)')
content = content.replace('cacheFile.readText()', 'FileSystem.SYSTEM.read(cacheFile) { readUtf8() }')

# Replace the HttpsURLConnection logic
regex_http = r"val connection = URL\(urlStr\)\.openConnection\(\) as HttpsURLConnection\s*connection\.connectTimeout = 10000\s*connection\.readTimeout = 10000\s*val json = connection\.inputStream\.bufferedReader\(\)\.use \{ it\.readText\(\) \}\s*cacheFile\.writeText\(json\)"

replacement_http = """val client = OkHttpClient.Builder()
                    .connectTimeout(10.seconds)
                    .readTimeout(10.seconds)
                    .build()
                val request = Request.Builder().url(urlStr).build()
                val json = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.string() ?: throw Exception("Empty body")
                }
                
                FileSystem.SYSTEM.write(cacheFile) { writeUtf8(json) }"""
content = re.sub(regex_http, replacement_http, content)

with open('shared/src/commonMain/kotlin/com/lucent/app/harness/plugins/PluginCatalogRemote.kt', 'w') as f:
    f.write(content)
