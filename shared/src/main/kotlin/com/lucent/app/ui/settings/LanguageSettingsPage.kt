package com.lucent.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.FontStore
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.AppLanguage
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentFontResolver
import com.lucent.app.ui.SYSTEM_FONT_KEY
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

/**
 * P1-3 — extracted from the `LanguagePage` local composable that used to live inside
 * `SettingsScreen` (byte-identical on both platforms before this split, down to the language
 * list and every font row).
 *
 * The font-import *mechanism* deliberately stays out of this page and in [SettingsScreen] on each
 * platform: picking a file is [rememberLauncherForActivityResult]/`ActivityResultContracts` on
 * Android and a native file dialog on desktop (`pickImportFont`), the two are already unrelated
 * pieces of platform code (not a "same shape, different body" seam candidate), and the picked file
 * then goes through a naming dialog before anything is imported. Moving that whole flow in here
 * would mean either duplicating it per platform inside a shared file (defeating the point) or
 * inventing a new picker abstraction under real time pressure, which is exactly what the brief
 * warned against. So this page only ever calls [onImportFontClick] — a plain "start picking" — and
 * reads [importedFonts]/[fontCanImportMore]/[fontImporting]/[fontError], which stay owned by
 * `SettingsScreen` for the same reason Personalization's name/style fields do: outer-scope code
 * un-related to this page's own composition (the naming dialog's confirm button, the delete
 * dialog) reads and writes them too.
 */
@Composable
internal fun LanguageSettingsPage(
    repo: SettingsRepository,
    importedFonts: List<FontStore.FontSlot>,
    fontCanImportMore: Boolean,
    fontImporting: Boolean,
    fontError: String,
    onRequestDeleteFont: (FontStore.FontSlot) -> Unit,
    onImportFontClick: () -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val context = LocalContext.current
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val savedLanguage by repo.appLanguage.collectAsState(initial = "system")
    val savedFont by repo.font.collectAsState(initial = "system")

    BackHeader(S.settingsLanguageTitle) { onRoute(SettingsRoute.Root) }

    // One flat radio list: "follow the system", then the four languages, each shown in
    // its OWN language (the one universal convention for language pickers — a reader who
    // can't parse the current UI language can still find their own name). Selecting
    // writes the setting; MainActivity's collector applies it, and because the catalog
    // is snapshot state every S-reading text in the app — including this list —
    // recomposes in the new language on the very next frame. No restart, no flash.
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { AppScope.io.launch { repo.setAppLanguage(AppLanguage.SYSTEM.key) } }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = savedLanguage == AppLanguage.SYSTEM.key,
                onClick = { AppScope.io.launch { repo.setAppLanguage(AppLanguage.SYSTEM.key) } }
            )
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(S.langSystem, color = onGradient)
            }
        }

        listOf(AppLanguage.EN, AppLanguage.ZH, AppLanguage.JA, AppLanguage.KO).forEach { lang ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { AppScope.io.launch { repo.setAppLanguage(lang.key) } }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = savedLanguage == lang.key,
                    onClick = { AppScope.io.launch { repo.setAppLanguage(lang.key) } }
                )
                Text(lang.label, color = onGradient, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    // Font sits inline here, parallel to the language picker above rather than behind a
    // further tap: a typeface is a writing/language choice as much as a visual one. The app
    // bundles no fonts (font library task): out of the box it follows the platform font,
    // and every other row is a font the user imported, shown under the name they gave it
    // and drawn in its own face so the list doubles as a live preview. Selecting saves
    // immediately; the trailing icon deletes an imported font (after confirming).
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.settingsFontTitle, color = onGradient, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(S.settingsFontSub, color = onGradientMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))

        // The system-default row. Also selected when the saved key is a dangling id (a
        // state only reachable by hand-editing storage): the app *renders* the system font
        // then, and the radio must tell the truth about what is on screen.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable {
                AppScope.io.launch { repo.setFont(SYSTEM_FONT_KEY) }
            }
        ) {
            RadioButton(
                selected = savedFont == SYSTEM_FONT_KEY || importedFonts.none { it.id == savedFont },
                onClick = { AppScope.io.launch { repo.setFont(SYSTEM_FONT_KEY) } }
            )
            Text(
                S.fontSystemLabel,
                color = onGradient,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        // One row per imported font: radio + the user's name for it + delete. Same anatomy
        // as the system row, plus the trailing delete — an imported font is the user's to
        // remove, the platform default is not.
        importedFonts.forEach { slot ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    AppScope.io.launch { repo.setFont(slot.id) }
                }
            ) {
                RadioButton(
                    selected = savedFont == slot.id,
                    onClick = { AppScope.io.launch { repo.setFont(slot.id) } }
                )
                Text(
                    slot.name.ifBlank { slot.fileName },
                    color = onGradient,
                    fontFamily = LucentFontResolver.resolve(context, slot.id),
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
                IconButton(onClick = { onRequestDeleteFont(slot) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = S.fontDeleteA11y,
                        tint = onGradientMuted
                    )
                }
            }
        }
        if (importedFonts.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.fontNoneImportedHint, color = onGradientMuted, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (fontImporting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.width(12.dp))
                Text(S.fontImporting, color = onGradientMuted, fontSize = 13.sp)
            }
        } else if (fontCanImportMore) {
            GlassButton(
                text = S.fontImportButton,
                icon = Icons.Default.Add,
                onClick = onImportFontClick
            )
        } else {
            Text(S.fontSlotsFullHint(FontStore.MAX_FONTS), color = onGradientMuted, fontSize = 12.sp)
        }
        if (fontError.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(fontError, color = Color(0xFFFFC1C1), fontSize = 13.sp)
        }
    }
}
