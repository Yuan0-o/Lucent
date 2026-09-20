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

object AppLockController {

    private const val GRACE_MS = 30_000L

    var enabled by mutableStateOf(false)
    var locked by mutableStateOf(false)

    private var processStarted = false
    private var backgroundedAt = 0L

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

@Composable
fun LockScreen(paletteColors: List<Color>, backdropColor: Color, backgroundAnimated: Boolean = true) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val errorTint = if (onGradient == Color.White) Color(0xFFFF8A80) else Color(0xFFB71C1C)

    val credentials by repo.appLockCredentials.collectAsState(initial = "")

    val pwFirstLimit by repo.pwFirstRoundLimit.collectAsState(
        initial = com.lucent.app.data.PasswordAttempts.DEFAULT_FIRST_ROUND_LIMIT)
    val pwLaterLimit by repo.pwLaterRoundLimit.collectAsState(
        initial = com.lucent.app.data.PasswordAttempts.DEFAULT_LATER_ROUND_LIMIT)
    val selfDestructArmed by repo.pwSelfDestructEnabled.collectAsState(
        initial = com.lucent.app.data.SettingsCache.pwSelfDestructEnabled)
    val selfDestructAt by repo.pwSelfDestructThreshold.collectAsState(
        initial = com.lucent.app.data.PasswordAttempts.DEFAULT_SELF_DESTRUCT_THRESHOLD)

    val biometricEnabled by repo.appLockBiometricEnabled.collectAsState(
        initial = com.lucent.app.data.SettingsCache.appLockBiometricEnabled)
    val biometricAvailable = remember { BiometricAuth.isAvailable(context) }
    val activity = context as? FragmentActivity

    var stage by remember { mutableStateOf(LockStage.ENTER_PASSWORD) }
    var password by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var errorClearsAt by remember { mutableStateOf(0L) }
    val attemptJson by repo.passwordAttemptState.collectAsState(initial = "")
    val attemptState = remember(attemptJson) {
        com.lucent.app.data.PasswordAttempts.State.fromJson(attemptJson)
    }
    var attemptTick by remember { mutableStateOf(0L) }
    val lockoutMs = remember(attemptState, attemptTick) {
        com.lucent.app.data.PasswordAttempts.remainingLockoutMs(attemptState)
    }
    val lockoutSeconds = ((lockoutMs.coerceAtLeast(0L) + 999L) / 1000L).toInt()

    fun showError(msg: String) {
        error = msg
        errorClearsAt = System.currentTimeMillis() + 3000L
    }

    LaunchedEffect(error) {
        if (error.isNotBlank() && lockoutSeconds == 0) {
            val deadline = errorClearsAt.coerceAtLeast(System.currentTimeMillis() + 3000L)
            while (System.currentTimeMillis() < deadline && lockoutSeconds == 0) {
                kotlinx.coroutines.delay(200)
            }
            if (lockoutSeconds == 0 && error.isNotBlank()) error = ""
        }
    }
    LaunchedEffect(lockoutSeconds) {
        while (lockoutSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            attemptTick++
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
                            enabled = password.isNotEmpty() && credentials.isNotEmpty() && lockoutSeconds == 0,
                            onClick = {
                                scope.launch {
                                    val state = com.lucent.app.data.PasswordAttempts.State.fromJson(
                                        repo.passwordAttemptStateOnce())
                                    val wait = com.lucent.app.data.PasswordAttempts.remainingLockoutMs(state)
                                    if (wait > 0L) {
                                        error = ""
                                        return@launch
                                    }
                                    if (AppLock.verifyPassword(credentials, password)) {
                                        password = ""
                                        error = ""
                                        errorClearsAt = 0L
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
                                            AppLockController.unlock()
                                        } else {
                                            val remain = com.lucent.app.data.PasswordAttempts.remainingLockoutMs(next)
                                            if (remain > 0L) {
                                                error = ""
                                            } else {
                                                showError(com.lucent.app.i18n.S.lockWrongPassword)
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(com.lucent.app.i18n.S.lockUnlock) }
                        if (biometricEnabled && biometricAvailable && activity != null && lockoutSeconds == 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    BiometricAuth.authenticate(
                                        activity = activity,
                                        title = com.lucent.app.i18n.S.biometricPromptTitle,
                                        subtitle = com.lucent.app.i18n.S.biometricPromptSubtitle,
                                        negativeButtonText = com.lucent.app.i18n.S.biometricUsePassword,
                                        onSuccess = {
                                            scope.launch {
                                                val st = com.lucent.app.data.PasswordAttempts.State.fromJson(
                                                    repo.passwordAttemptStateOnce())
                                                if (com.lucent.app.data.PasswordAttempts.remainingLockoutMs(st) > 0L) {
                                                } else {
                                                    password = ""
                                                    error = ""
                                                    errorClearsAt = 0L
                                                    AppLockController.unlock()
                                                }
                                            }
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
