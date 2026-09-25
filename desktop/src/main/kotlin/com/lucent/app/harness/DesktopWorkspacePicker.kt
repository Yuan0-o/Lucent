package com.lucent.app.harness

import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

object DesktopWorkspacePicker {

    fun choose(title: String): String? {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser()
        chooser.dialogTitle = title
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isMultiSelectionEnabled = false
        val home = System.getProperty("user.home")
        if (!home.isNullOrBlank()) chooser.currentDirectory = File(home)
        val result = chooser.showOpenDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile?.absolutePath
    }
}
