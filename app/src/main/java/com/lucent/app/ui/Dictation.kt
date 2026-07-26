package com.lucent.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lucent.app.i18n.S
import com.lucent.app.i18n.lucentLocale

/**
 * PHASE 4 — voice-to-text entry point (Android side of the seam).
 *
 * ### Why the system recognizer and not a bundled model
 *
 * Android ships a speech recognizer behind [RecognizerIntent]: a system-drawn dialog that records,
 * transcribes (on-device on most modern phones, falling back to the vendor service), and returns
 * plain text. Using it means: no RECORD_AUDIO permission in our manifest (the system activity holds
 * the mic, not us — one less scary prompt), no bundled model growing the APK, and the same
 * recognition quality every other app on the phone gets. The desktop twin of this file has no such
 * service and takes a different path (the user's own AI provider) — this is a platform seam on
 * purpose, same signature both sides.
 *
 * ### Language
 *
 * The recognizer is pinned to the APP language (not the device language): a user running Lucent in
 * Japanese on an English phone is dictating Japanese. That is the same rule every string in the app
 * already follows — the in-app language setting wins.
 *
 * The recognized text is APPENDED via [onText]; the caller decides how it joins the field. Nothing
 * here touches any field directly, so one button works for notes, tasks, and the chat alike.
 */
@Composable
fun DictationButton(onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) onText(spoken)
        }
        // A cancel (back gesture on the system dialog) is a decision, not an error — no toast.
    }

    IconButton(
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lucentLocale().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, S.dictateStart)
            }
            try {
                launcher.launch(intent)
            } catch (t: Throwable) {
                // No recognizer on this device (rare; some de-Googled builds). Say so instead of
                // silently doing nothing — a button that sometimes does nothing reads as broken.
                LucentToast.show(context.applicationContext, S.dictateFailed)
            }
        },
        modifier = modifier
    ) {
        Icon(Icons.Default.Mic, contentDescription = S.dictateStart)
    }
}
