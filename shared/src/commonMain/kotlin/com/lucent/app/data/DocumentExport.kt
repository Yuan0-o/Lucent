package com.lucent.app.data

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    MARKDOWN("Markdown (.md)", "md", "text/markdown"),
    WORD("Word (.docx)", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("PDF (.pdf)", "pdf", "application/pdf"),
    EXCEL("Excel (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
}
