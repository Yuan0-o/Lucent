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
    }

    IconButton(
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lucentLocale().toLanguageTag())
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES,
                    ArrayList(
                        (listOf(lucentLocale().toLanguageTag()) +
                            listOf("en-US", "zh-CN", "ja-JP", "ko-KR") +
                            listOf(java.util.Locale.getDefault().toLanguageTag())).distinct()
                    )
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lucentLocale().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, S.dictateStart)
            }
            try {
                launcher.launch(intent)
            } catch (t: Throwable) {
                LucentToast.show(context.applicationContext, S.dictateFailed)
            }
        },
        modifier = modifier
    ) {
        Icon(Icons.Default.Mic, contentDescription = S.dictateStart)
    }
}
