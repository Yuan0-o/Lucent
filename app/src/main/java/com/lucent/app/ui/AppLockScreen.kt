package com.lucent.app.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.AppLock
import com.lucent.app.data.BiometricAuth
import com.lucent.app.data.SettingsRepository
import kotlinx.coroutines.launch

/**
 * The process-wide App Lock state (task 2).
 *
 * This is a plain global holder, not persisted, so it naturally survives configuration changes (the
 * process outlives an Activity recreate) but resets when the process is killed — which is exactly
 * what "lock on next cold start" needs. [enabled] mirrors the stored setting; [locked] is the live
 * gate the UI reads.
 *
 * ### When it locks
 *
 *  - **Cold start:** the first Activity creation of a fresh process locks if the feature is on (see
 *    [markProcessStarted] / MainActivity). A configuration change does NOT re-lock, because
 *    [processStarted] is already true by then and [locked] keeps its value.
 *  - **Return from a real background:** [onStop] stamps the time; [onStart] re-locks only if the app
 *    was away longer than [GRACE_MS]. The grace window is what stops an in-app file picker or share
 *    sheet — which also fires stop/start — from demanding the password again the instant it returns,
 *    while a genuine "left the app and came back later" still locks.
 */
object AppLockController {

    private const val GRACE_MS = 30_000L

    var enabled by mutableStateOf(false)
    var locked by mutableStateOf(false)

    private var processStarted = false
    private var backgroundedAt = 0L

    /** Call once from the first onCreate of the process. Locks on a fresh start if enabled. */
    fun markProcessStarted(lockEnabled: Boolean) {
        enabled = lockEnabled
        if (!processStarted) {
            processStarted = true
            locked = lockEnabled
        }
    }

    fun onStop() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onStart() {
        if (enabled && backgroundedAt != 0L &&
            SystemClock.elapsedRealtime() - backgroundedAt > GRACE_MS
        ) {
            locked = true
        }
        backgroundedAt = 0L
    }

    fun unlock() { locked = false }
}

private enum class LockStage { ENTER_PASSWORD, ANSWER_QUESTION, SET_NEW_PASSWORD }

/**
 * The full-screen lock shown while [AppLockController.locked] is true. Rendered over the same fluid
 * background as the rest of the app so unlocking feels like part of Lucent, not a system dialog.
 *
 * Password entry is the default. "Forgot password?" reveals the security question; a correct answer
 * unlocks the path to setting a *new* password, which is saved and then unlocks the app. A wrong
 * password or answer just shows an inline error and lets the user try again — there is no lockout
 * counter, because the data is already encrypted at rest and a counter mostly punishes the owner.
 */
@Composable
fun LockScreen(paletteColors: List<Color>, backdropColor: Color, backgroundAnimated: Boolean = true) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    // R3 report: a fixed pale red is unreadable against the light theme's pale glass, so the
    // error tint follows the ink — dark themes (white ink) keep the pale red, light themes get a
    // deep red that holds its contrast.
    val errorTint = if (onGradient == Color.White) Color(0xFFFF8A80) else Color(0xFFB71C1C)

    // Enabled ⇒ credentials exist; collected here so verification has them. Until the first emission
    // arrives the unlock button stays disabled, so the opening frame can't produce a false "wrong
    // password" by comparing against an empty blob.
    val credentials by repo.appLockCredentials.collectAsState(initial = "")

    // Brute-force throttle knobs (R3 report): the lock screen never used to consult
    // PasswordAttempts at all — wrong passwords were free, and the "wipe after N lifetime
    // failures" safety net was a setting that nothing executed. These mirror Settings → Security.
    val pwFirstLimit by repo.pwFirstRoundLimit.collectAsState(
        initial = com.lucent.app.data.PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT)
    val pwLaterLimit by repo.pwLaterRoundLimit.collectAsState(
        initial = com.lucent.app.data.PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT)
    val selfDestructArmed by repo.pwSelfDestructEnabled.collectAsState(initial = false)
    val selfDestructAt by repo.pwSelfDestructThreshold.collectAsState(
        initial = com.lucent.app.data.PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD)

    // Biometric unlock is offered only when the user opted in AND the device can actually do it AND
    // the host is a FragmentActivity (BiometricPrompt's requirement). If any of these is false the
    // button simply doesn't appear and the password path is the only one shown — nothing breaks.
    val biometricEnabled by repo.appLockBiometricEnabled.collectAsState(initial = false)
    val biometricAvailable = remember { BiometricAuth.isAvailable(context) }
    val activity = context as? FragmentActivity

    var stage by remember { mutableStateOf(LockStage.ENTER_PASSWORD) }
    var password by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    // v2.4.0: every red error hint is TRANSIENT - it disappears three seconds after the LAST time
    // it was set, so a user hammering wrong passwords can never keep a stale banner pinned on
    // screen (each fresh failure restarts the three-second clock). The lockout countdown is a
    // separate, live line that ticks down once per second and clears itself when it reaches zero.
    var errorClearsAt by remember { mutableStateOf(0L) }
    var lockoutSeconds by remember { mutableStateOf(0) }

    fun showError(msg: String) {
        error = msg
        errorClearsAt = System.currentTimeMillis() + 3000L
    }

    // Auto-dismiss the transient hint 3 s after the last set (restarted whenever [error] changes).
    LaunchedEffect(error) {
        if (error.isNotBlank() && lockoutSeconds == 0) {
            val deadline = errorClearsAt.coerceAtLeast(System.currentTimeMillis() + 3000L)
            while (System.currentTimeMillis() < deadline && lockoutSeconds == 0) {
                kotlinx.coroutines.delay(200)
            }
            if (lockoutSeconds == 0 && error.isNotBlank()) error = ""
        }
    }
    // Live per-second countdown while a lockout is in force.
    LaunchedEffect(lockoutSeconds) {
        while (lockoutSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            lockoutSeconds--
        }
        error = ""
        errorClearsAt = 0L
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FluidGlassBackground(palette = paletteColors, backdropColor = backdropColor, animated = backgroundAnimated, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().frostedGlass().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = onGradient)
                Spacer(modifier = Modifier.height(8.dp))
                Text(com.lucent.app.i18n.S.lockIsLocked, color = onGradient, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(16.dp))

                when (stage) {
                    LockStage.ENTER_PASSWORD -> {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; error = "" },
                            label = { Text(com.lucent.app.i18n.S.lockPassword) },
                            singleLine = true,
                            isError = error.isNotEmpty(),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (lockoutSeconds > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                com.lucent.app.i18n.S.lockRetryIn(
                                    com.lucent.app.data.PasswordAttempts.formatRemaining(lockoutSeconds * 1000L)),
                                color = errorTint, fontSize = 13.sp
                            )
                        } else if (error.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(error, color = errorTint, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            enabled = password.isNotEmpty() && credentials.isNotEmpty(),
                            onClick = {
                                scope.launch {
                                    // R3 report: every attempt goes through the throttling engine.
                                    // A lockout in force rejects the try outright (the countdown is
                                    // shown instead of "wrong password"); a wrong password is
                                    // registered and persisted; crossing the self-destruct
                                    // threshold wipes ALL data (the same body as Settings' delete
                                    // everything) and unlocks into the empty app. A correct answer
                                    // clears the whole ladder.
                                    val state = com.lucent.app.data.PasswordAttempts.State.fromJson(
                                        repo.passwordAttemptStateOnce())
                                    val wait = com.lucent.app.data.PasswordAttempts.remainingLockoutMs(state)
                                    if (wait > 0L) {
                                        // Lockout in force: show a LIVE countdown (ticks each second)
                                        // instead of a stale one-shot message.
                                        error = ""
                                        lockoutSeconds = ((wait + 999L) / 1000L).toInt().coerceAtLeast(1)
                                        return@launch
                                    }
                                    if (AppLock.verifyPassword(credentials, password)) {
                                        password = ""
                                        error = ""
                                        errorClearsAt = 0L
                                        lockoutSeconds = 0
                                        repo.setPasswordAttemptState(
                                            com.lucent.app.data.PasswordAttempts.registerSuccess().toJson())
                                        AppLockController.unlock()
                                    } else {
                                        val next = com.lucent.app.data.PasswordAttempts.registerFailure(
                                            state, pwFirstLimit, pwLaterLimit)
                                        repo.setPasswordAttemptState(next.toJson())
                                        if (com.lucent.app.data.PasswordAttempts.shouldSelfDestruct(
                                                next, selfDestructArmed, selfDestructAt)
                                        ) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                com.lucent.app.data.wipeAllData(
                                                    context.applicationContext,
                                                    com.lucent.app.data.AppDatabase.getInstance(context.applicationContext),
                                                    repo)
                                            }
                                            error = ""
                                            lockoutSeconds = 0
                                            AppLockController.unlock()
                                        } else {
                                            val remain = com.lucent.app.data.PasswordAttempts.remainingLockoutMs(next)
                                            if (remain > 0L) {
                                                error = ""
                                                lockoutSeconds = ((remain + 999L) / 1000L).toInt().coerceAtLeast(1)
                                            } else {
                                                showError(com.lucent.app.i18n.S.lockWrongPassword)
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(com.lucent.app.i18n.S.lockUnlock) }
                        // Optional biometric shortcut. A success clears the lock exactly like a
                        // correct password; a cancel or failure just leaves the password field in
                        // place, so the fingerprint/face path is never the only way in.
                        if (biometricEnabled && biometricAvailable && activity != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    BiometricAuth.authenticate(
                                        activity = activity,
                                        title = com.lucent.app.i18n.S.biometricPromptTitle,
                                        subtitle = com.lucent.app.i18n.S.biometricPromptSubtitle,
                                        negativeButtonText = com.lucent.app.i18n.S.biometricUsePassword,
                                        onSuccess = {
                                            password = ""
                                            AppLockController.unlock()
                                        },
                                        onError = { msg -> error = msg.ifBlank { com.lucent.app.i18n.S.biometricFailed } }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = onGradient)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(com.lucent.app.i18n.S.biometricUse)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // "Forgot password?" is only offered when there is actually a security
                        // question behind it (task 9). A lock set up without one has no recovery
                        // path, and saying so plainly is kinder than a link that leads to a question
                        // nobody can answer.
                        if (AppLock.hasRecovery(credentials)) {
                            TextButton(onClick = {
                                error = ""
                                answer = ""
                                stage = LockStage.ANSWER_QUESTION
                            }) { Text(com.lucent.app.i18n.S.lockForgotPassword) }
                        } else if (credentials.isNotEmpty()) {
                            Text(
                                com.lucent.app.i18n.S.lockNoSecurityQuestion,
                                color = onGradientMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    LockStage.ANSWER_QUESTION -> {
                        val question = AppLock.question(credentials)
                        Text(
                            com.lucent.app.i18n.S.lockAnswerToReset,
                            color = onGradientMuted, fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(question.ifBlank { com.lucent.app.i18n.S.lockSecurityQuestionFallback }, color = onGradient, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it; error = "" },
                            label = { Text(com.lucent.app.i18n.S.lockAnswer) },
                            singleLine = true,
                            isError = error.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(error, color = errorTint, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            enabled = answer.isNotEmpty() && credentials.isNotEmpty(),
                            onClick = {
                                if (AppLock.verifyAnswer(credentials, answer)) {
                                    error = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                    stage = LockStage.SET_NEW_PASSWORD
                                } else {
                                    error = com.lucent.app.i18n.S.lockAnswerMismatch
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(com.lucent.app.i18n.S.lockContinue) }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = { error = ""; stage = LockStage.ENTER_PASSWORD }) {
                            Text(com.lucent.app.i18n.S.lockBackToPassword)
                        }
                    }

                    LockStage.SET_NEW_PASSWORD -> {
                        Text(com.lucent.app.i18n.S.lockChooseNewPassword, color = onGradientMuted, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it; error = "" },
                            label = { Text(com.lucent.app.i18n.S.lockNewPassword) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; error = "" },
                            label = { Text(com.lucent.app.i18n.S.lockConfirmNewPassword) },
                            singleLine = true,
                            isError = error.isNotEmpty(),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(error, color = errorTint, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            enabled = newPassword.isNotEmpty() && confirmPassword.isNotEmpty(),
                            onClick = {
                                if (newPassword != confirmPassword) {
                                    error = com.lucent.app.i18n.S.lockPasswordsDontMatch
                                    return@Button
                                }
                                val updated = AppLock.changePassword(credentials, newPassword)
                                if (updated == null) {
                                    error = com.lucent.app.i18n.S.lockCouldntUpdate
                                    return@Button
                                }
                                scope.launch { repo.setAppLockCredentials(updated) }
                                newPassword = ""
                                confirmPassword = ""
                                password = ""
                                AppLockController.unlock()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(com.lucent.app.i18n.S.lockSetPasswordUnlock) }
                    }
                }
            }
        }
    }
}
