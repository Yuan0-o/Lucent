package com.lucent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.LucentBuild
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass

private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val MIT_URL = "https://opensource.org/license/mit"
private const val BSD3_URL = "https://opensource.org/license/bsd-3-clause"
private const val SQLCIPHER_URL = "https://www.zetetic.net/sqlcipher/license/"
private const val JSON_URL = "https://www.json.org/license.html"
private const val SQLITE_URL = "https://www.sqlite.org/copyright.html"
private const val OFL_URL = "https://openfontlicense.org"

private data class LicenceEntry(
    val name: String,
    val holder: String,
    val licence: String,
    val url: String
)

private val ENTRIES = listOf(
    LicenceEntry("Kotlin", "JetBrains s.r.o.", "Apache-2.0", APACHE_URL),
    LicenceEntry("kotlinx.coroutines", "JetBrains s.r.o.", "Apache-2.0", APACHE_URL),
    LicenceEntry("Jetpack Compose and AndroidX", "The Android Open Source Project", "Apache-2.0", APACHE_URL),
    LicenceEntry("Compose Multiplatform and Skiko", "JetBrains s.r.o.", "Apache-2.0", APACHE_URL),
    LicenceEntry("Material Design Icons", "Google LLC", "Apache-2.0", APACHE_URL),
    LicenceEntry("Haze", "Chris Banes and contributors", "Apache-2.0", APACHE_URL),
    LicenceEntry("OkHttp", "Square, Inc.", "Apache-2.0", APACHE_URL),
    LicenceEntry("Apache PDFBox", "The Apache Software Foundation", "Apache-2.0", APACHE_URL),
    LicenceEntry("SQLite JDBC", "Taro L. Saito, David Crawshaw and contributors", "Apache-2.0", APACHE_URL),
    LicenceEntry("llama.cpp and GGML", "The ggml authors", "MIT", MIT_URL),
    LicenceEntry("Shizuku API", "Rikka and the Shizuku-API contributors", "MIT", MIT_URL),
    LicenceEntry("SQLCipher", "Zetetic LLC", "BSD-style", SQLCIPHER_URL),
    LicenceEntry("Skia", "Google LLC", "BSD-3-Clause", BSD3_URL),
    LicenceEntry("SQLite", "The SQLite authors", "Public domain", SQLITE_URL),
    LicenceEntry("org.json", "JSON.org", "JSON License", JSON_URL),
    LicenceEntry("Great Vibes", "The Great Vibes Pro Project Authors", "SIL OFL 1.1", OFL_URL)
)

@Composable
internal fun LicenceSettingsPage(onRoute: (SettingsRoute) -> Unit, onOpenUrl: ((String) -> Unit)? = null) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    BackHeader(onBack = { onRoute(SettingsRoute.About) })
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.licencesIntro, color = onGradientMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))
        ENTRIES.forEach { entry ->
            Text(entry.name, color = onGradient, fontSize = 13.sp)
            Text("${entry.holder} · ${entry.licence}", color = onGradientMuted, fontSize = 11.sp)
            Text(
                text = S.licencesFullText,
                color = onGradient,
                fontSize = 11.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onOpenUrl?.invoke(entry.url) }.padding(vertical = 2.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = S.licencesAllNotices,
            color = onGradient,
            fontSize = 12.sp,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { onOpenUrl?.invoke(LucentBuild.LICENSES_PAGE) }
        )
    }
}
