package com.lucent.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Task
import com.lucent.app.tools.TaskActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WidgetTaskConfirm {

    var pendingId by mutableStateOf<Long?>(null)
        private set

    fun offer(id: Long) { pendingId = id }
    fun clear() { pendingId = null }
}

@Composable
fun WidgetTaskConfirmDialog() {
    val id = WidgetTaskConfirm.pendingId ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var task by remember(id) { mutableStateOf<Task?>(null) }
    LaunchedEffect(id) {
        val appContext = context.applicationContext
        val loaded = withContext(Dispatchers.IO) {
            AppDatabase.getInstance(appContext).taskDao().getByIdOnce(id)
        }
        if (loaded == null || loaded.trashedAt != null) {
            WidgetTaskConfirm.clear()
            LucentToast.show(appContext, com.lucent.app.i18n.S.widgetTaskGone)
            com.lucent.app.widget.WidgetUpdater.refreshContent(appContext)
        } else {
            task = loaded
        }
    }
    val t = task ?: return

    val displayTitle = t.title.ifBlank { com.lucent.app.i18n.S.untitledTask }
    AlertDialog(
        onDismissRequest = { WidgetTaskConfirm.clear() },
        title = {
            Text(
                if (t.isDone) com.lucent.app.i18n.S.markNotDoneTitle
                else com.lucent.app.i18n.S.confirmMarkDone
            )
        },
        text = {
            Text(
                if (t.isDone) com.lucent.app.i18n.S.markNotDoneBody(displayTitle)
                else com.lucent.app.i18n.S.completeTaskBody(displayTitle)
            )
        },
        confirmButton = {
            Button(onClick = {
                WidgetTaskConfirm.clear()
                scope.launch {
                    val appContext = context.applicationContext
                    val done = withContext(Dispatchers.IO) {
                        val db = AppDatabase.getInstance(appContext)
                        val fresh = db.taskDao().getByIdOnce(id)
                        if (fresh == null || fresh.trashedAt != null) return@withContext null
                        if (fresh.isDone) {
                            TaskActions.restore(appContext, db, fresh)
                            false
                        } else {
                            TaskActions.complete(appContext, db, fresh)
                            true
                        }
                    }
                    when (done) {
                        true -> LucentToast.show(appContext, com.lucent.app.i18n.S.widgetTaskDoneToast(displayTitle))
                        false -> LucentToast.show(appContext, com.lucent.app.i18n.S.widgetTaskReopenedToast(displayTitle))
                        null -> LucentToast.show(appContext, com.lucent.app.i18n.S.widgetTaskGone)
                    }
                    com.lucent.app.widget.WidgetUpdater.refreshContent(appContext)
                }
            }) { Text(com.lucent.app.i18n.S.actionConfirm) }
        },
        dismissButton = {
            TextButton(onClick = { WidgetTaskConfirm.clear() }) {
                Text(com.lucent.app.i18n.S.actionCancel)
            }
        }
    )
}
