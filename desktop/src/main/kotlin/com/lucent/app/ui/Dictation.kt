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

/**
 * PHASE 4 — voice-to-text entry point (desktop side of the seam).
 *
 * ### Why the user's own AI provider, and not a bundled recognizer
 *
 * Windows has no speech service a JVM app can reach without native interop, and bundling an
 * offline model (whisper.cpp, Vosk) means another native build in CI plus tens of MB per language.
 * But this app ALREADY holds a configured OpenAI-compatible endpoint and key for the assistant —
 * and that ecosystem ships a transcription endpoint (`/audio/transcriptions`, Whisper-class
 * models) right next to `/chat/completions`. So desktop dictation records locally and asks the
 * provider the user already trusts with their chats. No new account, no new secret, no new native
 * dependency; and no language parameter is sent, so the model auto-detects — which is what
 * "multilingual" honestly means here.
 *
 * Privacy line, stated plainly: unlike everything else typed into a note, a dictated clip LEAVES
 * the machine (to the user's own configured provider — never to us). The button therefore refuses
 * with a clear message when no API is configured, rather than silently arming a network path.
 *
 * ### The two-tap shape
 *
 * Tap = start recording (icon becomes a red stop square); tap again = stop, transcribe, append.
 * A spinner shows while the provider is thinking. 16 kHz mono 16-bit WAV — the format Whisper-class
 * models are trained on; anything fancier is bytes for nothing.
 *
 * Same signature as the Android file: the caller appends via [onText] and never learns how the
 * text was produced.
 */
@Composable
fun DictationButton(onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = DesktopContext
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val baseUrl by repo.baseUrl.collectAsState(initial = "")
    val apiKey by repo.apiKey.collectAsState(initial = "")

    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf<TargetDataLine?>(null) }
    var buffer by remember { mutableStateOf<ByteArrayOutputStream?>(null) }

    fun startRecording() {
        if (baseUrl.isBlank()) {
            // No endpoint, no dictation: see the privacy note in the header. This is a message,
            // not a silent no-op — the user is told exactly what to set up.
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
            // Pump on IO: the line blocks on read, and the UI thread must keep drawing the red
            // stop button that ends this.
            scope.launch(Dispatchers.IO) {
                val chunk = ByteArray(4096)
                while (recording && l.isOpen) {
                    val n = try { l.read(chunk, 0, chunk.size) } catch (_: Throwable) { -1 }
                    if (n <= 0) break
                    out.write(chunk, 0, n)
                }
            }
        } catch (t: Throwable) {
            // No microphone, or the OS refused the line — say so.
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
                    // Under half a second of audio is a misclick, not a message.
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
                transcribing -> Unit // one job at a time; the spinner is the answer
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

/** Wrap raw 16 kHz mono 16-bit little-endian PCM in a WAV container via the sound API itself. */
private fun wavBytes(pcm: ByteArray): ByteArray {
    val format = AudioFormat(16000f, 16, 1, true, false)
    val stream = AudioInputStream(pcm.inputStream(), format, (pcm.size / format.frameSize).toLong())
    val out = ByteArrayOutputStream()
    AudioSystem.write(stream, javax.sound.sampled.AudioFileFormat.Type.WAVE, out)
    return out.toByteArray()
}

/**
 * POST the clip to the provider's OpenAI-compatible transcription endpoint.
 *
 * `whisper-1` is the ecosystem's de-facto default transcription model name; providers that use a
 * different one (e.g. Groq's whisper-large-v3) will reject it, which surfaces as the generic
 * failure toast — a provider-specific model-name setting is a known follow-up, recorded in the
 * phase report rather than invented here as an eleventh setting nobody asked for yet.
 */
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
