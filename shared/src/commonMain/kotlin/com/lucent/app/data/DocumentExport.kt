package com.lucent.app.data

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    MARKDOWN("Markdown (.md)", "md", "text/markdown"),
    WORD("Word (.docx)", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("PDF (.pdf)", "pdf", "application/pdf"),
    EXCEL("Excel (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
}

expect object DocumentExport {
    fun exportNotes(notes: List<Note>, format: ExportFormat): ByteArray
    fun exportTasks(tasks: List<Task>, format: ExportFormat): ByteArray
    fun doodlesPdf(canvases: List<DoodleExport.Canvas>, heading: String = ""): ByteArray
    fun zipWithAttachments(
        context: com.lucent.app.platform.PlatformContext,
        documentName: String,
        documentBytes: ByteArray,
        attachments: List<Attachment>,
        extraFiles: List<Pair<String, ByteArray>> = emptyList()
    ): ByteArray
}
