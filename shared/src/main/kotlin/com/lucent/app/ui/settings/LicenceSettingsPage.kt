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
private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val LGPL_URL = "https://www.gnu.org/licenses/lgpl-3.0.html"
private const val MPL_URL = "https://www.mozilla.org/MPL/2.0/"
private const val AGPL_URL = "https://www.gnu.org/licenses/agpl-3.0.html"
private const val TERMUX_URL = "https://termux.dev"
private const val PRoot_URL = "https://proot-me.github.io"
private const val LIBREOFFICE_URL = "https://www.libreoffice.org"
private const val PYTHON_URL = "https://www.python.org"
private const val NODE_URL = "https://nodejs.org"
private const val PLAYWRIGHT_URL = "https://playwright.dev"
private const val GIT_URL = "https://git-scm.com"
private const val PANDOC_URL = "https://pandoc.org"
private const val FFMPEG_URL = "https://ffmpeg.org"
private const val RIPGREP_URL = "https://github.com/BurntSushi/ripgrep"
private const val SEVENZIP_URL = "https://7-zip.org"
private const val TESSERACT_URL = "https://github.com/tesseract-ocr/tesseract"
private const val QPDF_URL = "https://qpdf.readthedocs.io"
private const val POPPLER_URL = "https://poppler.freedesktop.org"
private const val YTDLP_URL = "https://github.com/yt-dlp/yt-dlp"

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
    LicenceEntry("Great Vibes", "The Great Vibes Pro Project Authors", "SIL OFL 1.1", OFL_URL),
    LicenceEntry("Termux", "Termux contributors", "GPL-3.0 · optional download", TERMUX_URL),
    LicenceEntry("PRoot and proot-distro", "PRoot contributors", "GPL-2.0 · optional download", PRoot_URL),
    LicenceEntry("Ubuntu Base", "Canonical and contributors", "mixed free licences · optional download", LIBREOFFICE_URL),
    LicenceEntry("LibreOffice", "The Document Foundation", "MPL-2.0 · optional download", LIBREOFFICE_URL),
    LicenceEntry("Python", "The Python Software Foundation", "PSF-2.0 · optional download", PYTHON_URL),
    LicenceEntry("python-docx, openpyxl, python-pptx", "their respective authors", "MIT · optional download", PYTHON_URL),
    LicenceEntry("XlsxWriter", "John McNamara", "BSD-2-Clause · optional download", PYTHON_URL),
    LicenceEntry("pandas", "The pandas development team", "BSD-3-Clause · optional download", PYTHON_URL),
    LicenceEntry("PyMuPDF", "Artifex Software", "AGPL-3.0 · optional download", AGPL_URL),
    LicenceEntry("Node.js", "The OpenJS Foundation", "MIT · optional download", NODE_URL),
    LicenceEntry("Playwright", "Microsoft Corporation", "Apache-2.0 · optional download", PLAYWRIGHT_URL),
    LicenceEntry("Git", "The Git contributors", "GPL-2.0 · optional download", GIT_URL),
    LicenceEntry("Pandoc", "John MacFarlane", "GPL-2.0-or-later · optional download", PANDOC_URL),
    LicenceEntry("FFmpeg", "The FFmpeg developers", "LGPL-2.1 / GPL-2.0 · optional download", FFMPEG_URL),
    LicenceEntry("ripgrep", "Andrew Gallant", "MIT / Unlicense · optional download", RIPGREP_URL),
    LicenceEntry("7-Zip", "Igor Pavlov", "LGPL-2.1 · optional download", SEVENZIP_URL),
    LicenceEntry("Tesseract OCR", "The Tesseract contributors", "Apache-2.0 · optional download", TESSERACT_URL),
    LicenceEntry("qpdf", "Jay Berkenbilt and contributors", "Apache-2.0 · optional download", QPDF_URL),
    LicenceEntry("Poppler", "The Poppler developers", "GPL-2.0 / GPL-3.0 · optional download", POPPLER_URL),
    LicenceEntry("yt-dlp", "The yt-dlp contributors", "Unlicense · optional download", YTDLP_URL),
    LicenceEntry("ImageMagick", "ImageMagick Studio LLC", "ImageMagick Licence · optional download", "https://imagemagick.org")
)

@Composable
internal fun LicenceSettingsPage(onRoute: (SettingsRoute) -> Unit, onOpenUrl: ((String) -> Unit)? = null) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    BackHeader(onBack = { onRoute(SettingsRoute.About) })
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.licencesIntro, color = onGradientMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(S.licencesOptionalNote, color = onGradientMuted, fontSize = 12.sp)
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
