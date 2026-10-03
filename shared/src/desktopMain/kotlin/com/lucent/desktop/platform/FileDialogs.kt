
package com.lucent.desktop.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

data class FileFilter(val description: String, val extensions: List<String>) {
    companion object {
        val ANY = FileFilter("All files", emptyList())
        val IMAGES = FileFilter("Images", listOf("png", "jpg", "jpeg", "gif", "webp", "bmp"))
        val TEXT = FileFilter("Text", listOf("txt", "md", "json", "csv"))
    }
}

object DesktopFiles {

    fun openFile(
        title: String = "Open",
        filter: FileFilter = FileFilter.ANY,
        startIn: File? = null
    ): File? = openInternal(title, filter, multiple = false, startIn = startIn).firstOrNull()

    fun openFiles(
        title: String = "Open",
        filter: FileFilter = FileFilter.ANY,
        startIn: File? = null
    ): List<File> = openInternal(title, filter, multiple = true, startIn = startIn)

    fun saveFile(title: String = "Save", suggestedName: String = ""): File? {
        val owner: Frame? = null
        val dialog = FileDialog(owner, title, FileDialog.SAVE).apply {
            if (suggestedName.isNotBlank()) file = suggestedName
        }
        return try {
            dialog.isVisible = true
            val dir = dialog.directory
            val name = dialog.file
            if (dir != null && name != null) File(dir, name) else null
        } catch (t: Throwable) {
            null
        } finally {
            dialog.dispose()
        }
    }

    private fun openInternal(
        title: String,
        filter: FileFilter,
        multiple: Boolean,
        startIn: File? = null
    ): List<File> {
        val owner: Frame? = null
        val dialog = FileDialog(owner, title, FileDialog.LOAD).apply {
            isMultipleMode = multiple
            extFilter(filter)?.let { filenameFilter = it }
            if (startIn != null && startIn.isDirectory) directory = startIn.absolutePath
        }
        return try {
            dialog.isVisible = true
            if (multiple) {
                dialog.files?.toList() ?: emptyList()
            } else {
                val dir = dialog.directory
                val name = dialog.file
                if (dir != null && name != null) listOf(File(dir, name)) else emptyList()
            }
        } catch (t: Throwable) {
            emptyList()
        } finally {
            dialog.dispose()
        }
    }

    fun chooseFolder(title: String = "Choose folder"): File? = try {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = false
        }
        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.takeIf { it.isDirectory }
        } else null
    } catch (t: Throwable) {
        null
    }

    private fun extFilter(filter: FileFilter): FilenameFilter? {
        if (filter.extensions.isEmpty()) return null
        val wanted = filter.extensions.map { it.lowercase().removePrefix(".") }.toSet()
        return FilenameFilter { _, name ->
            val dot = name.lastIndexOf('.')
            dot >= 0 && name.substring(dot + 1).lowercase() in wanted
        }
    }
}
