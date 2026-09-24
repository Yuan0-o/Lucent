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
import com.lucent.app.data.SettingsCache
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
    val savedLanguage by repo.appLanguage.collectAsState(initial = SettingsCache.appLanguage)
    val savedFont by repo.font.collectAsState(initial = SettingsCache.font)

    BackHeader(S.settingsLanguageTitle) { onRoute(SettingsRoute.Root) }


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
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Text(S.settingsFontTitle, color = onGradient, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(S.settingsFontSub, color = onGradientMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))

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
                scale = 0.5f,
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
