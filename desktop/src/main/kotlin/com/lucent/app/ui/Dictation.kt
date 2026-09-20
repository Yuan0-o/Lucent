package com.lucent.app.ui

import android.content.DesktopContext
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

@Composable
fun DictationButton(onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = DesktopContext
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val baseUrl by repo.baseUrl.collectAsState(initial = com.lucent.app.data.SettingsCache.baseUrl)
    val apiKey by repo.apiKey.collectAsState(initial = com.lucent.app.data.SettingsCache.apiKey)

    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf<TargetDataLine?>(null) }
    var buffer by remember { mutableStateOf<ByteArrayOutputStream?>(null) }

    fun startRecording() {
        if (baseUrl.isBlank()) {
            LucentToast.show(context, S.sttNeedsApi, longDuration = true)
            return
        }
        try {
            val format = AudioFormat(16000f, 16, 1, true, false)
            val info = DataLine.Info(TargetDataLine::class.java, format)
            val l = AudioSystem.getLine(info) as TargetDataLine
            l.open(format)
            l.start()
            val out = ByteArrayOutputStream()
            line = l
            buffer = out
            recording = true
            scope.launch(Dispatchers.IO) {
                val chunk = ByteArray(4096)
                while (recording && l.isOpen) {
                    val n = try { l.read(chunk, 0, chunk.size) } catch (_: Throwable) { -1 }
                    if (n <= 0) break
                    out.write(chunk, 0, n)
                }
            }
        } catch (t: Throwable) {
            LucentToast.show(context, S.dictateFailed)
            recording = false
        }
    }

    fun stopAndTranscribe() {
        recording = false
        val l = line ?: return
        val out = buffer ?: return
        line = null
        buffer = null
        transcribing = true
        scope.launch(Dispatchers.IO) {
            val text = try {
                try { l.stop(); l.close() } catch (_: Throwable) {}
                val pcm = out.toByteArray()
                if (pcm.size < 16000) {
                    null
                } else {
                    transcribe(wavBytes(pcm), baseUrl, apiKey)
                }
            } catch (t: Throwable) {
                null
            }
            withContext(Dispatchers.Main) {
                transcribing = false
                if (text.isNullOrBlank()) LucentToast.show(context, S.dictateFailed)
                else onText(text.trim())
            }
        }
    }

    IconButton(
        onClick = {
            when {
                transcribing -> Unit
                recording -> stopAndTranscribe()
                else -> startRecording()
            }
        },
        modifier = modifier
    ) {
        when {
            transcribing -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            recording -> Icon(Icons.Default.Stop, contentDescription = S.dictateStop, tint = Color(0xFFE57373))
            else -> Icon(Icons.Default.Mic, contentDescription = S.dictateStart)
        }
    }
}

private fun wavBytes(pcm: ByteArray): ByteArray {
    val format = AudioFormat(16000f, 16, 1, true, false)
    val stream = AudioInputStream(pcm.inputStream(), format, (pcm.size / format.frameSize).toLong())
    val out = ByteArrayOutputStream()
    AudioSystem.write(stream, javax.sound.sampled.AudioFileFormat.Type.WAVE, out)
    return out.toByteArray()
}

private fun transcribe(wav: ByteArray, baseUrl: String, apiKey: String): String? {
    val url = baseUrl.trimEnd('/') + "/audio/transcriptions"
    val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
        .addFormDataPart("model", "whisper-1")
        .build()
    val request = Request.Builder()
        .url(url)
        .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
        .post(body)
        .build()
    val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val raw = resp.body?.string() ?: return null
        return try { JSONObject(raw).optString("text").ifBlank { null } } catch (_: Throwable) { null }
    }
}
