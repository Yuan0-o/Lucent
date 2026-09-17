package com.lucent.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.AppLock
import com.lucent.app.data.AttachmentLimits
import com.lucent.app.data.BackupManager
import com.lucent.app.data.FontStore
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.ShareIntegration
import com.lucent.app.data.StartupLog
import com.lucent.app.i18n.S
import com.lucent.app.local.LocalLlm
import com.lucent.app.local.LocalModelStore
import com.lucent.app.ui.settings.ApiSettingsPage
import com.lucent.app.ui.settings.AppearanceSettingsPage
import com.lucent.app.ui.settings.AssistantSettingsPage
import com.lucent.app.ui.settings.BackgroundSettingsPage
import com.lucent.app.ui.settings.DataSettingsPage
import com.lucent.app.ui.settings.EditorSettingsPage
import com.lucent.app.ui.settings.LanguageSettingsPage
import com.lucent.app.ui.settings.LocalModelSettingsPage
import com.lucent.app.ui.settings.MemorySettingsPage
import com.lucent.app.ui.settings.NetworkSettingsPage
import com.lucent.app.ui.settings.PersonalizationSettingsPage
import com.lucent.app.ui.settings.PrivacySettingsPage
import com.lucent.app.ui.settings.RootSettingsPage
import com.lucent.app.ui.settings.SecuritySettingsPage
import com.lucent.app.ui.settings.ThemeSettingsPage
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.text.style.TextAlign

// Memory and Web are recombined into a single "Memory & web" page (task 10 of the earlier round),
// reached via the Memory route; there is no separate Web route.
//
// Security and Privacy used to be one page. They are now two (task 10): *Security* is about keeping
// other people out of your data — today that is the app lock — while *Privacy* is about what leaves
// this device, or is recorded on it, at all: the share-sheet surface and local diagnostic logging.
// Those are different questions asked by different worries, and a single page called "Security and
// Privacy" answered neither of them clearly. Splitting them also gives each page room to grow
// without becoming the drawer where every remaining switch is kept.
//
// Two routes arrived with the localization / local-model round:
//   - Language: the in-app UI language picker (system / en / zh / ja / ko). It sits directly after
//     Appearance at the root, because "what language is this in" is the same kind of question as
//     "what does this look like".
//   - LocalModel: the on-device GGUF assistant — import, enable, inspect, delete. It lives under
//     Assistant beside API and Memory, because it IS an assistant backend: the fourth answer to
//     "where do replies come from".
internal enum class SettingsRoute { Root, Language, Assistant, Personalization, Memory, Network, Api, LocalModel, Appearance, Theme, Background, Editor, Cloud, Security, Privacy, Data }

/** Which kind of item the selective Markdown-export picker is currently choosing. */
internal enum class ExportKind { NOTES, TASKS }

/** Sentinel distinguishing "wrong password, try again" from "this file is damaged". */
private const val WRONG_PASSWORD = "__wrong_password__"

// ModalBottomSheet (the post-restore result sheet) is still experimental in Material 3.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(active: Boolean = true) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    val savedUrl by repo.baseUrl.collectAsState(initial = "")
    val savedSpec by repo.apiSpec.collectAsState(initial = "openai")
    val savedKey by repo.apiKey.collectAsState(initial = "")
    val savedModel by repo.model.collectAsState(initial = "")
    val savedFont by repo.font.collectAsState(initial = "system")
    val savedAssistantName by repo.assistantName.collectAsState(initial = "Lucent")
    val savedAssistantStyle by repo.assistantStyle.collectAsState(initial = "")
    // One-shot warning shown when small-model mode is switched on (B-group task 4); the switch's
    // own state is read directly from SettingsRepository by MemorySettingsPage now.
    var showSmallModelWarn by remember { mutableStateOf(false) }
    // Whether the assistant answers with the imported on-device model (local-model task).
    val localModelEnabled by repo.localModelEnabled.collectAsState(initial = false)

    // Working copies of the *active* profile's connection fields, used by the API editor page.
    // These mirror the flat saved values; the API page saves through saveApiProfiles (which also
    // re-mirrors them), so they're always in sync with the selected profile.
    var url by remember(savedUrl) { mutableStateOf(savedUrl) }
    var spec by remember(savedSpec) { mutableStateOf(savedSpec) }
    var key by remember(savedKey) { mutableStateOf(savedKey) }
    var selectedModel by remember(savedModel) { mutableStateOf(savedModel) }
    var assistantName by remember(savedAssistantName) { mutableStateOf(savedAssistantName) }
    var assistantStyle by remember(savedAssistantStyle) { mutableStateOf(savedAssistantStyle) }

    // --- Multi-API profiles ---
    val savedProfilesJson by repo.apiProfilesJson.collectAsState(initial = "")
    val savedSelectedIdx by repo.apiProfileSelected.collectAsState(initial = 0)
    // Parsed profile list. If nothing's been saved yet we seed a single profile from the existing
    // flat connection values, so users upgrading from the single-API version keep their config.
    //
    // "No profiles" and "no profiles yet" are different states, and telling them apart is what makes
    // deleting the last API actually possible (task 6). The stored JSON is the discriminator:
    //
    //   blank   -> nothing was ever saved. This is an install upgrading from the single-API version,
    //              so one profile is seeded from the legacy flat connection values and the user's
    //              existing configuration survives the upgrade untouched.
    //   "[]"    -> a list WAS saved and it is empty: the user deleted their last API on purpose.
    //              Re-seeding a blank "API 1" here is what used to make that deletion impossible —
    //              the row reappeared instantly and the delete looked like it had failed.
    val profiles = remember(savedProfilesJson, savedUrl, savedSpec, savedKey, savedModel) {
        val parsed = com.lucent.app.data.ApiProfiles.parse(savedProfilesJson)
        when {
            parsed.isNotEmpty() -> parsed
            savedProfilesJson.isNotBlank() -> emptyList()
            else -> listOf(
                com.lucent.app.data.ApiProfile(
                    name = "API 1", spec = savedSpec, baseUrl = savedUrl, apiKey = savedKey, model = savedModel
                )
            )
        }
    }
    val selectedProfileIdx = savedSelectedIdx.coerceIn(0, (profiles.size - 1).coerceAtLeast(0))

    var models by remember { mutableStateOf(listOf<String>()) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf("") }

    // --- Privacy toggles: App Lock (task 2), System integration (task 6), Startup logging (task 15) ---
    val appLockOn by repo.appLockEnabled.collectAsState(initial = false)

    // ---- C-group tasks 1, 3, 6, 18 ----
    val pwFirstRound by repo.pwFirstRoundLimit.collectAsState(
        initial = com.lucent.app.data.PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT
    )
    val pwLaterRound by repo.pwLaterRoundLimit.collectAsState(
        initial = com.lucent.app.data.PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT
    )
    val selfDestructOn by repo.pwSelfDestructEnabled.collectAsState(initial = false)
    val selfDestructThreshold by repo.pwSelfDestructThreshold.collectAsState(
        initial = com.lucent.app.data.PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD
    )

    // =========================================================================
    // Unified credential gate for this screen's password prompts (task 18/5B)
    // =========================================================================
    //
    // Every Settings dialog that VERIFIES a credential - turning the App Lock off, the
    // danger-zone auth gate, the hidden-area unlock, and the backup-restore password -
    // charges the SAME persisted counter as the lock screen (PasswordAttempts: one counter,
    // not one per screen). Wrong guesses announce the attempts left this round; a closed round
    // disables the field behind a live mm:ss countdown; and when the optional self-destruct is
    // enabled and its lifetime threshold is reached, the wipe (AppWipe.wipeAllData) runs and the
    // app reopens empty and unlocked. A correct app password anywhere calls registerSuccess,
    // which resets the whole counter.
    val gateAttemptJson by repo.passwordAttemptState.collectAsState(initial = "")
    val gateAttempt = remember(gateAttemptJson) {
        com.lucent.app.data.PasswordAttempts.State.fromJson(gateAttemptJson)
    }
    var gateTick by remember { mutableStateOf(0L) }
    val gateLockedMs = remember(gateAttempt, gateTick) {
        com.lucent.app.data.PasswordAttempts.remainingLockoutMs(gateAttempt)
    }
    val gateLockedOut = gateLockedMs > 0L
    LaunchedEffect(gateLockedOut) {
        while (gateLockedOut) {
            kotlinx.coroutines.delay(1000L)
            gateTick++
        }
    }
    var gateFailedOnce by remember { mutableStateOf(false) }
    var gateWiping by remember { mutableStateOf(false) }

    /** The optional self-destruct from a Settings prompt (same wipe as the lock screen). */
    fun runSettingsGateWipe() {
        if (gateWiping) return
        gateWiping = true
        scope.launch {
            val wiped = runCatching {
                com.lucent.app.data.wipeAllData(
                    context.applicationContext,
                    com.lucent.app.data.AppDatabase.getInstance(context.applicationContext),
                    repo)
            }.isSuccess
            if (!wiped) {
                gateWiping = false
                return@launch
            }
            repo.setAppLock(enabled = false, credentialsJson = "")
            repo.setPasswordAttemptState(com.lucent.app.data.PasswordAttempts.registerSuccess().toJson())
            AppLockController.enabled = false
            AppLockController.unlock()
            LucentToast.show(context, S.allDataClearedToast)
        }
    }

    /** A correct credential anywhere on this screen resets the whole shared counter. */
    fun settingsGateSuccess() {
        gateFailedOnce = false
        scope.launch {
            repo.setPasswordAttemptState(com.lucent.app.data.PasswordAttempts.registerSuccess().toJson())
        }
    }

    /** Charge one wrong credential guess on the shared counter. */
    fun chargeSettingsGate() {
        if (gateLockedOut || gateWiping) return
        val next = com.lucent.app.data.PasswordAttempts.registerFailure(
            gateAttempt, pwFirstRound, pwLaterRound
        )
        gateFailedOnce = true
        if (com.lucent.app.data.PasswordAttempts.shouldSelfDestruct(
                next, selfDestructOn, selfDestructThreshold
            )
        ) {
            runSettingsGateWipe()
            return
        }
        scope.launch { repo.setPasswordAttemptState(next.toJson()) }
    }

    /** Shared feedback under a gated password field: countdown / attempts-left / proximity warn. */
    @Composable
    fun SettingsGateFeedback() {
        if (selfDestructOn && !gateLockedOut && !gateWiping &&
            com.lucent.app.data.PasswordAttempts.failuresBeforeSelfDestruct(
                gateAttempt, selfDestructThreshold
            ) in 1..5
        ) {
            Text(S.selfDestructNear, color = Color(0xFFFFB74D), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (gateLockedOut) {
            Text(
                S.lockTryAgainIn(com.lucent.app.data.PasswordAttempts.formatRemaining(gateLockedMs)),
                color = Color(0xFFFF8A80),
                fontSize = 13.sp
            )
        } else if (gateFailedOnce) {
            val left = com.lucent.app.data.PasswordAttempts.attemptsRemaining(
                gateAttempt, pwFirstRound, pwLaterRound
            )
            if (left > 0) {
                Text(S.lockAttemptsLeft(left), color = Color(0xFFFFB74D), fontSize = 13.sp)
            }
        }
    }

    // Confirmation dialogs for the three switches that change what the app is allowed to do.
    var showBlackoutWarning by remember { mutableStateOf(false) }
    var showCrashShieldInfo by remember { mutableStateOf(false) }
    var showOpenLinksWarning by remember { mutableStateOf(false) }
    // Self-destruct is the only irreversible switch in the app, so it is gated by a TYPED phrase
    // rather than a tap: a destructive action reached by muscle memory is a destructive action
    // taken by accident.
    var showSelfDestructWarning by remember { mutableStateOf(false) }
    var selfDestructTyped by remember { mutableStateOf("") }
    // Result of the on-demand at-rest encryption self-check (task 17). Null = not run yet.
    var encryptionCheckResult by remember { mutableStateOf<String?>(null) }
    // Task 3.1 — the self-check result used to stay on screen forever. It is the answer to a
    // question the user asked by pressing a button one second ago, not a property of the page, and
    // once read it is just a line of stale text sitting under a control that now looks like it did
    // something permanent. Clear it after a few seconds, exactly like a toast.
    //
    // A FAILURE is deliberately left up: that one is not "here is your answer", it is "something is
    // wrong", and it should stay until the user leaves the page.
    LaunchedEffect(encryptionCheckResult) {
        if (encryptionCheckResult == "") {
            kotlinx.coroutines.delay(6000)
            encryptionCheckResult = null
        }
    }
    // Same rule for the backup status line, which had the same problem for the same reason.
    LaunchedEffect(backupStatus) {
        if (backupStatus.isNotBlank()) {
            kotlinx.coroutines.delay(8000)
            backupStatus = ""
        }
    }

    // App Lock setup dialog (only shown while turning the lock ON, to capture the credentials).
    var showAppLockSetup by remember { mutableStateOf(false) }
    var lockPw by remember { mutableStateOf("") }
    var lockPwConfirm by remember { mutableStateOf("") }
    var lockQuestion by remember { mutableStateOf("") }
    var lockAnswer by remember { mutableStateOf("") }
    var lockSetupError by remember { mutableStateOf("") }

    // Shown when the user tries to turn the lock on with no security question (task 9).
    var showNoRecoveryWarning by remember { mutableStateOf(false) }

    // Turning the lock OFF now also requires the password (task): a dialog confirms the user knows
    // it before the protection is removed, and spells out the security risk of removing it. The
    // credentials blob is collected here so the dialog can verify what's typed against it.
    val appLockCreds by repo.appLockCredentials.collectAsState(initial = "")
    var showAppLockDisable by remember { mutableStateOf(false) }
    var disablePw by remember { mutableStateOf("") }
    var disableError by remember { mutableStateOf("") }

    // System-integration privacy warning (shown before enabling the share/intent surface).
    var showShareWarning by remember { mutableStateOf(false) }

    // Writes the local diagnostic log to a user-chosen text file (task 15). Reading is off the main
    // thread; the log lives in internal storage and is only ever copied out by this explicit action.
    val logsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(StartupLog.buildExport(context).toByteArray())
                        } != null
                    } catch (e: Exception) {
                        false
                    }
                }
                backupStatus = if (ok) S.logsExported else S.logsExportFailed
            }
        }
    }

    // --- Backup encryption ---
    var showExportDialog by remember { mutableStateOf(false) }
    // Starts BLANK every time (task 5). It used to be pre-seeded from the last-used password, which —
    // combined with the old password-first default — meant a user who set a password once kept
    // silently exporting password-locked files, and those files then failed to restore on any other
    // device that didn't have the password. Blank means the default export uses the portable
    // built-in key; a password is only applied if the user deliberately types one this time.
    var exportPasswordDraft by remember { mutableStateOf("") }
    // Which sections the next export writes (task 9). Defaults to everything except the model
    // files, which is byte-for-byte what an export produced before this was selectable — so the
    // common case is unchanged and the choice only costs anyone who wants it.
    var exportModules by remember { mutableStateOf(BackupManager.DEFAULT_MODULES) }
    // Per-item selection (second-level menu). Null means "everything in that module", which is the
    // default and what every previous release did; a non-null set is an explicit subset the user
    // built by hand. Kept null until they actually open the sub-menu, so the common path never pays
    // to enumerate every note and task.
    var exportNoteIds by remember { mutableStateOf<Set<Long>?>(null) }
    var exportTaskIds by remember { mutableStateOf<Set<Long>?>(null) }
    // Same per-item shape for the two sections that gained it in task F1: which chat conversations
    // and which API profiles the export includes. Null = everything in that module (the default);
    // API profiles are held by NAME (the label the user picks, stable across reordering), the same
    // handle BackupManager filters on.
    var exportConversationIds by remember { mutableStateOf<Set<Long>?>(null) }
    var exportApiProfileNames by remember { mutableStateOf<Set<String>?>(null) }
    // The REAL saved API profiles — not the synthetic single profile the API page shows when nothing
    // has been saved yet (see `profiles` above). The per-profile export picker is built from and
    // gated on this list, so its indices line up exactly with what BackupManager re-parses from the
    // same stored JSON. When it's empty (a fresh install still on the legacy flat keys), no API
    // drill-in is offered and the whole-API toggle is the only choice — which is the honest option
    // when there is only one connection to include or leave out.
    val realProfiles = remember(savedProfilesJson) { com.lucent.app.data.ApiProfiles.parse(savedProfilesJson) }
    // Which second-level picker is open, if any.
    var itemPicker by remember { mutableStateOf<ExportItemKind?>(null) }
    // The item lists behind the second-level picker. Loaded only while the export dialog is open:
    // reading every note and task is cheap, but doing it on a Settings screen nobody is exporting
    // from is work for nothing, and on a large database it is work for nothing on every recomposition.
    var allNotes by remember { mutableStateOf<List<com.lucent.app.data.Note>>(emptyList()) }
    var allTasks by remember { mutableStateOf<List<com.lucent.app.data.Task>>(emptyList()) }
    // Conversations behind the chat picker, loaded (like notes/tasks) only while the export dialog is
    // open. API profiles need no load here — realProfiles above already comes from settings.
    var allConversations by remember {
        mutableStateOf<List<com.lucent.app.data.ChatConversation>>(emptyList())
    }
    LaunchedEffect(showExportDialog) {
        if (showExportDialog) {
            val loadedNotes = withContext(Dispatchers.IO) { db.noteDao().getAllOnce() }
            val loadedTasks = withContext(Dispatchers.IO) { db.taskDao().getAllOnce() }
            val loadedConversations = withContext(Dispatchers.IO) { db.chatConversationDao().getAllOnce() }
            allNotes = loadedNotes
            allTasks = loadedTasks
            allConversations = loadedConversations
        }
    }
    // Which sections a restore applies. Populated from the file's own contents when the preview
    // opens, so the list offers what is actually in the file rather than a fixed menu of maybes.
    var restoreModules by remember { mutableStateOf(BackupManager.DEFAULT_MODULES) }
    // Per-item restore choices (task F2), the import-side mirror of the export selection. Reset when a
    // new preview opens (see the ImportPreviewDialog). Chats by conversation id, API by name — the
    // handles the preview's own lists carry.
    var restoreConversationIds by remember { mutableStateOf<Set<Long>?>(null) }
    var restoreApiProfileNames by remember { mutableStateOf<Set<String>?>(null) }
    var restoreItemPicker by remember { mutableStateOf<ExportItemKind?>(null) }
    // Set when confirming a restore whose API profiles would push this device over ApiProfiles.MAX.
    // Drives the API-limit prompt (below), which asks which of the incoming profiles to keep before
    // any of them is written — the alternative, silently dropping the overflow, is never done.
    var apiLimitPrompt by remember { mutableStateOf(false) }
    var exportPasswordVisible by remember { mutableStateOf(false) }

    // The picked backup, held as its SAF Uri and re-opened for each of the import flow's two
    // streaming passes (inspect, then commit).
    //
    // History of this field, because each shape fixed a real failure. Originally the whole file
    // was read into a ByteArray — and a backup carrying local model files is gigabytes, so that
    // was an instant OutOfMemoryError, an Error the catch blocks never saw: the app simply
    // vanished on import. The first fix staged the pick into a cache file, which cured the crash
    // but still cost a full extra copy — for a 16 GB model backup that is 16 GB of scratch space
    // and minutes of flash I/O before the preview could even appear. So now the Uri itself is the
    // source: BackupManager streams both passes straight from the provider, and the only bytes
    // ever written are the restored payloads themselves. The old worry about the picker's read
    // grant expiring mid-flow is answered by taking a persistable read permission at pick time
    // (released again on every exit below); even where a provider refuses persistence, the
    // ordinary grant outlives this screen's inspect -> confirm -> commit sequence.
    var importSourceUri by remember { mutableStateOf<Uri?>(null) }
    // Whether the password prompt (step 2) is up for the picked backup.
    var importPasswordPrompt by remember { mutableStateOf(false) }
    var importPasswordDraft by remember { mutableStateOf("") }
    var importPasswordError by remember { mutableStateOf(false) }
    // What's in the file, worked out without writing anything. Non-null means the confirm step is up.
    var importPreview by remember { mutableStateOf<BackupManager.BackupPreview?>(null) }
    // Outcome of the last finished restore, surfaced as a bottom sheet the moment commit returns.
    // first = whether the commit succeeded (which picks the sheet's title), second = the same
    // summary / failure text that also goes to the Data page's status line. Null = no sheet shown.
    var importResultSheet by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    // The long-running backup operation currently in flight, if any: the label the modal
    // progress dialog shows (null = no dialog up). While non-null the dialog blocks every
    // other interaction, which is what keeps a second operation — or any other data action —
    // from starting mid-stream.
    var backupBusyLabel by remember { mutableStateOf<String?>(null) }
    // The coroutine doing that work, so the dialog's Cancel button can cancel it cooperatively.
    var backupOpJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // A re-openable source over [uri] for BackupManager's streaming passes. Opening can fail if
    // the grant is somehow gone; throwing (rather than returning an empty stream) lets inspect
    // surface it as a readable message and lets commit's best-effort blob pass skip cleanly.
    fun uriSource(uri: Uri) = BackupManager.BackupSource {
        context.contentResolver.openInputStream(uri)
            ?: throw java.io.FileNotFoundException("Backup could not be opened")
    }
    // Let go of the persistable read grant taken at pick time. Persisted grants are a bounded
    // system resource, so they are held exactly as long as the flow needs them and no longer.
    fun releaseImportGrant(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Throwable) {
            // Never taken (provider refused persistence) or already gone — nothing to release.
        }
    }
    // Close the import flow's file state. Called on every exit from the flow — cancel, a failed
    // inspect, or the launcher replacing a stale pick. The one path that must NOT call this is a
    // running restore: commit is still streaming from the Uri, so runRestore below does its own
    // cleanup after commit returns.
    fun discardImportSource() {
        importSourceUri?.let { releaseImportGrant(it) }
        importSourceUri = null
        importPasswordPrompt = false
    }
    // The actual restore + its post-restore housekeeping, in one place so the confirm button and the
    // API-limit prompt drive identical behaviour; only the API profile selection differs between them.
    // Passing a non-null (possibly empty) profile-name set makes the API restore MERGE into this
    // device's profiles; passing null keeps the legacy whole-API replace (used for old single-API
    // backups that carry only the flat connection keys).
    val runRestore: (BackupManager.BackupPreview, Set<BackupManager.BackupModule>, Set<Long>?, Set<String>?) -> Unit =
        { preview, modules, convIds, apiNames ->
            importPreview = null
            apiLimitPrompt = false
            // Capture the Uri before launching: commit's second streaming pass reads the
            // model/font blobs straight from it (see BackupManager — the preview no longer carries
            // a decrypted payload, which is what used to pin gigabytes of model file in memory and
            // kill the import with an OutOfMemoryError the catch below could never see).
            val sourceUri = importSourceUri
            val job = scope.launch {
                backupBusyLabel = S.importingBackup
                // Whether commit itself returned (true) or threw (false), tracked separately so
                // the result sheet below can title itself without parsing the message text.
                var committed = true
                // The outcome, recorded the instant commit produces one. A Cancel that lands after
                // the database phase has begun does not abort that phase (it is shielded — see
                // BackupManager.commit), but it still cancels this coroutine at the next suspension
                // point; recording the outcome here is how the true result survives to be reported
                // instead of a cancellation that never actually happened.
                var outcome: String? = null
                try {
                    withContext(Dispatchers.IO) {
                        outcome = try {
                            BackupManager.commit(
                                context, db, repo, preview, modules, convIds, apiNames,
                                source = sourceUri?.let { uriSource(it) }
                            )
                        } catch (t: kotlinx.coroutines.CancellationException) {
                            // A genuine cancel inside the cancellable blob phase — let it fly.
                            throw t
                        } catch (t: Throwable) {
                            // Throwable, not Exception: the failure this flow historically died of
                            // was an OutOfMemoryError, which is an Error. Streaming makes that
                            // unlikely, but if anything of the kind ever recurs it must surface as
                            // a message, not as the app vanishing.
                            committed = false
                            S.importFailed(t.message ?: "")
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Cancel pressed while the blob phase was still streaming: nothing further
                    // ran. The status line reports it; the completion sheet stays reserved for
                    // restores that actually finished.
                    if (outcome == null) backupStatus = S.importCancelledStatus
                } finally {
                    outcome?.let { result ->
                        backupStatus = result
                        // Announce the outcome in a bottom sheet as well. The status line above keeps the
                        // same text for the rest of THIS visit to the Data page, but it is cleared the next
                        // time the page is entered (see the LaunchedEffect(route) further down) — so the
                        // sheet is the moment-of-completion announcement, and the line is only same-visit
                        // context.
                        importResultSheet = committed to result
                        // A restored backup can bring in tasks that want reminders, and alarms aren't part of
                        // a backup file — they're OS state. BackupManager re-arms them, but the channel has to
                        // exist before one can be posted.
                        com.lucent.app.reminders.Notifications.ensureChannel(context)
                        // If a database had been set aside as undecryptable, restoring is the cure — retire
                        // the notice rather than leaving it frightening someone who has already fixed it.
                        com.lucent.app.data.DatabaseEncryption.clearLockedNotice(context)
                    }
                    // The flow is over — done, failed, or cancelled; the grant has served its
                    // purpose either way, and the modal comes down.
                    importSourceUri = null
                    importPasswordPrompt = false
                    sourceUri?.let { releaseImportGrant(it) }
                    backupBusyLabel = null
                    backupOpJob = null
                }
            }
            backupOpJob = job
        }
    // Set when a database could not be decrypted on this launch. Nothing was deleted — see
    // DatabaseEncryption.setAside — but the user has to be told, and told what to do about it.
    val lockedNotice = remember { com.lucent.app.data.DatabaseEncryption.lockedNotice(context) }
    var lockedDismissed by remember { mutableStateOf(false) }
    // The notice is *persisted at fault time* (see DatabaseEncryption.setAside), so an old marker
    // may be in English while the UI is not. The set-aside file name is the only variable part and
    // is always written inside double quotes, so it is extracted here and re-rendered through the
    // catalog; if extraction ever fails (foreign/edited marker), the stored text is shown verbatim
    // rather than nothing — a recovery notice must never be lost to a formatting quibble.
    val lockedNoticeFileName = remember(lockedNotice) {
        lockedNotice?.let { Regex("\"([^\"]+)\"").find(it)?.groupValues?.getOrNull(1) }
    }
    var showClearData by remember { mutableStateOf(false) }
    var showClearNotes by remember { mutableStateOf(false) }
    var showClearTasks by remember { mutableStateOf(false) }
    var showClearChats by remember { mutableStateOf(false) }
    // The API profile the user has asked to delete, held until they confirm (task 1). Deleting an
    // API key is destructive — it can't be undone and the key may be the only copy — so it now goes
    // through an explicit confirmation instead of firing on the first tap of the trash icon.
    var profilePendingDelete by remember { mutableStateOf<Int?>(null) }
    // Name of the profile being edited on the API page (editable so users can rename).
    var editingProfileName by remember(savedProfilesJson, selectedProfileIdx) {
        mutableStateOf(profiles.getOrNull(selectedProfileIdx)?.name ?: "")
    }

    // --- Local model (GGUF) page state (local-model task) ---
    //
    // lmRefresh is a change counter: bumping it makes the remember()s below re-read the store, so
    // the page reflects an import/delete/rename/switch immediately without any second source of truth.
    var lmRefresh by remember { mutableStateOf(0) }
    val lmIndex = remember(lmRefresh) { LocalModelStore.index(context) }
    val lmModels = lmIndex.slots
    val lmActiveId = lmIndex.activeId
    val lmCanImportMore = lmModels.size < LocalModelStore.MAX_MODELS
    var lmImporting by remember { mutableStateOf(false) }
    var lmError by remember { mutableStateOf("") }
    // The slot the user has asked to delete, held until they confirm. Deleting always asks first.
    var lmSlotPendingDelete by remember { mutableStateOf<LocalModelStore.ModelSlot?>(null) }
    // The slot being renamed and the working text (null = the rename dialog is closed).
    var lmRenameTarget by remember { mutableStateOf<LocalModelStore.ModelSlot?>(null) }
    var lmRenameText by remember { mutableStateOf("") }
    // A just-picked model file awaiting a name before it is imported (null = no naming dialog up).
    // Holding the Uri lets the user label the model at import time (custom names, task requirement).
    var lmPendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var lmImportName by remember { mutableStateOf("") }
    // Warning dialogs for the opt-in local-model switches (use-local, tools, GPU). Turning any ON
    // asks first; turning OFF is free (back to the safe default), so only the "on" path is gated.
    var lmConfirmToolsOn by remember { mutableStateOf(false) }
    var lmConfirmGpuOn by remember { mutableStateOf(false) }
    // Turning "use local model" ON freezes the cloud API and pulls a multi-gigabyte model into RAM,
    // so it warns first (API frozen, memory cost, don't quit mid-reply, quitting frees the memory).
    var lmConfirmUseLocalOn by remember { mutableStateOf(false) }
    // Letting a reply run on in the background keeps a multi-gigabyte model resident while the user
    // is somewhere else entirely, so switching it ON warns first (task 2). Off is always immediate.
    var lmConfirmBackgroundOn by remember { mutableStateOf(false) }

    // The GGUF picker. OpenDocument with * / * because .gguf has no registered MIME type and a
    // model downloaded as a .zip must be pickable too; LocalModelStore validates the actual bytes
    // (GGUF magic, or a zip containing a .gguf) and rejects everything else with a clear message.
    // Picking doesn't import straight away: it stages the Uri and opens a naming dialog first, so the
    // user can label the model (custom names, task requirement). The import runs on confirm.
    val lmImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !lmImporting) {
            lmError = ""
            // Default the name to the picked file's name (minus extension); the user can edit it.
            val picked = try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (_: Throwable) { null }
            lmImportName = (picked ?: "").substringBeforeLast('.').take(60)
            lmPendingImportUri = uri
        }
    }


    // PHASE 4 — multimodal projector (mmproj) import for the ACTIVE slot. Unlike the model import
    // there is no naming step: the projector has no user-facing name — it is a property of the
    // model it serves — so the pick imports directly. The resident model is freed first because
    // ensureLoaded attaches the projector at model-load time; the next send reloads the pair.
    val lmMmprojLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val mmprojSlotId = lmActiveId
        if (uri != null && !lmImporting && mmprojSlotId != null) {
            lmError = ""
            scope.launch {
                val error = withContext(Dispatchers.IO) {
                    try {
                        LocalLlm.shutdown()
                        LocalModelStore.importMmproj(context, mmprojSlotId, uri)
                        null
                    } catch (e: LocalModelStore.NotGgufException) {
                        S.lmImportFailedNotGguf
                    } catch (e: Exception) {
                        S.lmImportFailedGeneric(e.message ?: "")
                    }
                }
                if (error == null) {
                    lmRefresh++
                    LucentToast.show(context.applicationContext, S.lmMmprojImported)
                } else {
                    lmError = error
                }
            }
        }
    }

    fun removeMmproj() {
        val mmprojSlotId = lmActiveId ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                LocalLlm.shutdown()
                LocalModelStore.deleteMmproj(context, mmprojSlotId)
            }
            lmRefresh++
        }
    }

    // Run the staged import under the chosen name. Frees the resident model first so the peak
    // footprint stays at one model, then adds the new slot (which becomes active) and refreshes.
    fun startLocalImport(uri: Uri, name: String) {
        if (lmImporting) return
        lmImporting = true
        lmError = ""
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    LocalLlm.shutdown()
                    LocalModelStore.import(context, uri, name)
                    null
                } catch (e: LocalModelStore.TooManyModelsException) {
                    S.lmImportFailedTooMany(LocalModelStore.MAX_MODELS)
                } catch (e: LocalModelStore.NotGgufException) {
                    S.lmImportFailedNotGguf
                } catch (e: LocalModelStore.NoGgufInZipException) {
                    S.lmImportFailedNoGgufInZip
                } catch (e: Exception) {
                    S.lmImportFailedGeneric(e.message ?: "")
                }
            }
            lmImporting = false
            if (error == null) {
                lmRefresh++
                LucentToast.show(context.applicationContext, S.lmImportedToast)
            } else {
                lmError = error
            }
        }
    }

    // Switch the active model. Only one model is ever resident, so the currently loaded one is
    // released immediately; the next send loads the newly selected slot. No-op if already active.
    fun selectLocalModel(id: String) {
        if (id == lmActiveId) return
        scope.launch {
            withContext(Dispatchers.IO) {
                LocalLlm.shutdown()          // free the outgoing model's memory now
                LocalModelStore.setActive(context, id)
            }
            lmRefresh++
        }
    }

    // Delete a model slot. If it is the resident model, the engine is shut down first so a
    // multi-gigabyte model is never left in memory with nothing on disk to reload.
    fun deleteLocalModel(slot: LocalModelStore.ModelSlot) {
        scope.launch {
            val wasActive = slot.id == lmActiveId
            withContext(Dispatchers.IO) {
                if (wasActive) LocalLlm.shutdown()
                LocalModelStore.delete(context, slot.id)
                // If that was the last model, the "use local model" switch would point at nothing,
                // so turn it off — the assistant reverts to the cloud API cleanly.
                if (LocalModelStore.slots(context).isEmpty()) repo.setLocalModelEnabled(false)
            }
            lmRefresh++
            LucentToast.show(context.applicationContext, S.lmDeletedToast)
        }
    }

    // --- Imported font state (font library task) ---
    //
    // Same shape as the local-model state above, at a smaller scale: fontRefresh is a change
    // counter, and bumping it makes the remember() below re-read the store so the picker reflects
    // an import/delete immediately without a second source of truth.
    var fontRefresh by remember { mutableStateOf(0) }
    val importedFonts = remember(fontRefresh) { FontStore.index(context) }.slots
    val fontCanImportMore = importedFonts.size < FontStore.MAX_FONTS
    var fontImporting by remember { mutableStateOf(false) }
    var fontError by remember { mutableStateOf("") }
    // The font the user has asked to delete, held until they confirm. Deleting always asks first.
    var fontPendingDelete by remember { mutableStateOf<FontStore.FontSlot?>(null) }
    // A just-picked font file awaiting a name before it is imported (null = no naming dialog up).
    // Holding the Uri lets the user label the font at import time (custom names, task requirement).
    var fontPendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var fontImportName by remember { mutableStateOf("") }

    // The font picker. OpenDocument with * / * because font MIME types are registered too
    // inconsistently across pickers and sources to be worth filtering on; FontStore validates the
    // actual bytes (TTF/OTF/TTC magic) and rejects everything else with a clear message. Picking
    // doesn't import straight away: it stages the Uri and opens a naming dialog first.
    val fontImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !fontImporting) {
            fontError = ""
            // Default the name to the picked file's name (minus extension); the user can edit it.
            val picked = try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (_: Throwable) { null }
            fontImportName = (picked ?: "").substringBeforeLast('.').take(60)
            fontPendingImportUri = uri
        }
    }

    // Run the staged font import under the chosen name. The new font becomes the app font right
    // away — it is the one the user just added, and the immediate whole-app change doubles as the
    // clearest possible confirmation that the import worked.
    fun startFontImport(uri: Uri, name: String) {
        if (fontImporting) return
        fontImporting = true
        fontError = ""
        scope.launch {
            var importedId: String? = null
            val error = withContext(Dispatchers.IO) {
                try {
                    importedId = FontStore.import(context, uri, name).id
                    null
                } catch (e: FontStore.TooManyFontsException) {
                    S.fontImportFailedTooMany(FontStore.MAX_FONTS)
                } catch (e: FontStore.NotFontException) {
                    S.fontImportFailedNotFont
                } catch (e: Exception) {
                    S.fontImportFailedGeneric(e.message ?: "")
                }
            }
            fontImporting = false
            if (error == null) {
                importedId?.let { id -> AppScope.io.launch { repo.setFont(id) } }
                fontRefresh++
                LucentToast.show(context.applicationContext, S.fontImportedToast)
            } else {
                fontError = error
            }
        }
    }

    // Delete an imported font. If it is the selected font, the preference falls back to the
    // system font FIRST, so no frame is ever asked to render from a family whose file is gone;
    // the cached FontFamily is dropped for the same reason.
    fun deleteImportedFont(slot: FontStore.FontSlot) {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (savedFont == slot.id) repo.setFont(SYSTEM_FONT_KEY)
                FontStore.delete(context, slot.id)
            }
            LucentFontResolver.evict(slot.id)
            fontRefresh++
        }
    }

    var route by rememberSaveable { mutableStateOf(SettingsRoute.Root) }

    // --- Unsaved-changes guard for the Personalization sub-screen ---
    // Only personalization (name + chat style) can go "dirty"; the API/Appearance/Data pages save
    // each action immediately (an explicit button press), so they never need a guard.
    val assistantDirty = route == SettingsRoute.Personalization && (
        assistantName.ifBlank { "Lucent" } != savedAssistantName ||
            assistantStyle != savedAssistantStyle
        )
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val appContext = context.applicationContext
    fun persistAssistantSettings() {
        // App-lifetime scope: the unsaved-changes dialog saves and then leaves the screen in the
        // same action, which would otherwise cancel this write before it commits.
        AppScope.io.launch {
            repo.setAssistantName(assistantName.ifBlank { "Lucent" })
            repo.setAssistantStyle(assistantStyle)
            withContext(Dispatchers.Main) {
                LucentToast.show(appContext, S.savedToast)
            }
        }
    }

    fun discardAssistantSettings() {
        assistantName = savedAssistantName
        assistantStyle = savedAssistantStyle
    }

    // Saves the currently-edited connection fields into profile [idx] (replacing it) and makes it
    // active. Used by the API editor's Save button. Runs on the app-lifetime scope for the same
    // reason as above.
    //
    // The persisted fallback name deliberately stays the English "API N" pattern:
    // ApiProfiles.nextDefaultName generates the same pattern at the data layer, and a stored name
    // should not depend on which language happened to be active the moment Save was pressed.
    // Display-side fallbacks (list rows, the delete dialog) ARE localized.
    fun saveActiveProfile(idx: Int, activate: Boolean = true) {
        val updated = profiles.toMutableList()
        val edited = com.lucent.app.data.ApiProfile(
            name = editingProfileName.trim().ifBlank { "API ${idx + 1}" },
            spec = spec,
            baseUrl = url.trim(),
            apiKey = key.trim(),
            model = selectedModel
        )
        if (idx in updated.indices) updated[idx] = edited else updated.add(edited)
        val newSelected = if (activate) idx.coerceIn(0, updated.size - 1) else selectedProfileIdx
        AppScope.io.launch {
            repo.saveApiProfiles(updated, newSelected)
            withContext(Dispatchers.Main) { LucentToast.show(appContext, S.apiSavedToast) }
        }
    }

    // Switch the active profile to [idx] and load its fields into the editor. Saves immediately.
    fun selectProfile(idx: Int) {
        val p = profiles.getOrNull(idx) ?: return
        url = p.baseUrl; spec = p.spec; key = p.apiKey; selectedModel = p.model
        editingProfileName = p.name
        models = emptyList()
        AppScope.io.launch { repo.saveApiProfiles(profiles, idx) }
    }

    // Add a new empty profile (up to MAX) and start editing it. Its default name is the smallest
    // free "API N" number, so deleting a lower-numbered profile lets that number be reused instead
    // of always climbing (e.g. after deleting "API 1", the next add is "API 1" again, not "API 3").
    fun addProfile() {
        if (profiles.size >= com.lucent.app.data.ApiProfiles.MAX) return
        val defaultName = com.lucent.app.data.ApiProfiles.nextDefaultName(profiles)
        val newList = profiles + com.lucent.app.data.ApiProfile(name = defaultName)
        val newIdx = newList.size - 1
        url = ""; spec = "openai"; key = ""; selectedModel = ""; editingProfileName = defaultName
        models = emptyList()
        AppScope.io.launch { repo.saveApiProfiles(newList, newIdx) }
    }

    // Delete profile [idx] — including the last one, which is now a genuine deletion (task 6).
    //
    // It used to "delete" the final profile by replacing it with a fresh blank "API 1". That was
    // meant to keep the editor usable, and instead produced the one outcome a delete button must
    // never produce: you tapped Delete, confirmed, and a row with the same name was still sitting
    // there. Whether the key had actually been erased was anybody's guess from the outside.
    //
    // Now the list can be empty. The API page shows an explicit empty state instead of an editor
    // bound to nothing, and SettingsRepository.saveApiProfiles clears the mirrored connection keys
    // so the assistant stops using credentials the user just removed.
    fun deleteProfile(idx: Int) {
        if (idx !in profiles.indices) return
        val newList = profiles.toMutableList().also { it.removeAt(idx) }
        if (newList.isEmpty()) {
            url = ""; spec = "openai"; key = ""; selectedModel = ""; editingProfileName = ""
            models = emptyList()
        }
        val newSelected = if (newList.isEmpty()) 0 else selectedProfileIdx.coerceIn(0, newList.size - 1)
        AppScope.io.launch { repo.saveApiProfiles(newList, newSelected) }
    }

    // Leaving the Personalization sub-screen (back arrow or system back) while dirty asks first
    // instead of silently discarding. Every other page saves each action immediately, so this
    // guard only ever engages on the Personalization route.
    fun leavePersonalization() {
        if (assistantDirty) showUnsavedDialog = true else route = SettingsRoute.Assistant
    }

    // Where "back" goes from the current sub-route, reflecting the nesting:
    //   Assistant > { Personalization, API, Memory, Network, Local model }
    //   Appearance > { Theme, Background }
    //   Language  > { Font }
    fun goBack() {
        when (route) {
            SettingsRoute.Personalization -> leavePersonalization()
            SettingsRoute.Memory -> route = SettingsRoute.Assistant
            SettingsRoute.Network -> route = SettingsRoute.Assistant
            SettingsRoute.Api -> route = SettingsRoute.Assistant
            SettingsRoute.LocalModel -> route = SettingsRoute.Assistant
            SettingsRoute.Theme, SettingsRoute.Background -> route = SettingsRoute.Appearance
            SettingsRoute.Language, SettingsRoute.Assistant, SettingsRoute.Appearance, SettingsRoute.Editor,
            SettingsRoute.Cloud, SettingsRoute.Security, SettingsRoute.Privacy, SettingsRoute.Data -> route = SettingsRoute.Root
            else -> route = SettingsRoute.Root
        }
    }

    // Leaving Settings folds it back to the root list (task 3). Settings is the clearest case of
    // the problem: its sub-pages are deep (Assistant > API, Appearance > Background) and highly
    // specific, so returning to the tab and landing on the API editor — with no memory of having
    // been there — reads as the app having lost its place rather than helpfully kept it. The
    // Personalization guard has already resolved by the time this runs, so no edit can be lost.
    LaunchedEffect(active) {
        if (!active) route = SettingsRoute.Root
    }

    // A failed local-model import (e.g. "no .gguf in that zip") should not linger. Clearing it when
    // the Local Model page is (re-)entered means the message shows once, for that attempt, and is
    // gone the next time the user opens the page — whether they came back via the tab or the sub-nav.
    LaunchedEffect(route) {
        if (route == SettingsRoute.LocalModel) lmError = ""
        // Same idea for the API page's "fetch models" error: a one-off failure (bad URL, network,
        // empty address) shows once and is gone the next time the page is opened, rather than lingering.
        if (route == SettingsRoute.Api) errorText = ""
        // And for the Data page's status line — the last import summary or export result. It is
        // one-off feedback for the action just performed, but it used to sit there indefinitely
        // once set (the "Imported 0 notes, ..." line that survived every later visit). Clearing it
        // on (re-)entry means it shows for the visit that produced it and is gone the next time
        // the page is opened; the moment-of-completion announcement now lives in the result sheet.
        if (route == SettingsRoute.Data) backupStatus = ""
        // And the Language & type page's font-import error ("that file isn't a font"): it had no
        // such reset, so once set it lingered across every later visit — the exact staleness the
        // three lines above were added to end.
        if (route == SettingsRoute.Language) fontError = ""
    }

    // Registers this screen's dirty state with the app-lifetime guard so switching bottom-nav
    // tabs, or the system back button closing the app, also asks before losing changes here.
    SideEffect {
        if (assistantDirty) {
            UnsavedChangesGuard.register("settings", ::persistAssistantSettings, ::discardAssistantSettings)
        } else {
            UnsavedChangesGuard.clear("settings")
        }
    }
    DisposableEffect(Unit) { onDispose { UnsavedChangesGuard.clear("settings") } }


    // The opt-in warning for small-model mode (B-group task 4). Confirming applies the setting;
    // dismissing leaves the switch as it was, because the switch is driven by the stored value and
    // never by local state, so a cancelled dialog cannot leave the UI out of step with reality.
    if (showSmallModelWarn) {
        AlertDialog(
            onDismissRequest = { showSmallModelWarn = false },
            title = { Text(S.smallModelModeTitle) },
            text = { Text(S.smallModelModeWarn) },
            confirmButton = {
                TextButton(onClick = {
                    showSmallModelWarn = false
                    AppScope.io.launch { repo.setSmallModelModeEnabled(true) }
                }) { Text(S.actionConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { showSmallModelWarn = false }) { Text(S.actionCancel) }
            }
        )
    }

    @Composable
    @NonRestartableComposable
    fun UnsavedChangesDialog() {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(S.unsavedChangesTitle) },
            text = { Text(S.settingsUnsavedBody) },
            confirmButton = {
                TextButton(onClick = {
                    persistAssistantSettings()
                    showUnsavedDialog = false
                    route = SettingsRoute.Assistant
                }) { Text(S.actionSave) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        discardAssistantSettings()
                        showUnsavedDialog = false
                        route = SettingsRoute.Assistant
                    }) { Text(S.actionDiscard) }
                    TextButton(onClick = { showUnsavedDialog = false }) { Text(S.actionCancel) }
                }
            }
        )
    }
    if (showUnsavedDialog) { UnsavedChangesDialog() }

    // Inside a sub-menu, the system back button / edge-swipe returns to the Settings root
    // instead of leaving the screen. When already at the root this is disabled, so back falls
    // through to the app-level handler (which returns to the Notes home).
    BackHandler(enabled = route != SettingsRoute.Root) { goBack() }

    // --- API key visibility (task 14) ---
    // Hidden on entry. The eye button reveals for 3s. While the user is actively typing the
    // field is shown, then re-masks 1s after the last keystroke. keystrokeSeq starts at 0 and
    // only advances on real edits, so loading the saved key never triggers a reveal.
    var manualReveal by remember { mutableStateOf(false) }
    var typingReveal by remember { mutableStateOf(false) }
    var keystrokeSeq by remember { mutableStateOf(0) }
    LaunchedEffect(manualReveal) {
        if (manualReveal) {
            delay(3000)
            manualReveal = false
        }
    }
    LaunchedEffect(keystrokeSeq) {
        if (keystrokeSeq > 0) {
            delay(1000)
            typingReveal = false
        }
    }
    val keyVisible = manualReveal || typingReveal

    // =======================================================================================
    // Backup: export
    // =======================================================================================
    //
    // Exporting asks a question before it writes anything, because the answer genuinely matters and
    // the user is the only one who can give it. A backup is the one artefact that deliberately leaves
    // the device — into a cloud drive, an email to yourself, a Downloads folder shared with every app
    // that ever asked for storage access — and how it is locked is a decision with a real trade-off
    // on both sides. Picking silently on the user's behalf would be picking wrong for half of them.

    // The password this particular export will use. Null means "use the built-in key". Held across
    // the launcher round-trip, since the file picker's callback arrives long after the dialog closes.
    var exportPassword by remember { mutableStateOf<String?>(null) }

    // In-flight guard against the "multiple duplicate downloads" bug (task 11).
    //
    // The dialog's confirm/dismiss buttons both call beginExport, which dismisses the dialog and
    // launches the SAF create-document picker. If the button is tapped twice within the same frame —
    // easy to do, and the recomposition that removes the dialog hasn't happened yet — beginExport
    // fires twice and TWO pickers open, each writing its own file. That is exactly how three
    // identically-named lucent-backup.lcb files end up in Downloads from what the user experienced as
    // one action. This flag makes launching idempotent: the second call is ignored until the current
    // export finishes (or its picker is cancelled), so at most one file is ever written per request.
    var exportInFlight by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            val password = exportPassword
            val chosenSelection = BackupManager.BackupSelection(
                modules = exportModules,
                noteIds = exportNoteIds,
                taskIds = exportTaskIds,
                conversationIds = exportConversationIds,
                apiProfileNames = exportApiProfileNames
            )
            val job = scope.launch {
                backupBusyLabel = S.exportingBackup
                try {
                    // The full payload — notes (archived included), tasks, note version history,
                    // chats, conversations, every attachment, and all settings — sealed as one
                    // file. All of it, not just the API key. Written on IO so a large export can't
                    // stall the UI; use() closes the cipher stream even if the write throws, which
                    // matters because closing is what seals the final frame.
                    val result = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                BackupManager.exportEncrypted(context, db, repo, out, password, chosenSelection)
                            } ?: return@withContext S.backupWriteFailed
                            if (password.isNullOrEmpty()) {
                                S.backupSavedBuiltIn
                            } else {
                                S.backupSavedPassword
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // A user cancel is handled — and the partial file removed — below.
                            throw e
                        } catch (e: Exception) {
                            S.exportFailed(e.message ?: "")
                        }
                    }
                    backupStatus = result
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Cancel pressed: the write aborted mid-stream, so the partial file the picker
                    // created is deleted best-effort — a half-written envelope that can never
                    // decrypt again is worse than no file at all.
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                        try {
                            android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
                        } catch (_: Throwable) {
                            // Provider refused, or the document is already gone — nothing to do.
                        }
                    }
                    backupStatus = S.exportCancelledStatus
                } finally {
                    // The write is done, failed, or cancelled — clear the guard so the user can
                    // export again, and bring the modal down.
                    exportInFlight = false
                    backupBusyLabel = null
                    backupOpJob = null
                }
            }
            backupOpJob = job
        } else {
            // Picker cancelled: nothing was written, so release the guard immediately.
            exportInFlight = false
        }
    }

    fun beginExport(password: String?) {
        // Ignore a second launch while one is already pending — see exportInFlight above.
        if (exportInFlight) return
        exportInFlight = true
        exportPassword = password
        showExportDialog = false
        exportLauncher.launch("lucent-backup.lcb")
    }

    @Composable
    @NonRestartableComposable
    fun ExportBackupDialog() {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(S.exportBackupTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(S.exportBackupBody, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // ---- What to include (task 9) ----
                    Text(S.backupChooseWhat, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    // Notes and tasks carry a second level: tick the module to include it, or tap
                    // "choose…" to pick individual items. The count in the sub-label is the whole
                    // point of putting it here rather than only inside the sub-menu — "12 of 40"
                    // tells you at a glance that this is not a complete backup, which is the one
                    // fact a partial selection must never hide.
                    BackupModuleRow(
                        label = S.backupModNotes,
                        module = BackupManager.BackupModule.NOTES,
                        selected = exportModules,
                        subLabel = exportNoteIds?.let { S.backupNOfM(it.size, allNotes.size) },
                        onChooseItems = { itemPicker = ExportItemKind.NOTES },
                        onChange = { exportModules = it }
                    )
                    BackupModuleRow(
                        label = S.backupModTasks,
                        module = BackupManager.BackupModule.TASKS,
                        selected = exportModules,
                        subLabel = exportTaskIds?.let { S.backupNOfM(it.size, allTasks.size) },
                        onChooseItems = { itemPicker = ExportItemKind.TASKS },
                        onChange = { exportModules = it }
                    )
                    // Chats carry a second level too (task F1): the tick includes the whole assistant
                    // history, "choose…" narrows it to particular conversations. Offered only when
                    // there is more than nothing to pick.
                    BackupModuleRow(
                        label = S.backupModChats,
                        module = BackupManager.BackupModule.CHATS,
                        selected = exportModules,
                        subLabel = exportConversationIds?.let { S.backupNOfM(it.size, allConversations.size) },
                        onChooseItems = if (allConversations.isNotEmpty()) {
                            { itemPicker = ExportItemKind.CHATS }
                        } else null,
                        onChange = { exportModules = it }
                    )
                    BackupModuleRow(S.backupModSettings, BackupManager.BackupModule.SETTINGS, exportModules) { exportModules = it }
                    // Settings quietly carries the imported font files with it (they are what the
                    // font preference points at — see BackupManager). Unlike model files they are
                    // small, so they get no opt-out of their own, but the size is still quoted:
                    // a module that adds megabytes to the file should say so where it is ticked.
                    val importedFontBytes = remember(fontRefresh) { FontStore.totalFontBytes(context) }
                    if (importedFontBytes > 0L) {
                        Text(
                            S.backupModSettingsFontsDesc(AttachmentLimits.formatBytes(importedFontBytes)),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                        )
                    }
                    // API likewise: include every saved connection, or pick which ones. The drill-in
                    // is gated on realProfiles so its indices match what BackupManager filters on; a
                    // fresh install still on the flat keys just gets the whole-API tick.
                    BackupModuleRow(
                        label = S.backupModApi,
                        module = BackupManager.BackupModule.API,
                        selected = exportModules,
                        subLabel = exportApiProfileNames?.let { S.backupNOfM(it.size, realProfiles.size) },
                        onChooseItems = if (realProfiles.isNotEmpty()) {
                            { itemPicker = ExportItemKind.API }
                        } else null,
                        onChange = { exportModules = it }
                    )
                    BackupModuleRow(S.backupModLocalAssistant, BackupManager.BackupModule.LOCAL_ASSISTANT, exportModules) { exportModules = it }
                    // The model files are offered only when there are some, and always with their
                    // real size attached: "include local model files" means something very
                    // different at 40 MB than at 4 GB, and the number is the only honest way to
                    // say which one this phone is about to do.
                    val modelBytes = remember(lmRefresh) { LocalModelStore.totalModelBytes(context) }
                    if (modelBytes > 0L) {
                        BackupModuleRow(
                            S.backupModLocalModelFiles,
                            BackupManager.BackupModule.LOCAL_MODEL_FILES,
                            exportModules
                        ) { exportModules = it }
                        Text(
                            S.backupModLocalModelFilesDesc(AttachmentLimits.formatBytes(modelBytes)),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                        )
                    }
                    if (BackupManager.BackupSelection(exportModules, exportNoteIds, exportTaskIds, exportConversationIds, exportApiProfileNames).isEmpty) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(S.backupSelectionEmpty, color = DANGER_RED, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(S.addPasswordOptional, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(S.exportPasswordExplain, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = exportPasswordDraft,
                        onValueChange = { exportPasswordDraft = it },
                        label = { Text(S.fieldPasswordOptional) },
                        singleLine = true,
                        visualTransformation = if (exportPasswordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { exportPasswordVisible = !exportPasswordVisible }) {
                                Icon(
                                    if (exportPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (exportPasswordVisible) S.hidePassword else S.showPassword
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            // One primary action. A blank password → the portable built-in key (the default that
            // fixes cross-device restore, task 5); a typed password → real encryption. Saving the
            // typed password is only a same-device convenience for a quick re-import; a different
            // device still (correctly) asks for it, so the default file stays portable regardless.
            confirmButton = {
                Button(
                    enabled = !BackupManager.BackupSelection(exportModules, exportNoteIds, exportTaskIds, exportConversationIds, exportApiProfileNames).isEmpty,
                    onClick = {
                        val password = exportPasswordDraft.ifBlank { null }
                        if (password != null) {
                            AppScope.io.launch { repo.setBackupPassword(password) }
                        }
                        beginExport(password)
                    }
                ) { Text(S.actionExport) }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text(S.actionCancel) }
            }
        )
    }
    if (showExportDialog) { ExportBackupDialog() }

    // Second-level picker for individual notes / tasks. Rendered as a sibling of the export dialog
    // rather than nested inside it: an AlertDialog inside an AlertDialog is a platform arrangement
    // with no good behaviour, and this way dismissing the picker returns to an export dialog that
    // never went away and still holds the password the user had already typed.
    itemPicker?.let { kind ->
        // Notes and tasks are keyed on real row ids; chats on the conversation id; API on the
        // profile INDEX carried as a Long, so one generic picker drives all four. In every case a
        // full selection is stored back as null ("everything") rather than an explicit set of every
        // id — that keeps the manifest honest if items are added between choosing and exporting, and
        // it is what makes the "12 of 40" sub-label disappear again when the user re-ticks all.
        val ids: List<Pair<Long, String>> = when (kind) {
            ExportItemKind.NOTES -> allNotes.map { it.id to it.title.ifBlank { S.untitled } }
            ExportItemKind.TASKS -> allTasks.map { it.id to it.title.ifBlank { S.untitledTask } }
            ExportItemKind.CHATS -> allConversations.map { it.id to it.title.ifBlank { S.untitled } }
            ExportItemKind.API -> realProfiles.mapIndexed { i, p -> i.toLong() to p.name.ifBlank { S.backupModApi } }
        }
        val current: Set<Long> = when (kind) {
            ExportItemKind.NOTES -> exportNoteIds
            ExportItemKind.TASKS -> exportTaskIds
            ExportItemKind.CHATS -> exportConversationIds
            ExportItemKind.API -> exportApiProfileNames?.let { names ->
                realProfiles.mapIndexedNotNull { i, p -> if (p.name in names) i.toLong() else null }.toSet()
            }
        } ?: ids.map { it.first }.toSet()
        val title = when (kind) {
            ExportItemKind.NOTES -> S.backupPickNotesTitle
            ExportItemKind.TASKS -> S.backupPickTasksTitle
            ExportItemKind.CHATS -> S.backupPickChatsTitle
            ExportItemKind.API -> S.backupPickApiTitle
        }
        ExportItemPickerDialog(
            title = title,
            items = ids,
            selected = current,
            onDone = { picked ->
                val collapsed: Set<Long>? = if (picked.size == ids.size) null else picked
                when (kind) {
                    ExportItemKind.NOTES -> exportNoteIds = collapsed
                    ExportItemKind.TASKS -> exportTaskIds = collapsed
                    ExportItemKind.CHATS -> exportConversationIds = collapsed
                    ExportItemKind.API -> exportApiProfileNames =
                        collapsed?.let { idx -> idx.mapNotNull { realProfiles.getOrNull(it.toInt())?.name }.toSet() }
                }
                itemPicker = null
            },
            onDismiss = { itemPicker = null }
        )
    }

    // =======================================================================================
    // Backup: import
    // =======================================================================================
    //
    // Three steps, and the middle one only when it's needed:
    //
    //   1. Pick a file. Lucent reads its header and works out whether it wants a password.
    //   2. If it does, ask for it. (The header is plaintext precisely so this question can be asked
    //      *before* trying — otherwise the only way to find out would be to demand a password and see
    //      if it worked, which is a miserable thing to do to someone restoring a backup.)
    //   3. Show what is actually inside, and let them cancel.
    //
    // Step 3 is the one that was missing. Restoring merges a stranger's file into a live database,
    // and the old flow did it the instant the file was picked — no idea what was in it, no way back.

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val job = scope.launch {
                // Reading through a multi-gigabyte backup below is real work; the modal keeps
                // every other action out of the way while it runs and offers the one safe exit.
                backupBusyLabel = S.importingBackup
                try {
                    // A previous, abandoned pick may still hold state (and a grant); replace it.
                    discardImportSource()
                    // No whole-file read, and no staging copy either. The whole-file ByteArray was
                    // the crash (an OutOfMemoryError on any backup carrying model files, invisible
                    // to catch(Exception)); the staging copy that first replaced it cured the crash
                    // but still cost a full extra write — 16 GB of scratch space and minutes of
                    // flash I/O for a 16 GB model backup. BackupManager now streams both of its
                    // passes straight from the provider, so the pick costs nothing up front.
                    //
                    // Hold the read grant for the whole flow: OpenDocument Uris support persistable
                    // permissions, and taking one means the confirm dialog can sit open as long as
                    // the user likes without the source expiring underneath commit. Providers that
                    // refuse persistence throw; the ordinary session grant covers this screen's
                    // flow anyway, so that failure is deliberately ignored.
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Throwable) {
                    }
                    // The one cheap up-front check: can the document be opened at all? This keeps
                    // the friendly "couldn't read that file" for a dead pick, instead of a raw
                    // exception message out of inspect.
                    val openable = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { } != null
                        } catch (_: Throwable) {
                            false
                        }
                    }
                    if (!openable) {
                        releaseImportGrant(uri)
                        backupStatus = S.couldNotReadThatFile
                        return@launch
                    }
                    importSourceUri = uri
                    val source = uriSource(uri)

                    val header = BackupManager.peekPasswordRequirement(source)
                    if (header != null && header.needsPassword) {
                        // Try the password saved on this device first. On the phone that made the backup,
                        // that means restoring is still a single tap.
                        val stored = repo.backupPassword.first()
                        val preview = if (stored.isEmpty()) null else withContext(Dispatchers.IO) {
                            try {
                                BackupManager.inspect(context, source, stored)
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        if (preview != null) {
                            importPreview = preview
                        } else {
                            importPasswordDraft = ""
                            importPasswordError = false
                            importPasswordPrompt = true
                        }
                    } else {
                        val result = withContext(Dispatchers.IO) {
                            try {
                                Result.success(BackupManager.inspect(context, source, null))
                            } catch (t: Throwable) {
                                Result.failure(t)
                            }
                        }
                        result.fold(
                            onSuccess = { importPreview = it },
                            onFailure = {
                                backupStatus = S.importFailed(it.message ?: "")
                                discardImportSource()
                            }
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Cancel pressed while the file was being read: let go of the pick entirely.
                    // The grant may have been taken before importSourceUri was assigned, so it is
                    // released explicitly on that path.
                    if (importSourceUri == null) releaseImportGrant(uri) else discardImportSource()
                    backupStatus = S.importCancelledStatus
                } finally {
                    backupBusyLabel = null
                    backupOpJob = null
                }
            }
            backupOpJob = job
        }
    }

    // --- Step 2: the password prompt (only for a backup made with a custom password) ---
    @Composable
    @NonRestartableComposable
    fun ImportPasswordDialog(uri: Uri) {
        AlertDialog(
            onDismissRequest = { discardImportSource() },
            title = { Text(S.backupPasswordTitle) },
            text = {
                Column {
                    Text(S.backupPasswordBody, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = importPasswordDraft,
                        onValueChange = { importPasswordDraft = it; importPasswordError = false },
                        singleLine = true,
                        isError = importPasswordError,
                        enabled = !gateLockedOut && !gateWiping,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(if (importPasswordError) S.wrongPassword else S.lockPassword) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Wrong backup-password guesses charge the SAME shared counter as the lock
                    // screen and the other Settings prompts; a correct backup password merely
                    // proceeds (only the app's own credential resets the counter).
                    SettingsGateFeedback()
                }
            },
            confirmButton = {
                Button(
                    enabled = importPasswordDraft.isNotEmpty() && !gateLockedOut && !gateWiping,
                    onClick = {
                        val attempt = importPasswordDraft
                        val job = scope.launch {
                            // The PBKDF2 cost plus a full read-through of the file: long enough on
                            // a big backup to deserve the same modal-and-Cancel as the other passes.
                            backupBusyLabel = S.importingBackup
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    try {
                                        // Streaming inspect over the picked backup — the password's
                                        // PBKDF2 cost is paid here once; commit reuses the validated
                                        // password from the preview for its own pass.
                                        Result.success(
                                            BackupManager.inspect(context, uriSource(uri), attempt)
                                        )
                                    } catch (t: Throwable) {
                                        Result.failure(t)
                                    }
                                }
                                result.fold(
                                    onSuccess = {
                                        importPasswordPrompt = false
                                        importPreview = it
                                    },
                                    onFailure = { error ->
                                        if (error is com.lucent.app.data.BackupCrypto.WrongPasswordException) {
                                            // Stay on the dialog. There is no recovery for a forgotten
                                            // backup password — that is the whole point of it — so the
                                            // only useful thing left to offer is another try. Each
                                            // wrong guess also charges the shared credential gate.
                                            importPasswordError = true
                                            chargeSettingsGate()
                                        } else {
                                            backupStatus = S.importFailed(error.message ?: "")
                                            discardImportSource()
                                        }
                                    }
                                )
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                // Cancel pressed mid-inspect: drop the pick, exactly like the
                                // dialog's own Cancel button does.
                                discardImportSource()
                                backupStatus = S.importCancelledStatus
                            } finally {
                                backupBusyLabel = null
                                backupOpJob = null
                            }
                        }
                        backupOpJob = job
                    }
                ) { Text(S.lockContinue) }
            },
            dismissButton = { TextButton(onClick = { discardImportSource() }) { Text(S.actionCancel) } }
        )
    }
    if (importPasswordPrompt) importSourceUri?.let { ImportPasswordDialog(it) }

    // --- Step 3: show what's in the file, and let them say no ---
    @Composable
    @NonRestartableComposable
    fun ImportPreviewDialog(preview: BackupManager.BackupPreview) {
        AlertDialog(
            // Backing out of the preview abandons the import: the staging copy goes with it, or a
            // multi-gigabyte file would sit in cache until the next pick happened to replace it.
            onDismissRequest = { importPreview = null; discardImportSource() },
            title = { Text(S.restoreBackupTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    preview.exportedAt?.let {
                        Text(S.exportedWhen(formatTimestamp(it)), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        if (preview.passwordProtected) S.protectedByPassword
                        else S.protectedBuiltIn,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (preview.isEmpty) {
                        Text(S.backupEmpty, fontSize = 13.sp)
                    } else {
                        Text(S.backupContains, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        BackupContentLine(S.bkNotes, preview.notes, buildList {
                            if (preview.archivedNotes > 0) add(S.bkNArchived(preview.archivedNotes))
                            if (preview.trashedNotes > 0) add(S.bkNInTrash(preview.trashedNotes))
                        })
                        BackupContentLine(S.bkTasks, preview.tasks, buildList {
                            if (preview.completedTasks > 0) add(S.bkNCompleted(preview.completedTasks))
                            if (preview.trashedTasks > 0) add(S.bkNInTrash(preview.trashedTasks))
                        })
                        BackupContentLine(S.bkNoteVersions, preview.noteVersions, emptyList())
                        BackupContentLine(S.bkConversations, preview.conversations, emptyList())
                        BackupContentLine(S.bkChatMessages, preview.chatMessages, emptyList())
                        BackupContentLine(S.bkAttachments, preview.attachments, emptyList())
                        if (preview.hasSettings) {
                            BackupContentLine(S.bkSettings, 1, listOf(S.bkIncludingApiKeys))
                        }
                        if (preview.modelFiles > 0) {
                            BackupContentLine(
                                S.backupModLocalModelFiles,
                                preview.modelFiles,
                                listOf(AttachmentLimits.formatBytes(preview.modelBytes))
                            )
                        }
                        if (preview.fontFiles > 0) {
                            BackupContentLine(
                                S.bkImportedFonts,
                                preview.fontFiles,
                                listOf(AttachmentLimits.formatBytes(preview.fontBytes))
                            )
                        }

                        // ---- Restore only part of it (task 9) ----
                        //
                        // The same module list as the export dialog, but narrowed to what this file
                        // actually contains: offering to restore tasks from a notes-only backup is
                        // a checkbox that can only disappoint. `available` is computed from the
                        // preview's real counts rather than from the manifest's "modules" list, so
                        // a pre-v10 backup — which has no such list — still gets an accurate menu.
                        val available = buildSet {
                            if (preview.notes > 0) add(BackupManager.BackupModule.NOTES)
                            if (preview.tasks > 0) add(BackupManager.BackupModule.TASKS)
                            if (preview.chatMessages > 0 || preview.conversations > 0) {
                                add(BackupManager.BackupModule.CHATS)
                            }
                            if (preview.hasSettings) {
                                add(BackupManager.BackupModule.SETTINGS)
                                add(BackupManager.BackupModule.API)
                                add(BackupManager.BackupModule.LOCAL_ASSISTANT)
                            }
                            if (preview.modelFiles > 0) add(BackupManager.BackupModule.LOCAL_MODEL_FILES)
                        }
                        // Start with everything the file has selected: restoring all of it is what
                        // the button used to do, so that stays the default and opting out is the
                        // deliberate act.
                        LaunchedEffect(preview) {
                            restoreModules = available
                            restoreConversationIds = null
                            restoreApiProfileNames = null
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(S.restoreChooseWhat, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (BackupManager.BackupModule.NOTES in available) {
                            BackupModuleRow(S.backupModNotes, BackupManager.BackupModule.NOTES, restoreModules) { restoreModules = it }
                        }
                        if (BackupManager.BackupModule.TASKS in available) {
                            BackupModuleRow(S.backupModTasks, BackupManager.BackupModule.TASKS, restoreModules) { restoreModules = it }
                        }
                        if (BackupManager.BackupModule.CHATS in available) {
                            BackupModuleRow(
                                label = S.backupModChats,
                                module = BackupManager.BackupModule.CHATS,
                                selected = restoreModules,
                                subLabel = restoreConversationIds?.let { S.backupNOfM(it.size, preview.conversationList.size) },
                                onChooseItems = if (preview.conversationList.isNotEmpty()) {
                                    { restoreItemPicker = ExportItemKind.CHATS }
                                } else null,
                                onChange = { restoreModules = it }
                            )
                        }
                        if (BackupManager.BackupModule.SETTINGS in available) {
                            BackupModuleRow(S.backupModSettings, BackupManager.BackupModule.SETTINGS, restoreModules) { restoreModules = it }
                            BackupModuleRow(
                                label = S.backupModApi,
                                module = BackupManager.BackupModule.API,
                                selected = restoreModules,
                                subLabel = restoreApiProfileNames?.let { S.backupNOfM(it.size, preview.apiProfileNames.size) },
                                onChooseItems = if (preview.apiProfileNames.isNotEmpty()) {
                                    { restoreItemPicker = ExportItemKind.API }
                                } else null,
                                onChange = { restoreModules = it }
                            )
                            BackupModuleRow(S.backupModLocalAssistant, BackupManager.BackupModule.LOCAL_ASSISTANT, restoreModules) { restoreModules = it }
                        }
                        if (BackupManager.BackupModule.LOCAL_MODEL_FILES in available) {
                            BackupModuleRow(S.backupModLocalModelFiles, BackupManager.BackupModule.LOCAL_MODEL_FILES, restoreModules) { restoreModules = it }
                        }
                        if (restoreModules.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(S.backupSelectionEmpty, color = DANGER_RED, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(S.restoreMergeNote, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    enabled = !preview.isEmpty && restoreModules.isNotEmpty(),
                    onClick = {
                        // Which API profiles the restore should carry, and whether that would overflow
                        // the device's cap. Only a MULTI-API backup (one that carries a named profile
                        // list) merges; a legacy single-API backup keeps the whole-API replace by
                        // handing null through. "All" (restoreApiProfileNames == null) is turned into
                        // the explicit full set so it merges too, rather than replacing what's here.
                        val apiSelected = BackupManager.BackupModule.API in restoreModules
                        val isMultiApiBackup = preview.apiProfileNames.isNotEmpty()
                        val effectiveApiNames: Set<String>? = when {
                            !apiSelected -> null
                            !isMultiApiBackup -> null
                            else -> restoreApiProfileNames ?: preview.apiProfileNames.toSet()
                        }
                        // Only NEW names take a slot; a name already saved here is kept by the merge
                        // and costs nothing. If the newcomers wouldn't fit, ask before writing any.
                        val existingNames = com.lucent.app.data.ApiProfiles
                            .parse(savedProfilesJson).map { it.name }.toHashSet()
                        val incomingNew = effectiveApiNames?.count { it !in existingNames } ?: 0
                        val room = com.lucent.app.data.ApiProfiles.MAX - existingNames.size
                        if (incomingNew > room) {
                            apiLimitPrompt = true
                        } else {
                            runRestore(preview, restoreModules, restoreConversationIds, effectiveApiNames)
                        }
                    }
                ) { Text(S.actionRestore) }
            },
            dismissButton = {
                TextButton(onClick = { importPreview = null; discardImportSource() }) { Text(S.actionCancel) }
            }
        )
    }
    importPreview?.let { ImportPreviewDialog(it) }

    // --- Step 4: the result, announced the moment the restore finishes ---
    // A bottom sheet rather than another dialog: the user has just answered a dialog (the preview)
    // and a sheet reads as a report, not yet another question. It shows the exact summary the
    // commit produced ("Imported N notes, ..." or the failure message) under a success/failure
    // title. Rendered only while this tab is active so a restore that finishes after the user has
    // switched tabs presents its sheet when they come back to Settings, not over an unrelated
    // screen.
    // The modal progress dialog for a long-running backup export or import. Deliberately
    // impossible to dismiss from the outside — no outside tap, no back press — because the whole
    // point is that nothing else may run while gigabytes are in flight; Cancel is the one way out,
    // and it goes through cooperative cancellation so the streams shut down cleanly.
    if (active) backupBusyLabel?.let { busyLabel ->
        AlertDialog(
            onDismissRequest = { /* Blocked on purpose — see the comment above. */ },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { Text(busyLabel) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(S.backupBusyBody, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { backupOpJob?.cancel() }) { Text(S.actionCancel) }
            }
        )
    }

    if (active) importResultSheet?.let { (committed, message) ->
        val resultSheetState = rememberModalBottomSheetState()
        // Slide the sheet out before removing it from composition, so the button dismisses with
        // the same animation as a swipe-down or a scrim tap.
        fun dismissResultSheet() {
            scope.launch { resultSheetState.hide() }.invokeOnCompletion { importResultSheet = null }
        }
        ModalBottomSheet(
            onDismissRequest = { importResultSheet = null },
            sheetState = resultSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    if (committed) S.restoreDoneTitle else S.restoreFailedTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(message, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { dismissResultSheet() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(S.gotIt) }
            }
        }
    }

    // Second-level picker for the restore dialog (task F2) — the import-side twin of the export one.
    // Built from the preview's own lists (not the live database), so it offers exactly what the file
    // contains. Chats key on the conversation id; API keys on the profile INDEX in the file carried as
    // a Long, with the stored selection kept as names — same shape as the export picker.
    restoreItemPicker?.let { kind ->
        importPreview?.let { preview ->
            val ids: List<Pair<Long, String>> = when (kind) {
                ExportItemKind.CHATS -> preview.conversationList.map { it.first to it.second.ifBlank { S.untitled } }
                ExportItemKind.API -> preview.apiProfileNames.mapIndexed { i, name -> i.toLong() to name.ifBlank { S.backupModApi } }
                else -> emptyList()
            }
            val current: Set<Long> = when (kind) {
                ExportItemKind.CHATS -> restoreConversationIds
                ExportItemKind.API -> restoreApiProfileNames?.let { names ->
                    preview.apiProfileNames.mapIndexedNotNull { i, n -> if (n in names) i.toLong() else null }.toSet()
                }
                else -> null
            } ?: ids.map { it.first }.toSet()
            val title = when (kind) {
                ExportItemKind.CHATS -> S.backupPickChatsTitle
                else -> S.backupPickApiTitle
            }
            ExportItemPickerDialog(
                title = title,
                items = ids,
                selected = current,
                onDone = { picked ->
                    val collapsed: Set<Long>? = if (picked.size == ids.size) null else picked
                    when (kind) {
                        ExportItemKind.CHATS -> restoreConversationIds = collapsed
                        ExportItemKind.API -> restoreApiProfileNames =
                            collapsed?.let { idx -> idx.mapNotNull { preview.apiProfileNames.getOrNull(it.toInt()) }.toSet() }
                        else -> {}
                    }
                    restoreItemPicker = null
                },
                onDismiss = { restoreItemPicker = null }
            )
        }
    }

    // API-limit prompt: shown when a confirmed restore's API profiles would exceed ApiProfiles.MAX.
    // Rendered here (not nested inside the preview dialog) so a cancel leaves the preview and its
    // selections intact — the same reason the item picker above lives at this level. The incoming
    // list and remaining room are recomputed from the same inputs the confirm button used, so the two
    // always agree on whether a prompt is warranted.
    if (apiLimitPrompt) {
        importPreview?.let { preview ->
            val existingNames = com.lucent.app.data.ApiProfiles
                .parse(savedProfilesJson).map { it.name }.toHashSet()
            val chosenApi = restoreApiProfileNames ?: preview.apiProfileNames.toSet()
            val incoming = chosenApi.filter { it !in existingNames }
            val room = (com.lucent.app.data.ApiProfiles.MAX - existingNames.size).coerceAtLeast(0)
            ApiImportLimitDialog(
                incoming = incoming,
                canAdd = room,
                max = com.lucent.app.data.ApiProfiles.MAX,
                onDone = { picked -> runRestore(preview, restoreModules, restoreConversationIds, picked) },
                onDismiss = { apiLimitPrompt = false }
            )
        }
    }

    // --- App Lock setup ---
    // Captures a password (twice) and, optionally, a security question and its answer, then turns the
    // lock on. The credentials are hashed by AppLock before anything is stored; the raw
    // password/answer are never persisted. Cancelling leaves the lock off.
    //
    // The question is optional as of task 9 — but skipping it is a genuinely consequential choice, so
    // it is confirmed rather than merely allowed (see showNoRecoveryWarning below). Note that this is
    // not just a UI nicety: AppLock.createCredentials stores an *empty* answer hash in that case
    // rather than the hash of an empty string, because the latter would have been matched by typing a
    // single space into the recovery form.
    fun applyAppLock() {
        val creds = AppLock.createCredentials(lockPw, lockQuestion, lockAnswer)
        scope.launch { repo.setAppLock(true, creds) }
        AppLockController.enabled = true
        // Clear the captured secrets from memory now that they're hashed & stored.
        lockPw = ""; lockPwConfirm = ""; lockQuestion = ""; lockAnswer = ""
        lockSetupError = ""
        showAppLockSetup = false
        LucentToast.show(context, S.appLockOnToast)
    }

    @Composable
    @NonRestartableComposable
    fun AppLockSetupDialog() {
        AlertDialog(
            onDismissRequest = { showAppLockSetup = false },
            title = { Text(S.appLockSetupTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(S.appLockSetupBody, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = lockPw,
                        onValueChange = { lockPw = it; lockSetupError = "" },
                        label = { Text(S.lockPassword) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lockPwConfirm,
                        onValueChange = { lockPwConfirm = it; lockSetupError = "" },
                        label = { Text(S.fieldConfirmPassword) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lockQuestion,
                        onValueChange = { lockQuestion = it; lockSetupError = "" },
                        label = { Text(S.fieldSecurityQuestionOptional) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lockAnswer,
                        onValueChange = { lockAnswer = it; lockSetupError = "" },
                        label = { Text(S.fieldAnswerOptional) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (lockSetupError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(lockSetupError, color = Color(0xFFFF8A80), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    when {
                        lockPw.length < 4 -> lockSetupError = S.lockErrTooShort
                        lockPw != lockPwConfirm -> lockSetupError = S.lockErrMismatch
                        // Half a security question is still an error: a question with no answer can
                        // never be verified, and an answer with no question can never be asked.
                        lockQuestion.isNotBlank() && lockAnswer.isBlank() ->
                            lockSetupError = S.lockErrNeedAnswer
                        lockAnswer.isNotBlank() && lockQuestion.isBlank() ->
                            lockSetupError = S.lockErrNeedQuestion
                        // Both blank: allowed, but only after the user has been told what it costs.
                        lockQuestion.isBlank() && lockAnswer.isBlank() -> showNoRecoveryWarning = true
                        else -> applyAppLock()
                    }
                }) { Text(S.turnOn) }
            },
            dismissButton = {
                TextButton(onClick = {
                    lockPw = ""; lockPwConfirm = ""; lockQuestion = ""; lockAnswer = ""
                    showAppLockSetup = false
                }) { Text(S.actionCancel) }
            }
        )
    }
    if (showAppLockSetup) { AppLockSetupDialog() }

    // --- Disable-lock confirmation (task) ---
    //
    // Turning the lock OFF is a security downgrade, so it is gated exactly like a login: the current
    // password must be entered correctly before the protection is removed. The body spells out what
    // is being given up (anyone can then read everything without a password). A wrong password shows
    // an inline error and changes nothing; only a correct one disables the lock.
    @Composable
    @NonRestartableComposable
    fun AppLockDisableDialog() {
        AlertDialog(
            onDismissRequest = { showAppLockDisable = false },
            title = { Text(S.appLockDisableTitle) },
            text = {
                Column {
                    Text(S.appLockDisableBody, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = disablePw,
                        onValueChange = { disablePw = it; disableError = "" },
                        label = { Text(S.lockPassword) },
                        singleLine = true,
                        isError = disableError.isNotEmpty(),
                        enabled = !gateLockedOut && !gateWiping,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (disableError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(disableError, color = Color(0xFFFF8A80), fontSize = 13.sp)
                    }
                    SettingsGateFeedback()
                }
            },
            confirmButton = {
                Button(
                    enabled = disablePw.isNotEmpty() && appLockCreds.isNotEmpty() && !gateLockedOut && !gateWiping,
                    onClick = {
                        if (AppLock.verifyPassword(appLockCreds, disablePw)) {
                            settingsGateSuccess()
                            scope.launch { repo.setAppLock(false, "") }
                            AppLockController.enabled = false
                            AppLockController.unlock()
                            disablePw = ""; disableError = ""
                            showAppLockDisable = false
                            LucentToast.show(context, S.appLockOffToast)
                        } else {
                            disableError = ""
                            chargeSettingsGate()
                        }
                    }
                ) { Text(S.turnOff) }
            },
            dismissButton = {
                TextButton(onClick = {
                    disablePw = ""; disableError = ""
                    showAppLockDisable = false
                }) { Text(S.actionCancel) }
            }
        )
    }
    if (showAppLockDisable) { AppLockDisableDialog() }

    // --- "No security question" warning (task 9) ---
    //
    // Skipping the question is permitted, because a lock on a personal device is often protecting
    // against a curious housemate rather than an adversary, and forcing a recovery question on
    // someone who doesn't want one just adds a second secret to lose. But it is irreversible in the
    // worst way: forget the password and there is no reset, only "clear all data" — which is the
    // whole database, every attachment, gone. That deserves a sentence saying so *before* it
    // happens, not a support question afterwards.
    //
    // The setup dialog stays open underneath, so "Add a question" returns to it with the password
    // the user already typed still there.
    @Composable
    @NonRestartableComposable
    fun NoRecoveryWarningDialog() {
        AlertDialog(
            onDismissRequest = { showNoRecoveryWarning = false },
            title = { Text(S.noRecoveryTitle) },
            text = { Text(S.noRecoveryBody) },
            confirmButton = {
                Button(onClick = {
                    showNoRecoveryWarning = false
                    applyAppLock()
                }) { Text(S.turnOnAnyway) }
            },
            dismissButton = {
                TextButton(onClick = { showNoRecoveryWarning = false }) { Text(S.addAQuestion) }
            }
        )
    }
    if (showNoRecoveryWarning) { NoRecoveryWarningDialog() }

    // --- System integration privacy warning (task 6) ---
    @Composable
    @NonRestartableComposable
    fun ShareWarningDialog() {
        AlertDialog(
            onDismissRequest = { showShareWarning = false },
            title = { Text(S.shareWarnTitle) },
            text = { Text(S.shareWarnBody) },
            confirmButton = {
                Button(onClick = {
                    scope.launch { repo.setSystemIntegrationEnabled(true) }
                    ShareIntegration.setEnabled(context, true)
                    showShareWarning = false
                    // Toast rather than the Data page's backupStatus line: this control lives on
                    // Security and Privacy now (task 5).
                    LucentToast.show(context, S.systemIntegrationOnToast)
                }) { Text(S.turnOn) }
            },
            dismissButton = {
                TextButton(onClick = { showShareWarning = false }) { Text(S.actionCancel) }
            }
        )
    }
    if (showShareWarning) { ShareWarningDialog() }

    // ==============================================================================================
    //  C-GROUP dialogs — every switch that changes what the app is ALLOWED to do explains itself
    //  first, and every one of them states the cost, not just the benefit.
    // ==============================================================================================

    // --- Task 1: Blackout Mode ---
    @Composable
    @NonRestartableComposable
    fun BlackoutWarningDialog() {
        AlertDialog(
            onDismissRequest = { showBlackoutWarning = false },
            title = { Text(S.blackoutWarnTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(S.blackoutWarnBody)
                    // Blackout requires a password. If there is no lock yet, say so HERE rather
                    // than letting the user confirm and then bounce off a second dialog.
                    if (!appLockOn) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(S.blackoutNeedsPassword, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showBlackoutWarning = false
                    if (!appLockOn) {
                        // No lock yet: send the user through the existing lock-setup dialog, which
                        // already collects a password AND an optional security question. Blackout
                        // is switched on by that flow's completion, not here — turning it on with
                        // no password would produce an app nobody can open.
                        lockPw = ""; lockPwConfirm = ""; lockQuestion = ""; lockAnswer = ""
                        lockSetupError = ""
                        showAppLockSetup = true
                    } else {
                        scope.launch {
                            repo.setBlackoutEnabled(true)
                            com.lucent.app.data.BlackoutMode.hydrate(true)
                            ShareIntegration.setEnabled(context, false)
                        }
                    }
                }) { Text(S.blackoutWarnConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { showBlackoutWarning = false }) { Text(S.actionCancel) }
            }
        )
    }
    if (showBlackoutWarning) { BlackoutWarningDialog() }

    // --- Task 3: Crash Shield ---
    @Composable
    @NonRestartableComposable
    fun CrashShieldInfoDialog() {
        AlertDialog(
            onDismissRequest = { showCrashShieldInfo = false },
            title = { Text(S.crashShieldLimitsTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(S.crashShieldLimitsBody)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showCrashShieldInfo = false
                    scope.launch {
                        // setCrashShieldEnabled forces logging on in the same edit, so the two can
                        // never end up disagreeing.
                        repo.setCrashShieldEnabled(true)
                        StartupLog.setEnabled(true)
                        StartupLog.event(context, "crash shield: enabled from Settings")
                    }
                }) { Text(S.turnOn) }
            },
            dismissButton = {
                TextButton(onClick = { showCrashShieldInfo = false }) { Text(S.actionCancel) }
            }
        )
    }
    if (showCrashShieldInfo) { CrashShieldInfoDialog() }

    // --- Task 6: opening links in another app ---
    @Composable
    @NonRestartableComposable
    fun OpenLinksWarningDialog() {
        AlertDialog(
            onDismissRequest = { showOpenLinksWarning = false },
            title = { Text(S.openLinksWarnTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(S.openLinksWarnBody)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showOpenLinksWarning = false
                    scope.launch { repo.setOpenLinksExternally(true) }
                }) { Text(S.turnOn) }
            },
            dismissButton = {
                TextButton(onClick = { showOpenLinksWarning = false }) { Text(S.actionCancel) }
            }
        )
    }
    if (showOpenLinksWarning) { OpenLinksWarningDialog() }

    // --- Task 18: self-destruct ---
    //
    // The one dialog in the app whose confirm button is disabled until the user TYPES a word. Every
    // other destructive action here is undoable or recoverable from a backup; this one is neither,
    // and a tap is too cheap a gesture for a decision that cannot be revisited.
    @Composable
    @NonRestartableComposable
    fun SelfDestructWarningDialog() {
        val phrase = S.selfDestructConfirmPhrase
        AlertDialog(
            onDismissRequest = { showSelfDestructWarning = false },
            title = { Text(S.selfDestructWarnTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(S.selfDestructWarnBody)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = selfDestructTyped,
                        onValueChange = { selfDestructTyped = it },
                        singleLine = true,
                        label = { Text(S.selfDestructConfirmHint(phrase)) }
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = selfDestructTyped.trim().equals(phrase, ignoreCase = true),
                    onClick = {
                        showSelfDestructWarning = false
                        selfDestructTyped = ""
                        scope.launch { repo.setPwSelfDestructEnabled(true) }
                    }
                ) { Text(S.turnOn) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSelfDestructWarning = false
                    selfDestructTyped = ""
                }) { Text(S.actionCancel) }
            }
        )
    }
    if (showSelfDestructWarning) { SelfDestructWarningDialog() }

    // --- App-Lock gate for the danger zone ---
    //
    // When App Lock is on, the four destructive clears (all data / notes / tasks / chats) must be
    // confirmed with the lock password. The destructive dialogs themselves stay exactly as they
    // are; their confirm buttons route through requireLockAuth, which either runs the wipe
    // directly (lock off) or parks it behind this password prompt. The password is verified
    // against the same PBKDF2 credentials the lock screen uses, and a wrong entry stays in the
    // dialog with the standard error line rather than silently doing nothing.
    var dangerAuthAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var dangerAuthPw by remember { mutableStateOf("") }
    var dangerAuthError by remember { mutableStateOf("") }

    fun requireLockAuth(action: () -> Unit) {
        if (appLockOn && appLockCreds.isNotEmpty()) {
            dangerAuthPw = ""
            dangerAuthError = ""
            dangerAuthAction = action
        } else {
            action()
        }
    }

    @Composable
    @NonRestartableComposable
    fun DangerAuthDialog() {
        AlertDialog(
            onDismissRequest = { dangerAuthAction = null; dangerAuthPw = ""; dangerAuthError = "" },
            title = { Text(S.dangerAuthTitle) },
            text = {
                Column {
                    Text(S.dangerAuthBody)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = dangerAuthPw,
                        onValueChange = { dangerAuthPw = it; dangerAuthError = "" },
                        label = { Text(S.lockPassword) },
                        singleLine = true,
                        isError = dangerAuthError.isNotEmpty(),
                        enabled = !gateLockedOut && !gateWiping,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (dangerAuthError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(dangerAuthError, color = Color(0xFFFF8A80), fontSize = 13.sp)
                    }
                    SettingsGateFeedback()
                }
            },
            confirmButton = {
                Button(
                    enabled = dangerAuthPw.isNotEmpty() && !gateLockedOut && !gateWiping,
                    onClick = {
                        if (AppLock.verifyPassword(appLockCreds, dangerAuthPw)) {
                            settingsGateSuccess()
                            val action = dangerAuthAction
                            dangerAuthAction = null
                            dangerAuthPw = ""
                            dangerAuthError = ""
                            action?.invoke()
                        } else {
                            dangerAuthError = ""
                            chargeSettingsGate()
                        }
                    }
                ) { Text(S.actionConfirm) }
            },
            dismissButton = {
                TextButton(onClick = {
                    dangerAuthAction = null; dangerAuthPw = ""; dangerAuthError = ""
                }) { Text(S.actionCancel) }
            }
        )
    }
    if (dangerAuthAction != null) { DangerAuthDialog() }


    @Composable
    @NonRestartableComposable
    fun ClearDataDialog() {
        AlertDialog(
            onDismissRequest = { showClearData = false },
            title = { Text(S.clearAllDataTitle) },
            text = { Text(S.clearAllDataBody) },
            confirmButton = {
                TextButton(onClick = {
                    showClearData = false
                    // App Lock on → the wipe below runs only after the password prompt
                    // (requireLockAuth / DangerAuthDialog); lock off → it runs immediately.
                    requireLockAuth { AppScope.io.launch {
                        // Cancel every scheduled reminder *before* the rows they point at disappear.
                        // An alarm outlives the task it belongs to: skip this and a notification for
                        // a task that no longer exists anywhere in the app can still fire hours
                        // later, which is both baffling and impossible to make stop.
                        db.taskDao().getAllOnce().forEach {
                            com.lucent.app.reminders.ReminderScheduler.cancel(appContext, it.id)
                        }
                        // ---- Database rows ----
                        db.noteVersionDao().clearAll()
                        db.noteDao().clearAll()
                        db.taskDao().clearAll()
                        db.chatDao().clearAll()
                        db.chatConversationDao().clearAll()

                        // ---- Preferences ----
                        repo.clearAll()
                        // The usage scores live in their OWN DataStore (lucent_usage), which
                        // repo.clearAll() has never touched — see UsageTracker.clearAll.
                        com.lucent.app.data.UsageTracker.clearAll(appContext)

                        // ---- Files on disk ----
                        // Nothing references anything on disk any more, so sweeping with an
                        // empty "referenced ids" set drops every stored attachment file. If
                        // we skipped this, the files would just sit there until the next
                        // startup ran the orphan sweep — cleaner to free the space now so
                        // the storage figure matches what the user just did.
                        com.lucent.app.data.AttachmentStore.pruneOrphans(appContext, emptySet())
                        // Decrypted attachment previews are copies of the user's files sitting
                        // in cacheDir. The OS clears that eventually; "delete everything" should
                        // not mean "eventually".
                        com.lucent.app.data.AttachmentAccess.clearPreviewCache(appContext)
                        // Everything else parked in cacheDir goes too — restore temp blobs from an
                        // interrupted import, decode scratch files, whatever a future feature puts
                        // there. Cache contents are disposable by definition, and "delete
                        // everything" should leave the cache looking like first launch, not
                        // "whenever the OS gets around to it".
                        appContext.cacheDir.listFiles()?.forEach { f -> runCatching { f.deleteRecursively() } }

                        // ---- The imported local models (task 8) ----
                        //
                        // This is the omission that prompted the task, and it was the largest
                        // thing on disk by three orders of magnitude: a wipe could leave four
                        // gigabytes of GGUF behind while reporting that all data had been
                        // cleared. LocalModelStore.deleteAll had existed, correctly written and
                        // documented as "used when wiping all data", and simply was never called
                        // from anywhere — dead code that read as a finished feature.
                        //
                        // Shut the engine down FIRST. The active model may be resident in memory
                        // with its file open; deleting underneath a live llama context risks a
                        // native fault, and on some filesystems the bytes are not freed until the
                        // last handle closes, so the space would not even come back.
                        com.lucent.app.local.LocalLlm.shutdown()
                        com.lucent.app.local.LocalModelStore.deleteAll(appContext)

                        // ---- The imported fonts (font library task) ----
                        //
                        // Same reasoning as the models above, at font scale: imported font files
                        // are user data on disk that repo.clearAll() knows nothing about, so a
                        // wipe must sweep them explicitly. The cached FontFamily objects go too —
                        // the font preference has just been reset to "system" by repo.clearAll(),
                        // and a family built from a deleted file must not survive to be handed
                        // out again if a later import mints a new library.
                        FontStore.deleteAll(appContext)
                        LucentFontResolver.evictAll()

                        // ---- Diagnostics and one-off markers ----
                        com.lucent.app.data.StartupLog.clear(appContext)
                        com.lucent.app.data.StartupLog.setEnabled(false)
                        // A "your database couldn't be decrypted" notice describes a database
                        // that no longer exists, so it must not survive into the fresh app.
                        com.lucent.app.data.DatabaseEncryption.clearLockedNotice(appContext)
                        // Set-aside database copies: a failed decryption parks the old DB beside
                        // the live one rather than deleting it, which is right in normal operation
                        // and wrong here — a wipe that leaves multi-megabyte snapshots of the data
                        // it claims to have erased is not the reinstall it promises.
                        com.lucent.app.data.DatabaseEncryption.purgeSetAsideDatabases(appContext)

                        // ---- OS-level state the app owns ----
                        // The share-sheet entry is a manifest component, not a preference: the
                        // preference has just been reset to its default, so the component has to
                        // follow it or the app keeps advertising an integration it believes is
                        // off. Same class of bug as a reminder outliving its task.
                        com.lucent.app.data.ShareIntegration.setEnabled(appContext, false)
                        // Home-screen widgets keep rendering their last known rows until told
                        // otherwise, so without this the user's launcher would still be showing
                        // tasks from the data they just erased.
                        com.lucent.app.widget.WidgetUpdater.refreshContent(appContext)

                        withContext(Dispatchers.Main) {
                            backupStatus = ""
                            // The local-model list on this page is remember()ed off lmRefresh.
                            // Without this bump it keeps rendering the pre-wipe snapshot — model
                            // names whose files are gone, showing as "0 MB" and still marked
                            // Active — until the screen happens to be rebuilt. That stale ghost
                            // was the reported "cleared all data, the model name is still there"
                            // bug; the store itself was wiped correctly above. The font list
                            // and the byte figures on the export sheet are remember()ed the same
                            // way, so they get the same treatment.
                            lmRefresh++
                            fontRefresh++
                            // The assistant holds the current conversation id in memory; the row
                            // it points at is gone, so reset it to the greeting rather than
                            // leaving it observing a conversation that no longer exists.
                            AssistantController.onAllChatsCleared(appContext)
                            LucentToast.show(appContext, S.allDataClearedToast)
                        }
                    }
                } }) { Text(S.deleteEverything) }
            },
            dismissButton = { TextButton(onClick = { showClearData = false }) { Text(S.actionCancel) } }
        )
    }
    if (showClearData) { ClearDataDialog() }

    // Clear only notes. After deleting the rows, re-run the orphan sweep, which recomputes the
    // referenced attachment ids from whatever remains (tasks) and frees files that belonged only
    // to notes while keeping any still referenced elsewhere.
    @Composable
    @NonRestartableComposable
    fun ClearNotesDialog() {
        AlertDialog(
            onDismissRequest = { showClearNotes = false },
            title = { Text(S.clearNotesTitle) },
            text = { Text(S.clearNotesBody) },
            confirmButton = {
                TextButton(onClick = {
                    showClearNotes = false
                    // App Lock on → the wipe below runs only after the password prompt
                    // (requireLockAuth / DangerAuthDialog); lock off → it runs immediately.
                    requireLockAuth { AppScope.io.launch {
                        // A note's revision history belongs to the note. Leaving it behind would
                        // orphan every row and quietly grow a table nothing can ever reach again.
                        db.noteVersionDao().clearAll()
                        db.noteDao().clearAll()
                        com.lucent.app.data.AttachmentMigration.pruneOrphans(appContext)
                        withContext(Dispatchers.Main) { LucentToast.show(appContext, S.notesClearedToast) }
                    }
                } }) { Text(S.deleteNotesBtn) }
            },
            dismissButton = { TextButton(onClick = { showClearNotes = false }) { Text(S.actionCancel) } }
        )
    }
    if (showClearNotes) { ClearNotesDialog() }

    @Composable
    @NonRestartableComposable
    fun ClearTasksDialog() {
        AlertDialog(
            onDismissRequest = { showClearTasks = false },
            title = { Text(S.clearTasksTitle) },
            text = { Text(S.clearTasksBody) },
            confirmButton = {
                TextButton(onClick = {
                    showClearTasks = false
                    // App Lock on → the wipe below runs only after the password prompt
                    // (requireLockAuth / DangerAuthDialog); lock off → it runs immediately.
                    requireLockAuth { AppScope.io.launch {
                        // Same reasoning as "Clear all data": an alarm outlives its task unless it's
                        // explicitly cancelled first.
                        db.taskDao().getAllOnce().forEach {
                            com.lucent.app.reminders.ReminderScheduler.cancel(appContext, it.id)
                        }
                        db.taskDao().clearAll()
                        com.lucent.app.data.AttachmentMigration.pruneOrphans(appContext)
                        withContext(Dispatchers.Main) { LucentToast.show(appContext, S.tasksClearedToast) }
                    }
                } }) { Text(S.deleteTasksBtn) }
            },
            dismissButton = { TextButton(onClick = { showClearTasks = false }) { Text(S.actionCancel) } }
        )
    }
    if (showClearTasks) { ClearTasksDialog() }

    // Clear only the assistant's chat history. Chat attachments are stored inline on each message
    // row, so deleting the rows frees them directly — no disk sweep needed. We also reset the
    // assistant's in-memory conversation cache so it doesn't keep showing a deleted conversation.
    // --- API key deletion confirmation (task 1) ---
    // Only acts on the explicit "Delete" press. The index is re-validated inside the click because
    // the profile list can change between opening the dialog and confirming; deleteProfile itself
    // also refuses to remove the last remaining profile.
    @Composable
    @NonRestartableComposable
    fun DeleteApiProfileDialog(idx: Int) {
        val name = profiles.getOrNull(idx)?.name?.ifBlank { S.apiFallbackName(idx + 1) } ?: S.thisApiFallback
        AlertDialog(
            onDismissRequest = { profilePendingDelete = null },
            title = { Text(S.apiDeleteConfirmTitle) },
            text = { Text(S.apiDeleteConfirmBody(name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteProfile(idx)
                    profilePendingDelete = null
                }) { Text(S.actionDelete) }
            },
            dismissButton = { TextButton(onClick = { profilePendingDelete = null }) { Text(S.actionCancel) } }
        )
    }
    profilePendingDelete?.let { DeleteApiProfileDialog(it) }

    @Composable
    @NonRestartableComposable
    fun ClearChatsDialog() {
        AlertDialog(
            onDismissRequest = { showClearChats = false },
            title = { Text(S.clearChatsTitle) },
            text = { Text(S.clearChatsBody) },
            confirmButton = {
                TextButton(onClick = {
                    showClearChats = false
                    // App Lock on → the wipe below runs only after the password prompt
                    // (requireLockAuth / DangerAuthDialog); lock off → it runs immediately.
                    requireLockAuth { AppScope.io.launch {
                        db.chatDao().clearAll()
                        db.chatConversationDao().clearAll()
                        AssistantController.onAllChatsCleared(appContext)
                        withContext(Dispatchers.Main) { LucentToast.show(appContext, S.chatsClearedToast) }
                    }
                } }) { Text(S.deleteChatsBtn) }
            },
            dismissButton = { TextButton(onClick = { showClearChats = false }) { Text(S.actionCancel) } }
        )
    }
    if (showClearChats) { ClearChatsDialog() }

    // --- Local model: per-slot delete confirmation (local-model task) ---
    // Deleting always asks first, for ANY model. If the model being deleted is the resident one the
    // engine is freed FIRST, then the file: unloading after deleting would leave a multi-gigabyte
    // model resident with nothing on disk to reload, which is the worst of both.
    @Composable
    @NonRestartableComposable
    fun DeleteLocalModelDialog(slot: LocalModelStore.ModelSlot) {
        AlertDialog(
            onDismissRequest = { lmSlotPendingDelete = null },
            title = { Text(S.lmDeleteTitle) },
            text = { Text(S.lmDeleteBody(slot.name.ifBlank { "model.gguf" })) },
            confirmButton = {
                TextButton(onClick = {
                    deleteLocalModel(slot)
                    lmSlotPendingDelete = null
                }) { Text(S.actionDelete) }
            },
            dismissButton = { TextButton(onClick = { lmSlotPendingDelete = null }) { Text(S.actionCancel) } }
        )
    }
    lmSlotPendingDelete?.let { DeleteLocalModelDialog(it) }

    // --- Local model: name a model at import time (custom names, task requirement) ---
    // The file is already picked; this captures the label before the copy runs. Cancelling here
    // drops the pending import entirely (nothing was copied yet).
    @Composable
    @NonRestartableComposable
    fun NameLocalModelDialog(uri: Uri) {
        AlertDialog(
            onDismissRequest = { lmPendingImportUri = null },
            title = { Text(S.lmNameModelTitle) },
            text = {
                Column {
                    Text(S.lmNameModelBody, color = onGradientMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lmImportName,
                        onValueChange = { lmImportName = it.take(60) },
                        label = { Text(S.lmModelNameField) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    lmPendingImportUri = null
                    startLocalImport(uri, lmImportName)
                }) { Text(S.lmImportConfirm) }
            },
            dismissButton = { TextButton(onClick = { lmPendingImportUri = null }) { Text(S.actionCancel) } }
        )
    }
    lmPendingImportUri?.let { NameLocalModelDialog(it) }

    // --- Local model: rename an imported model ---
    @Composable
    @NonRestartableComposable
    fun RenameLocalModelDialog(slot: LocalModelStore.ModelSlot) {
        AlertDialog(
            onDismissRequest = { lmRenameTarget = null },
            title = { Text(S.lmRenameTitle) },
            text = {
                OutlinedTextField(
                    value = lmRenameText,
                    onValueChange = { lmRenameText = it.take(60) },
                    label = { Text(S.lmModelNameField) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = slot.id
                    val newName = lmRenameText
                    lmRenameTarget = null
                    scope.launch {
                        withContext(Dispatchers.IO) { LocalModelStore.rename(context, id, newName) }
                        lmRefresh++
                    }
                }) { Text(S.actionSave) }
            },
            dismissButton = { TextButton(onClick = { lmRenameTarget = null }) { Text(S.actionCancel) } }
        )
    }
    lmRenameTarget?.let { RenameLocalModelDialog(it) }

    // --- Imported fonts: per-font delete confirmation (font library task) ---
    // Deleting always asks first. The helper resets the selection to the system font before the
    // file goes, so nothing is ever asked to render from a family whose file is missing.
    @Composable
    @NonRestartableComposable
    fun DeleteImportedFontDialog(slot: FontStore.FontSlot) {
        AlertDialog(
            onDismissRequest = { fontPendingDelete = null },
            title = { Text(S.fontDeleteTitle) },
            text = { Text(S.fontDeleteBody(slot.name.ifBlank { slot.fileName })) },
            confirmButton = {
                TextButton(onClick = {
                    deleteImportedFont(slot)
                    fontPendingDelete = null
                }) { Text(S.actionDelete) }
            },
            dismissButton = { TextButton(onClick = { fontPendingDelete = null }) { Text(S.actionCancel) } }
        )
    }
    fontPendingDelete?.let { DeleteImportedFontDialog(it) }

    // --- Imported fonts: name a font at import time (custom names, task requirement) ---
    // The file is already picked; this captures the label before the copy runs. Cancelling here
    // drops the pending import entirely (nothing was copied yet).
    @Composable
    @NonRestartableComposable
    fun NameImportedFontDialog(uri: Uri) {
        AlertDialog(
            onDismissRequest = { fontPendingImportUri = null },
            title = { Text(S.fontNameTitle) },
            text = {
                Column {
                    Text(S.fontNameBody, color = onGradientMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fontImportName,
                        onValueChange = { fontImportName = it.take(60) },
                        label = { Text(S.fontNameField) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    fontPendingImportUri = null
                    startFontImport(uri, fontImportName)
                }) { Text(S.lmImportConfirm) }
            },
            dismissButton = { TextButton(onClick = { fontPendingImportUri = null }) { Text(S.actionCancel) } }
        )
    }
    fontPendingImportUri?.let { NameImportedFontDialog(it) }

    // --- Local model: warn before turning "use local model" ON ---
    // Enabling local mode freezes the cloud API and loads a multi-gigabyte model into RAM, so it
    // spells out the consequences first: the API stops being called, memory use is high, quitting the
    // app mid-reply interrupts the answer, and quitting frees that memory. Confirming enables it.
    @Composable
    @NonRestartableComposable
    fun ConfirmUseLocalDialog() {
        AlertDialog(
            onDismissRequest = { lmConfirmUseLocalOn = false },
            title = { Text(S.lmUseLocalWarnTitle) },
            text = { Text(S.lmUseLocalWarnBody) },
            confirmButton = {
                TextButton(onClick = {
                    lmConfirmUseLocalOn = false
                    AppScope.io.launch { repo.setLocalModelEnabled(true) }
                }) { Text(S.lmWarnEnableAnyway) }
            },
            dismissButton = { TextButton(onClick = { lmConfirmUseLocalOn = false }) { Text(S.actionCancel) } }
        )
    }
    if (lmConfirmUseLocalOn) { ConfirmUseLocalDialog() }

    // Warn before letting the on-device model call tools: it can be slower and, on a small model,
    // unreliable. Confirming enables it; cancelling leaves it off.
    @Composable
    @NonRestartableComposable
    fun ConfirmToolsDialog() {
        AlertDialog(
            onDismissRequest = { lmConfirmToolsOn = false },
            title = { Text(S.lmToolsWarnTitle) },
            text = { Text(S.lmToolsWarnBody) },
            confirmButton = {
                TextButton(onClick = {
                    lmConfirmToolsOn = false
                    AppScope.io.launch { repo.setLocalToolsEnabled(true) }
                }) { Text(S.lmWarnEnableAnyway) }
            },
            dismissButton = { TextButton(onClick = { lmConfirmToolsOn = false }) { Text(S.actionCancel) } }
        )
    }
    if (lmConfirmToolsOn) { ConfirmToolsDialog() }

    // Warn before switching the on-device model to the GPU: faster on some phones, but Vulkan
    // drivers vary and it can be unstable; it also needs the GPU backend compiled into the build.
    @Composable
    @NonRestartableComposable
    fun ConfirmGpuDialog() {
        AlertDialog(
            onDismissRequest = { lmConfirmGpuOn = false },
            title = { Text(S.lmGpuWarnTitle) },
            text = { Text(S.lmGpuWarnBody) },
            confirmButton = {
                TextButton(onClick = {
                    lmConfirmGpuOn = false
                    // Record-only by design: never call into LocalLlm from here. A reply that is
                    // mid-generation keeps the backend it started on; the next send picks this
                    // up (LocalLlm.setGpuEnabled documents the contract).
                    AppScope.io.launch { repo.setLocalGpuEnabled(true) }
                }) { Text(S.lmWarnEnableAnyway) }
            },
            dismissButton = { TextButton(onClick = { lmConfirmGpuOn = false }) { Text(S.actionCancel) } }
        )
    }
    if (lmConfirmGpuOn) { ConfirmGpuDialog() }

    // --- Warn before letting a reply keep running after the app leaves the screen (task 2) ---
    // Same shape as the tools/GPU warnings, for the same reason: this is the one setting that lets
    // Lucent hold gigabytes of RAM while the user is doing something else entirely, so it is opt-in
    // behind a plain description of that cost. Turning it back OFF never asks.
    @Composable
    @NonRestartableComposable
    fun ConfirmBackgroundDialog() {
        AlertDialog(
            onDismissRequest = { lmConfirmBackgroundOn = false },
            title = { Text(S.lmBackgroundWarnTitle) },
            text = { Text(S.lmBackgroundWarnBody) },
            confirmButton = {
                TextButton(onClick = {
                    lmConfirmBackgroundOn = false
                    AppScope.io.launch { repo.setLocalBackgroundReplyEnabled(true) }
                }) { Text(S.lmWarnEnableAnyway) }
            },
            dismissButton = { TextButton(onClick = { lmConfirmBackgroundOn = false }) { Text(S.actionCancel) } }
        )
    }
    if (lmConfirmBackgroundOn) { ConfirmBackgroundDialog() }

    // --- Selective Markdown export (choose which notes/tasks) ---
    // Lists for the picker, live so a just-added item is selectable.
    val notesForExport by remember { db.noteDao().getAll() }.collectAsState(initial = emptyList())
    val tasksForExport by remember { db.taskDao().getAll() }.collectAsState(initial = emptyList())
    // Which picker is open (null = none). NOTES or TASKS.
    var exportKind by remember { mutableStateOf<ExportKind?>(null) }
    // Part of the same "fold back to the root on leave" rule as `route` above (task 3); it lives
    // here rather than beside that effect because this is where the state it clears is declared.
    LaunchedEffect(active) {
        if (!active) exportKind = null
    }
    // The generated file bytes waiting for the location the user is about to pick. Bytes rather than
    // a Markdown string now, because Word/Excel/PDF exports are binary (task 1). The MIME rides along
    // so the created document is typed correctly.
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingExportName by remember { mutableStateOf("lucent-export.md") }
    // The picker is created with a generic type and the real MIME/extension come from the chosen
    // format via the suggested file name — one launcher serves every format.
    val selectiveExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val bytes = pendingExportBytes
        pendingExportBytes = null
        if (uri != null && bytes != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
                }
                backupStatus = S.exportedSelected
            }
        }
    }

    // Turn a chosen subset + format into bytes and kick off the file picker. Shared by the notes and
    // tasks export screens below so the format handling lives in exactly one place. When [asZip] is
    // set the bytes are a .zip bundle (document + attachment files) and the suggested name gets a
    // .zip extension instead of the format's own; the generic launcher writes whatever bytes it's
    // given, so the extension is all that needs to change.
    fun launchExport(
        fileStem: String,
        bytes: ByteArray,
        format: com.lucent.app.data.ExportFormat,
        asZip: Boolean = false
    ) {
        pendingExportBytes = bytes
        pendingExportName = "$fileStem.${if (asZip) "zip" else format.extension}"
        exportKind = null
        selectiveExportLauncher.launch(pendingExportName)
    }

    BackHandler(enabled = exportKind != null) { exportKind = null }

    // A single scroll state for the whole settings body, declared BEFORE the export-selection early
    // return below (task 9). The export picker replaces the settings body entirely for a moment; if
    // the body's scroll position lived inside it, that position would be forgotten while the picker
    // was up and the page would snap back to the top on return. Hoisting it here — above the return —
    // keeps it alive across the detour, so coming back from "Choose … to export" lands exactly where
    // the user left off.
    val rootScroll = rememberScrollState()

    if (exportKind != null) {
        when (exportKind) {
            ExportKind.NOTES -> ExportSelectionScreen(
                title = S.exportNotesScreenTitle,
                items = notesForExport.filter { it.trashedAt == null },
                id = { it.id },
                label = { it.title },
                subtitle = { formatTimestamp(it.updatedAt) },
                timestamp = { it.updatedAt },
                searchText = { it.title + "\n" + it.body },
                attachmentsOf = { com.lucent.app.data.Attachments.parse(it.attachments) },
                // Round R1, task 3 — a note's drawn canvases, offered beside its files.
                doodlesOf = { com.lucent.app.data.DoodleExport.canvasesOf(it) },
                onExport = { subset, format, atts, canvases ->
                    val doc = com.lucent.app.data.DocumentExport.exportNotes(subset, format)
                    // R3 report: one PDF per NOTE, not one per canvas. The ticked canvases of each
                    // doodle note are grouped and written as a single multi-page PDF (the picker
                    // still ticks canvases individually — that is the selection UI — but the
                    // exporter merges them). Rendering happens here rather than in the picker so
                    // the picker stays a picker: it decides what is wanted, this decides what that
                    // costs. The heading is the note's title, so a folder of exported drawings
                    // survives being renamed.
                    val canvasFiles = canvases.groupBy { it.ownerId }.flatMap { (ownerId, group) ->
                        val owner = subset.firstOrNull { it.id == ownerId }
                        val title = owner?.title.orEmpty().ifBlank { S.untitled }
                        // One ticked canvas keeps its descriptive "canvas N" name; a merged set
                        // takes the note's plain file stem.
                        val name = if (group.size == 1) group.first().fileName
                                   else com.lucent.app.data.DoodleExport.fileStem(title).ifBlank { S.untitled } + ".pdf"
                        listOf(name to com.lucent.app.data.DocumentExport.doodlesPdf(group, title))
                    }
                    if (atts.isEmpty() && canvasFiles.isEmpty()) {
                        launchExport("lucent-notes", doc, format)
                    } else {
                        val bundle = com.lucent.app.data.DocumentExport.zipWithAttachments(
                            context, "lucent-notes.${format.extension}", doc, atts, canvasFiles
                        )
                        launchExport("lucent-notes", bundle, format, asZip = true)
                    }
                },
                onBack = { exportKind = null }
            )
            ExportKind.TASKS -> ExportSelectionScreen(
                title = S.exportTasksScreenTitle,
                items = tasksForExport.filter { it.trashedAt == null },
                id = { it.id },
                label = { it.title },
                subtitle = { formatTimestamp(it.createdAt) + if (it.isDone) S.doneSuffix else "" },
                timestamp = { it.createdAt },
                searchText = { it.title + "\n" + it.notes },
                attachmentsOf = { com.lucent.app.data.Attachments.parse(it.attachments) },
                // A task cannot hold a drawing, so `doodlesOf` is left at its empty default and the
                // canvas list handed back here is always empty; it is named `_` rather than dropped
                // so the arity mismatch would be a compile error if that ever stopped being true.
                onExport = { subset, format, atts, _ ->
                    val doc = com.lucent.app.data.DocumentExport.exportTasks(subset, format)
                    if (atts.isEmpty()) {
                        launchExport("lucent-tasks", doc, format)
                    } else {
                        val bundle = com.lucent.app.data.DocumentExport.zipWithAttachments(
                            context, "lucent-tasks.${format.extension}", doc, atts
                        )
                        launchExport("lucent-tasks", bundle, format, asZip = true)
                    }
                },
                onBack = { exportKind = null }
            )
            null -> {}
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rootScroll).hazeSource(state = LocalHazeState.current).padding(16.dp).padding(bottom = LocalBottomBarInset.current)
    ) {
        when (route) {
            SettingsRoute.Root -> RootSettingsPage(onRoute = { route = it })

            SettingsRoute.Language -> LanguageSettingsPage(
                repo = repo,
                importedFonts = importedFonts,
                fontCanImportMore = fontCanImportMore,
                fontImporting = fontImporting,
                fontError = fontError,
                onRequestDeleteFont = { fontPendingDelete = it },
                onImportFontClick = { fontImportLauncher.launch(arrayOf("*/*")) },
                onRoute = { route = it }
            )

            SettingsRoute.Assistant -> AssistantSettingsPage(
                repo = repo,
                profiles = profiles,
                selectedProfileIdx = selectedProfileIdx,
                onRoute = { route = it }
            )

            SettingsRoute.LocalModel -> LocalModelSettingsPage(
                repo = repo,
                lmModels = lmModels,
                lmActiveId = lmActiveId,
                lmImporting = lmImporting,
                lmCanImportMore = lmCanImportMore,
                lmError = lmError,
                lmRefresh = lmRefresh,
                onSelectLocalModel = { selectLocalModel(it) },
                onRequestRenameModel = { lmRenameText = it.name; lmRenameTarget = it },
                onRequestDeleteModel = { lmSlotPendingDelete = it },
                onImportModelClick = { lmImportLauncher.launch(arrayOf("*/*")) },
                onImportMmprojClick = { lmMmprojLauncher.launch(arrayOf("*/*")) },
                onRemoveMmproj = { removeMmproj() },
                onRequestUseLocalOn = { lmConfirmUseLocalOn = true },
                onRequestToolsOn = { lmConfirmToolsOn = true },
                onRequestGpuOn = { lmConfirmGpuOn = true },
                onRequestBackgroundOn = { lmConfirmBackgroundOn = true },
                onRoute = { route = it }
            )

            SettingsRoute.Personalization -> PersonalizationSettingsPage(
                repo = repo,
                assistantName = assistantName,
                onAssistantNameChange = { assistantName = it },
                assistantStyle = assistantStyle,
                onAssistantStyleChange = { assistantStyle = it },
                onSave = { persistAssistantSettings() },
                onBack = { leavePersonalization() }
            )

            SettingsRoute.Memory -> MemorySettingsPage(
                repo = repo,
                onRequestSmallModelWarning = { showSmallModelWarn = true },
                onRoute = { route = it }
            )

            SettingsRoute.Network -> NetworkSettingsPage(repo = repo, onRoute = { route = it })

            SettingsRoute.Api -> ApiSettingsPage(
                repo = repo,
                profiles = profiles,
                selectedProfileIdx = selectedProfileIdx,
                editingProfileName = editingProfileName,
                onEditingProfileNameChange = { editingProfileName = it },
                url = url,
                onUrlChange = { url = it },
                spec = spec,
                onSpecChange = { spec = it },
                key = key,
                onKeyChange = { key = it; typingReveal = true; keystrokeSeq++ },
                keyVisible = keyVisible,
                onRevealKey = { manualReveal = true },
                selectedModel = selectedModel,
                onSelectedModelChange = { selectedModel = it },
                models = models,
                onModelsChange = { models = it },
                loading = loading,
                onLoadingChange = { loading = it },
                errorText = errorText,
                onErrorTextChange = { errorText = it },
                onRequestDeleteProfile = { profilePendingDelete = it },
                onSelectProfile = { selectProfile(it) },
                onAddProfile = { addProfile() },
                onSaveProfile = { saveActiveProfile(selectedProfileIdx) },
                onRoute = { route = it }
            )

            SettingsRoute.Appearance -> AppearanceSettingsPage(repo = repo, onRoute = { route = it })

            SettingsRoute.Theme -> ThemeSettingsPage(repo = repo, onRoute = { route = it })

            SettingsRoute.Background -> BackgroundSettingsPage(repo = repo, onRoute = { route = it })

            SettingsRoute.Editor -> EditorSettingsPage(
                repo = repo,
                onRequestOpenLinksWarning = { showOpenLinksWarning = true },
                onRoute = { route = it }
            )

            SettingsRoute.Cloud -> CloudSettingsPage(
                repo = repo,
                showToast = { msg -> LucentToast.show(context, msg) },
                onBack = { route = SettingsRoute.Root }
            )

            SettingsRoute.Security -> SecuritySettingsPage(
                repo = repo,
                onRequestEnableAppLock = {
                    lockPw = ""; lockPwConfirm = ""; lockQuestion = ""; lockAnswer = ""
                    lockSetupError = ""
                    showAppLockSetup = true
                },
                onRequestDisableAppLock = {
                    disablePw = ""; disableError = ""
                    showAppLockDisable = true
                },
                onRequestEnableSelfDestruct = {
                    selfDestructTyped = ""
                    showSelfDestructWarning = true
                },
                onRequestCrashShieldInfo = { showCrashShieldInfo = true },
                encryptionCheckResult = encryptionCheckResult,
                onEncryptionCheckResultChange = { encryptionCheckResult = it },
                onRoute = { route = it }
            )

            SettingsRoute.Privacy -> PrivacySettingsPage(
                repo = repo,
                gateLockedOut = gateLockedOut,
                gateWiping = gateWiping,
                onSettingsGateSuccess = { settingsGateSuccess() },
                onChargeSettingsGate = { chargeSettingsGate() },
                settingsGateFeedback = { SettingsGateFeedback() },
                onRequestBlackoutWarning = { showBlackoutWarning = true },
                onRequestShareWarning = { showShareWarning = true },
                onExportLogsClick = { logsExportLauncher.launch("lucent-startup-log.txt") },
                onRoute = { route = it }
            )

            SettingsRoute.Data -> DataSettingsPage(
                repo = repo,
                lockedNotice = lockedNotice,
                lockedNoticeFileName = lockedNoticeFileName,
                lockedDismissed = lockedDismissed,
                onLockedDismissedChange = { lockedDismissed = it },
                backupStatus = backupStatus,
                onImportBackupClick = { importLauncher.launch(arrayOf("*/*")) },
                onRequestExportBackup = { showExportDialog = true },
                onRequestExportKind = { exportKind = it },
                onRequestClearNotes = { showClearNotes = true },
                onRequestClearTasks = { showClearTasks = true },
                onRequestClearChats = { showClearChats = true },
                onRequestClearData = { showClearData = true },
                onRoute = { route = it }
            )
        }
    }
}

