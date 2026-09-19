package com.lucent.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.BackHeader
import com.lucent.app.ui.DesktopIntegrationRows
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.SettingsRoute
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.launch

/**
 * P1-3 — extracted from the `EditorPage` local composable that used to live inside
 * `SettingsScreen`. Diffing the two originals turned up one difference beyond Markdown/rich
 * text/links/open-links (identical on both platforms): desktop has two extra rows, "close to
 * tray" and "start with Windows", right after rich text and before Links. That pair is now the
 * [DesktopIntegrationRows] seam (desktop: the two rows and their own trailing gaps; Android:
 * nothing) — placed exactly where desktop's version had it, so on Android this call simply
 * contributes nothing and Links ends up directly after rich text, same as before the split.
 *
 * [onRequestOpenLinksWarning] is a write-only trigger for the `OpenLinksWarningDialog`, which
 * lives in, and stays in, [SettingsScreen] (`showOpenLinksWarning`) — same reasoning as
 * `onRequestSmallModelWarning` on the Memory page.
 */
@Composable
internal fun EditorSettingsPage(
    repo: SettingsRepository,
    onRequestOpenLinksWarning: () -> Unit,
    onRoute: (SettingsRoute) -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val scope = rememberCoroutineScope()
    val markdownEnabled by repo.markdownEnabled.collectAsState(initial = false)
    val richTextEnabled by repo.richTextEnabled.collectAsState(initial = false)
    val linksEnabled by repo.linksEnabled.collectAsState(initial = false)
    val blackoutOn by repo.blackoutEnabled.collectAsState(initial = false)
    val openLinksExternallyOn by repo.openLinksExternally.collectAsState(initial = false)

    BackHeader(S.settingsEditorTitle) { onRoute(SettingsRoute.Root) }
    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.markdownFormattingTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = markdownEnabled,
                onCheckedChange = { checked -> scope.launch { repo.setMarkdownEnabled(checked) } }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // ---- INTEGRATION: rich text (C-group task 20) ----
        // Placed directly under Markdown because the two are alternatives, and adjacency is
        // how a settings page says "pick one of these" without a radio group. The exclusivity
        // is enforced in SettingsRepository rather than here, so it holds no matter which
        // surface flips the flag.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.richTextTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = richTextEnabled,
                onCheckedChange = { checked -> scope.launch { repo.setRichTextEnabled(checked) } }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // ---- PHASE 3 (C-3): desktop citizenship — tray + startup ----
        // Desktop-only rows; the Android build has AlarmManager and needs neither. See
        // [DesktopIntegrationRows].
        DesktopIntegrationRows(repo)
        // Links is a fully independent switch now (task 8). It used to be a sub-toggle:
        // greyed out and forced off whenever Markdown was off, on the theory that links
        // are a Markdown feature. They aren't. Markdown decides whether text is
        // *formatted*; links decide whether notes are *connected*. Anyone who wanted to
        // see their text exactly as typed was made to give up their note graph as well —
        // every [[link]] they had written went dead, and the switch that would have
        // fixed it was greyed out with no explanation.
        //
        // All four combinations are now real and behave sensibly, plain-text-with-links
        // included (see ui/Markdown.kt, LinkedPlainText).
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.linksTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = linksEnabled,
                onCheckedChange = { checked -> scope.launch { repo.setLinksEnabled(checked) } }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // ---- Task 9: "open web links in your browser" moves here from Privacy ----
        //
        // It was filed under Privacy on the reasoning that the switch decides whether a tap
        // hands an address to another app. True, but it is not how anyone looks for it. The
        // switch turns text in the editor into something you can tap, it belongs to the same
        // family as Markdown, rich text and [[links]] directly above it, and every one of
        // those four answers the same question: what does typing this into a note do? A user
        // who has just turned Links on and wants http:// addresses to work too should find
        // that here, not two pages away under a heading about what leaves the device.
        //
        // The privacy consequence has not been swept under the carpet — it is still stated
        // in full, and turning the switch ON still opens the same consent dialog
        // ([OpenLinksWarningDialog]) with the same argument for and against. Blackout Mode
        // still outranks it, and the switch still says so.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S.openLinksTitle, color = onGradient)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = openLinksExternallyOn,
                // Blackout overrides this outright, so while it is on the switch is shown
                // but inert — the frozen-UI treatment the rest of the app uses for a
                // control that a higher-ranking setting has taken over.
                enabled = !blackoutOn,
                onCheckedChange = { turnOn ->
                    if (turnOn) onRequestOpenLinksWarning()
                    else scope.launch { repo.setOpenLinksExternally(false) }
                }
            )
        }
        if (blackoutOn) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(S.openLinksBlockedByBlackout, color = onGradientMuted, fontSize = 12.sp)
        }
    }
}
