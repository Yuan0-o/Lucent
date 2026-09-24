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
import androidx.compose.foundation.ScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.lucent.app.AppNavigation
import com.lucent.app.BackClaim
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.AppLock
import com.lucent.app.data.AttachmentLimits
import com.lucent.app.data.BackupManager
import com.lucent.app.data.FontStore
import com.lucent.app.data.SettingsCache
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
import com.lucent.app.ui.settings.SplashSettingsPage
import com.lucent.app.ui.settings.SecuritySettingsPage
import com.lucent.app.ui.settings.ThemeSettingsPage
import com.lucent.app.ui.settings.AboutSettingsPage
import com.lucent.app.ui.settings.AdvancedSettingsPage
import com.lucent.app.ui.settings.LicenceSettingsPage
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.text.style.TextAlign

internal enum class SettingsRoute { Root, Language, Assistant, Personalization, Memory, Network, Api, LocalModel, Appearance, Theme, Background, Splash, Editor, Cloud, Security, Privacy, Data, About, Licences, Advanced }

internal enum class ExportKind { NOTES, TASKS }

private const val WRONG_PASSWORD = "__wrong_password__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(active: Boolean = true) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    val appVersionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            ?: com.lucent.app.LucentBuild.VERSION
    }
    val appBuildNumber = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString()
        }.getOrNull() ?: com.lucent.app.LucentBuild.BUILD_NUMBER
    }

    val savedUrl by repo.baseUrl.collectAsState(initial = SettingsCache.baseUrl)
    val savedSpec by repo.apiSpec.collectAsState(initial = SettingsCache.apiSpec)
    val savedKey by repo.apiKey.collectAsState(initial = SettingsCache.apiKey)
    val savedModel by repo.model.collectAsState(initial = SettingsCache.model)
    val savedFont by repo.font.collectAsState(initial = SettingsCache.font)
    val savedAssistantName by repo.assistantName.collectAsState(initial = SettingsCache.assistantName ?: "Lucent")
    val savedAssistantStyle by repo.assistantStyle.collectAsState(initial = SettingsCache.assistantStyle)
    var showSmallModelWarn by remember { mutableStateOf(false) }
    val localModelEnabled by repo.localModelEnabled.collectAsState(initial = SettingsCache.localModelEnabled)

    var url by remember(savedUrl) { mutableStateOf(savedUrl) }
    var spec by remember(savedSpec) { mutableStateOf(savedSpec) }
    var key by remember(savedKey) { mutableStateOf(savedKey) }
    var selectedModel by remember(savedModel) { mutableStateOf(savedModel) }
    var assistantName by remember(savedAssistantName) { mutableStateOf(savedAssistantName) }
    var assistantStyle by remember(savedAssistantStyle) { mutableStateOf(savedAssistantStyle) }

    val savedProfilesJson by repo.apiProfilesJson.collectAsState(initial = SettingsCache.apiProfilesJson)
    val savedSelectedIdx by repo.apiProfileSelected.collectAsState(initial = SettingsCache.apiProfileSelected)
    val profiles = remember(savedProfilesJson, savedUrl, savedSpec, savedKey, savedModel) {
        val parsed = com.lucent.app.data.ApiProfiles.parse(savedProfilesJson)
        when {
            parsed.isNotEmpty() -> parsed
            savedProfilesJson.isNotBlank() -> emptyList()
            else -> listOf(
                com.lucent.app.data.ApiProfile(
                    name = "API 1", spec = savedSpec, baseUrl = savedUrl, apiKey = savedKey, model = savedModel,
                    provider = com.lucent.app.data.ApiProviders.match(savedSpec, savedUrl),
                    selectedModels = listOfNotNull(savedModel.trim().takeIf { it.isNotBlank() })
                )
            )
        }
    }
    val selectedProfileIdx = savedSelectedIdx.coerceIn(0, (profiles.size - 1).coerceAtLeast(0))

    var provider by remember(savedProfilesJson, selectedProfileIdx) {
        mutableStateOf(
            profiles.getOrNull(selectedProfileIdx)?.provider ?: com.lucent.app.data.ApiProviders.CUSTOM
        )
    }
    var models by remember(savedProfilesJson, selectedProfileIdx) {
        mutableStateOf(profiles.getOrNull(selectedProfileIdx)?.selectedModels ?: emptyList())
    }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf("") }

    val appLockOn by repo.appLockEnabled.collectAsState(initial = SettingsCache.appLockEnabled)

    val pwFirstRound by repo.pwFirstRoundLimit.collectAsState(
        initial = SettingsCache.pwFirstRoundLimit
    )
    val pwLaterRound by repo.pwLaterRoundLimit.collectAsState(
        initial = SettingsCache.pwLaterRoundLimit
    )
    val selfDestructOn by repo.pwSelfDestructEnabled.collectAsState(initial = SettingsCache.pwSelfDestructEnabled)
    val selfDestructThreshold by repo.pwSelfDestructThreshold.collectAsState(
        initial = SettingsCache.pwSelfDestructThreshold
    )

    val gateAttemptJson by repo.passwordAttemptState.collectAsState(initial = SettingsCache.passwordAttemptState)
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

    fun settingsGateSuccess() {
        gateFailedOnce = false
        scope.launch {
            repo.setPasswordAttemptState(com.lucent.app.data.PasswordAttempts.registerSuccess().toJson())
        }
    }

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

    var showBlackoutWarning by remember { mutableStateOf(false) }
    var showCrashShieldInfo by remember { mutableStateOf(false) }
    var showOpenLinksWarning by remember { mutableStateOf(false) }
    var showSelfDestructWarning by remember { mutableStateOf(false) }
    var selfDestructTyped by remember { mutableStateOf("") }
    LaunchedEffect(backupStatus) {
        if (backupStatus.isNotBlank()) {
            kotlinx.coroutines.delay(8000)
            backupStatus = ""
        }
    }

    var showAppLockSetup by remember { mutableStateOf(false) }
    var lockPw by remember { mutableStateOf("") }
    var lockPwConfirm by remember { mutableStateOf("") }
    var lockQuestion by remember { mutableStateOf("") }
    var lockAnswer by remember { mutableStateOf("") }
    var lockSetupError by remember { mutableStateOf("") }

    var showNoRecoveryWarning by remember { mutableStateOf(false) }

    val appLockCreds by repo.appLockCredentials.collectAsState(initial = "")
    var showAppLockDisable by remember { mutableStateOf(false) }
    var disablePw by remember { mutableStateOf("") }
    var disableError by remember { mutableStateOf("") }

    var showShareWarning by remember { mutableStateOf(false) }

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

    var showExportDialog by remember { mutableStateOf(false) }
    var exportPasswordDraft by remember { mutableStateOf("") }
    var exportModules by remember { mutableStateOf(BackupManager.DEFAULT_MODULES) }
    var exportNoteIds by remember { mutableStateOf<Set<Long>?>(null) }
    var exportTaskIds by remember { mutableStateOf<Set<Long>?>(null) }
    var exportConversationIds by remember { mutableStateOf<Set<Long>?>(null) }
    var exportApiProfileNames by remember { mutableStateOf<Set<String>?>(null) }
    val realProfiles = remember(savedProfilesJson) { com.lucent.app.data.ApiProfiles.parse(savedProfilesJson) }
    var itemPicker by remember { mutableStateOf<ExportItemKind?>(null) }
    var allNotes by remember { mutableStateOf<List<com.lucent.app.data.Note>>(emptyList()) }
    var allTasks by remember { mutableStateOf<List<com.lucent.app.data.Task>>(emptyList()) }
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
    var restoreModules by remember { mutableStateOf(BackupManager.DEFAULT_MODULES) }
    var restoreConversationIds by remember { mutableStateOf<Set<Long>?>(null) }
    var restoreApiProfileNames by remember { mutableStateOf<Set<String>?>(null) }
    var restoreItemPicker by remember { mutableStateOf<ExportItemKind?>(null) }
    var apiLimitPrompt by remember { mutableStateOf(false) }
    var exportPasswordVisible by remember { mutableStateOf(false) }

    var importSourceUri by remember { mutableStateOf<Uri?>(null) }
    var importPasswordPrompt by remember { mutableStateOf(false) }
    var importPasswordDraft by remember { mutableStateOf("") }
    var importPasswordError by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<BackupManager.BackupPreview?>(null) }
    var importResultSheet by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var backupBusyLabel by remember { mutableStateOf<String?>(null) }
    var backupOpJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun uriSource(uri: Uri) = BackupManager.BackupSource {
        context.contentResolver.openInputStream(uri)
            ?: throw java.io.FileNotFoundException("Backup could not be opened")
    }
    fun releaseImportGrant(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Throwable) {
        }
    }
    fun discardImportSource() {
        importSourceUri?.let { releaseImportGrant(it) }
        importSourceUri = null
        importPasswordPrompt = false
    }
    val runRestore: (BackupManager.BackupPreview, Set<BackupManager.BackupModule>, Set<Long>?, Set<String>?) -> Unit =
        { preview, modules, convIds, apiNames ->
            importPreview = null
            apiLimitPrompt = false
            val sourceUri = importSourceUri
            val job = scope.launch {
                backupBusyLabel = S.importingBackup
                var committed = true
                var outcome: String? = null
                try {
                    withContext(Dispatchers.IO) {
                        outcome = try {
                            BackupManager.commit(
                                context, db, repo, preview, modules, convIds, apiNames,
                                source = sourceUri?.let { uriSource(it) }
                            )
                        } catch (t: kotlinx.coroutines.CancellationException) {
                            throw t
                        } catch (t: Throwable) {
                            committed = false
                            S.importFailed(t.message ?: "")
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    if (outcome == null) backupStatus = S.importCancelledStatus
                } finally {
                    outcome?.let { result ->
                        backupStatus = result
                        importResultSheet = committed to result
                        com.lucent.app.reminders.Notifications.ensureChannel(context)
                        com.lucent.app.data.DatabaseEncryption.clearLockedNotice(context)
                    }
                    importSourceUri = null
                    importPasswordPrompt = false
                    sourceUri?.let { releaseImportGrant(it) }
                    backupBusyLabel = null
                    backupOpJob = null
                }
            }
            backupOpJob = job
        }
    val lockedNotice = remember { com.lucent.app.data.DatabaseEncryption.lockedNotice(context) }
    var lockedDismissed by remember { mutableStateOf(false) }
    val lockedNoticeFileName = remember(lockedNotice) {
        lockedNotice?.let { Regex("\"([^\"]+)\"").find(it)?.groupValues?.getOrNull(1) }
    }
    var showClearData by remember { mutableStateOf(false) }
    var showClearNotes by remember { mutableStateOf(false) }
    var showClearTasks by remember { mutableStateOf(false) }
    var showClearChats by remember { mutableStateOf(false) }
    var profilePendingDelete by remember { mutableStateOf<Int?>(null) }
    var editingProfileName by remember(savedProfilesJson, selectedProfileIdx) {
        mutableStateOf(profiles.getOrNull(selectedProfileIdx)?.name ?: "")
    }

    var lmRefresh by remember { mutableStateOf(0) }
    val lmIndex = remember(lmRefresh) { LocalModelStore.index(context) }
    val lmModels = lmIndex.slots
    val lmActiveId = lmIndex.activeId
    val lmCanImportMore = lmModels.size < LocalModelStore.MAX_MODELS
    var lmImporting by remember { mutableStateOf(false) }
    var lmError by remember { mutableStateOf("") }
    var lmSlotPendingDelete by remember { mutableStateOf<LocalModelStore.ModelSlot?>(null) }
    var lmRenameTarget by remember { mutableStateOf<LocalModelStore.ModelSlot?>(null) }
    var lmRenameText by remember { mutableStateOf("") }
    var lmPendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var lmImportName by remember { mutableStateOf("") }
    var lmConfirmToolsOn by remember { mutableStateOf(false) }
    var lmConfirmGpuOn by remember { mutableStateOf(false) }
    var lmConfirmUseLocalOn by remember { mutableStateOf(false) }
    var lmConfirmBackgroundOn by remember { mutableStateOf(false) }

    val lmImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !lmImporting) {
            lmError = ""
            val picked = try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (_: Throwable) { null }
            lmImportName = (picked ?: "").substringBeforeLast('.').take(60)
            lmPendingImportUri = uri
        }
    }


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

    fun selectLocalModel(id: String) {
        if (id == lmActiveId) return
        scope.launch {
            withContext(Dispatchers.IO) {
                LocalLlm.shutdown()
                LocalModelStore.setActive(context, id)
            }
            lmRefresh++
        }
    }

    fun deleteLocalModel(slot: LocalModelStore.ModelSlot) {
        scope.launch {
            val wasActive = slot.id == lmActiveId
            withContext(Dispatchers.IO) {
                if (wasActive) LocalLlm.shutdown()
                LocalModelStore.delete(context, slot.id)
                if (LocalModelStore.slots(context).isEmpty()) repo.setLocalModelEnabled(false)
            }
            lmRefresh++
            LucentToast.show(context.applicationContext, S.lmDeletedToast)
        }
    }

    var fontRefresh by remember { mutableStateOf(0) }
    val importedFonts = remember(fontRefresh) { FontStore.index(context) }.slots
    val fontCanImportMore = importedFonts.size < FontStore.MAX_FONTS
    var fontImporting by remember { mutableStateOf(false) }
    var fontError by remember { mutableStateOf("") }
    var fontPendingDelete by remember { mutableStateOf<FontStore.FontSlot?>(null) }
    var fontPendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var fontImportName by remember { mutableStateOf("") }

    val fontImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !fontImporting) {
            fontError = ""
            val picked = try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (_: Throwable) { null }
            fontImportName = (picked ?: "").substringBeforeLast('.').take(60)
            fontPendingImportUri = uri
        }
    }

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

    val route = AppNavigation.settingsRoute

    fun setRoute(next: SettingsRoute) {
        AppNavigation.rememberSettingsRoute(next)
    }

    val assistantDirty = route == SettingsRoute.Personalization && (
        assistantName.ifBlank { "Lucent" } != savedAssistantName ||
            assistantStyle != savedAssistantStyle
        )
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val appContext = context.applicationContext
    fun persistAssistantSettings() {
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

    fun saveActiveProfile(
        idx: Int,
        providerId: String,
        selectedModels: List<String>
    ) {
        val updated = profiles.toMutableList()
        val edited = com.lucent.app.data.ApiProfile(
            name = editingProfileName.trim().ifBlank { "API ${idx + 1}" },
            spec = spec,
            baseUrl = url.trim(),
            apiKey = key.trim(),
            model = selectedModel,
            provider = providerId,
            selectedModels = selectedModels
        )
        if (idx in updated.indices) updated[idx] = edited else updated.add(edited)
        val newSelected = idx.coerceIn(0, updated.size - 1)
        AppScope.io.launch {
            repo.saveApiProfiles(updated, newSelected)
            withContext(Dispatchers.Main) { LucentToast.show(appContext, S.apiSavedToast) }
        }
    }

    fun selectProfile(idx: Int) {
        val p = profiles.getOrNull(idx) ?: return
        url = p.baseUrl; spec = p.spec; key = p.apiKey; selectedModel = p.model
        editingProfileName = p.name
        AppScope.io.launch { repo.saveApiProfiles(profiles, idx) }
    }

    fun addProfile() {
        if (profiles.size >= com.lucent.app.data.ApiProfiles.MAX) return
        val defaultName = com.lucent.app.data.ApiProfiles.nextDefaultName(profiles)
        val newList = profiles + com.lucent.app.data.ApiProfile(name = defaultName)
        val newIdx = newList.size - 1
        url = ""; spec = "openai"; key = ""; selectedModel = ""; editingProfileName = defaultName
        AppScope.io.launch { repo.saveApiProfiles(newList, newIdx) }
    }

    fun deleteProfile(idx: Int) {
        if (idx !in profiles.indices) return
        val newList = profiles.toMutableList().also { it.removeAt(idx) }
        if (newList.isEmpty()) {
            url = ""; spec = "openai"; key = ""; selectedModel = ""; editingProfileName = ""
        }
        val newSelected = if (newList.isEmpty()) 0 else selectedProfileIdx.coerceIn(0, newList.size - 1)
        AppScope.io.launch { repo.saveApiProfiles(newList, newSelected) }
    }

    fun leavePersonalization() {
        if (assistantDirty) showUnsavedDialog = true else setRoute(SettingsRoute.Assistant)
    }

    fun goBack() {
        when (route) {
            SettingsRoute.Personalization -> leavePersonalization()
            SettingsRoute.Memory -> setRoute(SettingsRoute.Assistant)
            SettingsRoute.Network -> setRoute(SettingsRoute.Assistant)
            SettingsRoute.Api -> setRoute(SettingsRoute.Assistant)
            SettingsRoute.LocalModel -> setRoute(SettingsRoute.Assistant)
            SettingsRoute.Theme, SettingsRoute.Background, SettingsRoute.Splash -> setRoute(SettingsRoute.Appearance)
            SettingsRoute.Licences -> setRoute(SettingsRoute.About)
            SettingsRoute.Language, SettingsRoute.Assistant, SettingsRoute.Appearance, SettingsRoute.Editor,
            SettingsRoute.Cloud, SettingsRoute.Security, SettingsRoute.Privacy, SettingsRoute.Data, SettingsRoute.About, SettingsRoute.Advanced -> setRoute(SettingsRoute.Root)
            else -> setRoute(SettingsRoute.Root)
        }
    }

    LaunchedEffect(route) {
        if (route == SettingsRoute.LocalModel) lmError = ""
        if (route == SettingsRoute.Api) errorText = ""
        if (route == SettingsRoute.Data) backupStatus = ""
        if (route == SettingsRoute.Language) fontError = ""
    }

    SideEffect {
        if (assistantDirty) {
            UnsavedChangesGuard.register("settings", ::persistAssistantSettings, ::discardAssistantSettings)
        } else {
            UnsavedChangesGuard.clear("settings")
        }
    }
    DisposableEffect(Unit) { onDispose { UnsavedChangesGuard.clear("settings") } }


    if (showSmallModelWarn) {
        AlertDialog(
            onDismissRequest = { showSmallModelWarn = false },
            title = { Text(S.smallModelModeTitle) },
            text = { Text(S.smallModelModeWarn) },
            confirmButton = {
                TextButton(onClick = {
                    showSmallModelWarn = false
                    SettingsCache.smallModelModeEnabled = true
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
                    setRoute(SettingsRoute.Assistant)
                }) { Text(S.actionSave) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        discardAssistantSettings()
                        showUnsavedDialog = false
                        setRoute(SettingsRoute.Assistant)
                    }) { Text(S.actionDiscard) }
                    TextButton(onClick = { showUnsavedDialog = false }) { Text(S.actionCancel) }
                }
            }
        )
    }
    if (showUnsavedDialog) { UnsavedChangesDialog() }

    BackHandler(enabled = route != SettingsRoute.Root) { goBack() }
    BackClaim(active && route != SettingsRoute.Root)

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


    var exportPassword by remember { mutableStateOf<String?>(null) }

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
                            throw e
                        } catch (e: Exception) {
                            S.exportFailed(e.message ?: "")
                        }
                    }
                    backupStatus = result
                } catch (e: kotlinx.coroutines.CancellationException) {
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                        try {
                            android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
                        } catch (_: Throwable) {
                        }
                    }
                    backupStatus = S.exportCancelledStatus
                } finally {
                    exportInFlight = false
                    backupBusyLabel = null
                    backupOpJob = null
                }
            }
            backupOpJob = job
        } else {
            exportInFlight = false
        }
    }

    fun beginExport(password: String?) {
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

                    Text(S.backupChooseWhat, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
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
                    val importedFontBytes = remember(fontRefresh) { FontStore.totalFontBytes(context) }
                    if (importedFontBytes > 0L) {
                        Text(
                            S.backupModSettingsFontsDesc(AttachmentLimits.formatBytes(importedFontBytes)),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                        )
                    }
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

    itemPicker?.let { kind ->
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


    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val job = scope.launch {
                backupBusyLabel = S.importingBackup
                try {
                    discardImportSource()
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Throwable) {
                    }
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
                    SettingsGateFeedback()
                }
            },
            confirmButton = {
                Button(
                    enabled = importPasswordDraft.isNotEmpty() && !gateLockedOut && !gateWiping,
                    onClick = {
                        val attempt = importPasswordDraft
                        val job = scope.launch {
                            backupBusyLabel = S.importingBackup
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    try {
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
                                            importPasswordError = true
                                            chargeSettingsGate()
                                        } else {
                                            backupStatus = S.importFailed(error.message ?: "")
                                            discardImportSource()
                                        }
                                    }
                                )
                            } catch (e: kotlinx.coroutines.CancellationException) {
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

    @Composable
    @NonRestartableComposable
    fun ImportPreviewDialog(preview: BackupManager.BackupPreview) {
        AlertDialog(
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
                        val apiSelected = BackupManager.BackupModule.API in restoreModules
                        val isMultiApiBackup = preview.apiProfileNames.isNotEmpty()
                        val effectiveApiNames: Set<String>? = when {
                            !apiSelected -> null
                            !isMultiApiBackup -> null
                            else -> restoreApiProfileNames ?: preview.apiProfileNames.toSet()
                        }
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

    if (active) backupBusyLabel?.let { busyLabel ->
        AlertDialog(
            onDismissRequest = {  },
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

    fun applyAppLock() {
        val creds = AppLock.createCredentials(lockPw, lockQuestion, lockAnswer)
        scope.launch { repo.setAppLock(true, creds) }
        SettingsCache.appLockEnabled = true
        AppLockController.enabled = true
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
                        lockQuestion.isNotBlank() && lockAnswer.isBlank() ->
                            lockSetupError = S.lockErrNeedAnswer
                        lockAnswer.isNotBlank() && lockQuestion.isBlank() ->
                            lockSetupError = S.lockErrNeedQuestion
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

    @Composable
    @NonRestartableComposable
    fun ShareWarningDialog() {
        AlertDialog(
            onDismissRequest = { showShareWarning = false },
            title = { Text(S.shareWarnTitle) },
            text = { Text(S.shareWarnBody) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        repo.setSystemIntegrationEnabled(true)
                        SettingsCache.systemIntegrationEnabled = true
                    }
                    ShareIntegration.setEnabled(context, true)
                    showShareWarning = false
                    LucentToast.show(context, S.systemIntegrationOnToast)
                }) { Text(S.turnOn) }
            },
            dismissButton = {
                TextButton(onClick = { showShareWarning = false }) { Text(S.actionCancel) }
            }
        )
    }
    if (showShareWarning) { ShareWarningDialog() }


    @Composable
    @NonRestartableComposable
    fun BlackoutWarningDialog() {
        AlertDialog(
            onDismissRequest = { showBlackoutWarning = false },
            title = { Text(S.blackoutWarnTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(S.blackoutWarnBody)
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
                        lockPw = ""; lockPwConfirm = ""; lockQuestion = ""; lockAnswer = ""
                        lockSetupError = ""
                        showAppLockSetup = true
                    } else {
                        scope.launch {
                            repo.setBlackoutEnabled(true)
                            SettingsCache.blackoutEnabled = true
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
                        repo.setCrashShieldEnabled(true)
                        SettingsCache.crashShieldEnabled = true
                        SettingsCache.startupLoggingEnabled = true
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
                    scope.launch {
                        repo.setOpenLinksExternally(true)
                        SettingsCache.openLinksExternally = true
                    }
                }) { Text(S.turnOn) }
            },
            dismissButton = {
                TextButton(onClick = { showOpenLinksWarning = false }) { Text(S.actionCancel) }
            }
        )
    }
    if (showOpenLinksWarning) { OpenLinksWarningDialog() }

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
                        scope.launch {
                            repo.setPwSelfDestructEnabled(true)
                            SettingsCache.pwSelfDestructEnabled = true
                        }
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
                    requireLockAuth { com.lucent.app.data.backgroundWrite(context, "clear all data") {
                        com.lucent.app.data.wipeAllData(appContext, db, repo)

                        withContext(Dispatchers.Main) {
                            backupStatus = ""
                            lmRefresh++
                            fontRefresh++
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
                    requireLockAuth { com.lucent.app.data.backgroundWrite(context, "clear notes") {
                        db.noteVersionDao().clearAll()
                        db.noteDao().clearAll()
                        com.lucent.app.data.AttachmentMigration.pruneOrphans(appContext)
                        com.lucent.app.widget.WidgetUpdater.refreshContent(appContext)
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
                    requireLockAuth { com.lucent.app.data.backgroundWrite(context, "clear tasks") {
                        db.taskDao().getAllOnce().forEach {
                            com.lucent.app.reminders.ReminderScheduler.cancel(appContext, it.id)
                        }
                        db.taskVersionDao().clearAll()
                        db.taskDao().clearAll()
                        com.lucent.app.data.AttachmentMigration.pruneOrphans(appContext)
                        com.lucent.app.widget.WidgetUpdater.refreshContent(appContext)
                        withContext(Dispatchers.Main) { LucentToast.show(appContext, S.tasksClearedToast) }
                    }
                } }) { Text(S.deleteTasksBtn) }
            },
            dismissButton = { TextButton(onClick = { showClearTasks = false }) { Text(S.actionCancel) } }
        )
    }
    if (showClearTasks) { ClearTasksDialog() }

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
                    requireLockAuth { com.lucent.app.data.backgroundWrite(context, "clear chats") {
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
                    SettingsCache.localModelEnabled = true
                    AppScope.io.launch { repo.setLocalModelEnabled(true) }
                }) { Text(S.lmWarnEnableAnyway) }
            },
            dismissButton = { TextButton(onClick = { lmConfirmUseLocalOn = false }) { Text(S.actionCancel) } }
        )
    }
    if (lmConfirmUseLocalOn) { ConfirmUseLocalDialog() }

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
                    SettingsCache.localToolsEnabled = true
                    AppScope.io.launch { repo.setLocalToolsEnabled(true) }
                }) { Text(S.lmWarnEnableAnyway) }
            },
            dismissButton = { TextButton(onClick = { lmConfirmToolsOn = false }) { Text(S.actionCancel) } }
        )
    }
    if (lmConfirmToolsOn) { ConfirmToolsDialog() }

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
                    SettingsCache.localGpuEnabled = true
                    AppScope.io.launch { repo.setLocalGpuEnabled(true) }
                }) { Text(S.lmWarnEnableAnyway) }
            },
            dismissButton = { TextButton(onClick = { lmConfirmGpuOn = false }) { Text(S.actionCancel) } }
        )
    }
    if (lmConfirmGpuOn) { ConfirmGpuDialog() }

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
                    SettingsCache.localBackgroundReplyEnabled = true
                    AppScope.io.launch { repo.setLocalBackgroundReplyEnabled(true) }
                }) { Text(S.lmWarnEnableAnyway) }
            },
            dismissButton = { TextButton(onClick = { lmConfirmBackgroundOn = false }) { Text(S.actionCancel) } }
        )
    }
    if (lmConfirmBackgroundOn) { ConfirmBackgroundDialog() }

    val notesForExport by remember { db.noteDao().getAll() }.collectAsState(initial = emptyList())
    val tasksForExport by remember { db.taskDao().getAll() }.collectAsState(initial = emptyList())
    var exportKind by remember { mutableStateOf<ExportKind?>(null) }
    LaunchedEffect(active) {
        if (!active) exportKind = null
    }
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingExportName by remember { mutableStateOf("lucent-export.md") }
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

    val routeScrolls = remember { mutableMapOf<SettingsRoute, ScrollState>() }
    val rootScroll = routeScrolls.getOrPut(route) { ScrollState(SettingsScrollMemory.of(route)) }
    LaunchedEffect(rootScroll) {
        snapshotFlow { rootScroll.value }.collect { SettingsScrollMemory.write(route, it) }
    }

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
                doodlesOf = { com.lucent.app.data.DoodleExport.canvasesOf(it) },
                onExport = { subset, format, atts, canvases ->
                    val doc = com.lucent.app.data.DocumentExport.exportNotes(subset, format)
                    val canvasFiles = canvases.groupBy { it.ownerId }.flatMap { (ownerId, group) ->
                        val owner = subset.firstOrNull { it.id == ownerId }
                        val title = owner?.title.orEmpty().ifBlank { S.untitled }
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
            SettingsRoute.Root -> RootSettingsPage(onRoute = { setRoute(it) })

            SettingsRoute.Language -> LanguageSettingsPage(
                repo = repo,
                importedFonts = importedFonts,
                fontCanImportMore = fontCanImportMore,
                fontImporting = fontImporting,
                fontError = fontError,
                onRequestDeleteFont = { fontPendingDelete = it },
                onImportFontClick = { fontImportLauncher.launch(arrayOf("*/*")) },
                onRoute = { setRoute(it) }
            )

            SettingsRoute.Assistant -> AssistantSettingsPage(
                repo = repo,
                profiles = profiles,
                selectedProfileIdx = selectedProfileIdx,
                onRoute = { setRoute(it) }
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
                onRoute = { setRoute(it) }
            )

            SettingsRoute.Personalization -> PersonalizationSettingsPage(
                repo = repo,
                assistantName = assistantName,
                onAssistantNameChange = { assistantName = it },
                assistantStyle = assistantStyle,
                onAssistantStyleChange = { assistantStyle = it },
                onSave = { persistAssistantSettings() },
                onRequestSmallModelWarning = { showSmallModelWarn = true },
                onBack = { leavePersonalization() }
            )

            SettingsRoute.Memory -> MemorySettingsPage(
                repo = repo,
                onRoute = { setRoute(it) }
            )

            SettingsRoute.Network -> NetworkSettingsPage(repo = repo, onRoute = { setRoute(it) })

            SettingsRoute.Api -> ApiSettingsPage(
                repo = repo,
                profiles = profiles,
                selectedProfileIdx = selectedProfileIdx,
                editingProfileName = editingProfileName,
                onEditingProfileNameChange = { editingProfileName = it },
                provider = provider,
                onProviderChange = { id ->
                    if (id != provider) {
                        val wasPreset = com.lucent.app.data.ApiProviders.isPreset(provider)
                        provider = id
                        val preset = com.lucent.app.data.ApiProviders.preset(id)
                        if (preset != null) {
                            url = preset.baseUrl
                            spec = preset.spec
                        } else {
                            url = ""
                            if (wasPreset) {
                                spec = "openai"
                                selectedModel = ""
                                models = emptyList()
                            }
                        }
                    }
                },
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
                onModelsChange = { picked -> models = picked },
                loading = loading,
                onLoadingChange = { loading = it },
                errorText = errorText,
                onErrorTextChange = { errorText = it },
                onRequestDeleteProfile = { profilePendingDelete = it },
                onSelectProfile = { selectProfile(it) },
                onAddProfile = { addProfile() },
                onSaveProfile = { saveActiveProfile(selectedProfileIdx, provider, models) },
                onRoute = { setRoute(it) }
            )

            SettingsRoute.Appearance -> AppearanceSettingsPage(repo = repo, onRoute = { setRoute(it) })

            SettingsRoute.Theme -> ThemeSettingsPage(repo = repo, onRoute = { setRoute(it) })

            SettingsRoute.Background -> BackgroundSettingsPage(repo = repo, onRoute = { setRoute(it) })

            SettingsRoute.Splash -> SplashSettingsPage(repo = repo, onRoute = { setRoute(it) })

            SettingsRoute.Editor -> EditorSettingsPage(
                repo = repo,
                onRequestOpenLinksWarning = { showOpenLinksWarning = true },
                onRoute = { setRoute(it) }
            )

            SettingsRoute.Cloud -> CloudSettingsPage(
                repo = repo,
                showToast = { msg -> LucentToast.show(context, msg) },
                onBack = { setRoute(SettingsRoute.Root) }
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
                onRoute = { setRoute(it) }
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
                onRoute = { setRoute(it) }
            )

            SettingsRoute.About -> AboutSettingsPage(
                repo = repo,
                onRoute = { setRoute(it) },
                onOpenUrl = { url ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    context.startActivity(intent)
                },
                versionName = appVersionName,
                buildNumber = appBuildNumber
            )

            SettingsRoute.Licences -> LicenceSettingsPage(
                onRoute = { setRoute(it) },
                onOpenUrl = { url ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    context.startActivity(intent)
                }
            )

            SettingsRoute.Advanced -> {
                val privilegedOn by repo.privilegedEnabled.collectAsState(initial = SettingsCache.privilegedEnabled)
                var shizukuReady by remember { mutableStateOf(com.lucent.app.data.ShizukuShell.isReady()) }
                var shizukuRunning by remember { mutableStateOf(com.lucent.app.data.ShizukuShell.isServiceRunning()) }
                var shizukuInstalled by remember { mutableStateOf(com.lucent.app.data.ShizukuShell.isInstalled(context)) }
                var shizukuUid by remember { mutableStateOf(com.lucent.app.data.ShizukuShell.privilegeUid()) }
                val refreshShizuku = {
                    shizukuRunning = com.lucent.app.data.ShizukuShell.isServiceRunning()
                    shizukuInstalled = com.lucent.app.data.ShizukuShell.isInstalled(context)
                    shizukuReady = com.lucent.app.data.ShizukuShell.isReady()
                    shizukuUid = com.lucent.app.data.ShizukuShell.privilegeUid()
                }
                DisposableEffect(Unit) {
                    com.lucent.app.data.ShizukuShell.observePermission { granted ->
                        shizukuReady = granted
                        shizukuUid = com.lucent.app.data.ShizukuShell.privilegeUid()
                        shizukuRunning = com.lucent.app.data.ShizukuShell.isServiceRunning()
                    }
                    com.lucent.app.data.ShizukuShell.observeBinder { running ->
                        shizukuRunning = running
                        shizukuReady = com.lucent.app.data.ShizukuShell.isReady()
                        shizukuUid = com.lucent.app.data.ShizukuShell.privilegeUid()
                    }
                    onDispose {
                        com.lucent.app.data.ShizukuShell.observePermission(null)
                        com.lucent.app.data.ShizukuShell.observeBinder(null)
                    }
                }
                LaunchedEffect(Unit) {
                    shizukuRunning = com.lucent.app.data.ShizukuShell.isServiceRunning()
                    shizukuReady = com.lucent.app.data.ShizukuShell.isReady()
                    shizukuUid = com.lucent.app.data.ShizukuShell.privilegeUid()
                }
                val shizukuStatus = when {
                    !privilegedOn -> S.shizukuStatusOff
                    !shizukuInstalled -> S.shizukuStatusNoApp
                    !shizukuRunning -> S.shizukuStatusNoService
                    !shizukuReady -> S.shizukuStatusNoPermission
                    shizukuUid >= 0 -> "${S.shizukuReady} \u00b7 uid $shizukuUid"
                    else -> S.shizukuReady
                }
                val shizukuAction = when {
                    !privilegedOn -> null
                    !shizukuInstalled -> S.shizukuActionInstall
                    !shizukuRunning -> S.shizukuActionOpen
                    !shizukuReady -> S.shizukuActionGrant
                    else -> null
                }
                AdvancedSettingsPage(
                    ui = com.lucent.app.ui.settings.AdvancedPrivilegeUi(
                        title = S.shizukuTitle,
                        description = S.shizukuEnableDesc,
                        status = shizukuStatus,
                        enabled = privilegedOn,
                        ready = shizukuReady,
                        busy = false,
                        actionLabel = shizukuAction
                    ),
                    onToggle = { wanted ->
                        SettingsCache.privilegedEnabled = wanted
                        scope.launch { repo.setPrivilegedEnabled(wanted) }
                        if (wanted) {
                            com.lucent.app.data.ShizukuWatcher.ensureStarted(context)
                            com.lucent.app.data.ShizukuWatcher.checkNow(context)
                        } else {
                            com.lucent.app.data.ShizukuWatcher.stop()
                        }
                        refreshShizuku()
                    },
                    onAction = {
                        when {
                            !shizukuInstalled -> com.lucent.app.data.ShizukuShell.openDownloadPage(context)
                            !shizukuRunning -> com.lucent.app.data.ShizukuShell.openManager(context)
                            else -> com.lucent.app.data.ShizukuShell.requestPermission(context)
                        }
                        refreshShizuku()
                    },
                    onRoute = { setRoute(it) }
                )
            }

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
                onRoute = { setRoute(it) }
            )
        }
    }
}

