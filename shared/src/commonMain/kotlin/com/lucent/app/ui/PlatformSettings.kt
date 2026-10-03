package com.lucent.app.ui

import androidx.compose.runtime.Composable
import com.lucent.app.data.SettingsRepository

expect val crashShieldLocksStartupLogging: Boolean

@Composable
expect fun DynamicColorRow(repo: SettingsRepository)

@Composable
expect fun rememberDynamicColorActive(repo: SettingsRepository): Boolean

@Composable
expect fun DesktopIntegrationRows(repo: SettingsRepository)

@Composable
expect fun SecondaryUnlockRow(repo: SettingsRepository, appLockOn: Boolean)

@Composable
expect fun rememberBackupFolderPicker(onFolderPicked: (String) -> Unit): () -> Unit
