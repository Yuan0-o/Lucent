package com.lucent.app.harness.plugins

data class PluginSource(
    val id: String,
    val label: String,
    val url: String,
    val official: Boolean = false,
    val sha256: String = "",
    val bytes: Long = 0L
)

data class PluginSpec(
    val id: String,
    val name: String,
    val summary: String,
    val android: Boolean,
    val desktop: Boolean,
    val bytes: Long,
    val sources: List<PluginSource>,
    val detectCommand: String,
    val installScript: String,
    val removeScript: String = "",
    val licence: String,
    val homepage: String,
    val needsShell: Boolean = true,
    val windowsDetect: String = "",
    val windowsInstall: String = "",
    val windowsRemove: String = ""
) {
    fun probeFor(android: Boolean): String = if (!android && windowsDetect.isNotBlank()) windowsDetect else detectCommand

    fun installFor(android: Boolean): String = if (!android && windowsInstall.isNotBlank()) windowsInstall else installScript

    fun removeFor(android: Boolean): String = if (!android && windowsRemove.isNotBlank()) windowsRemove else removeScript
}

object PluginCatalog {

    private fun desktopTool(
        id: String,
        name: String,
        binary: String,
        winget: String,
        summary: String,
        licence: String,
        homepage: String
    ) = PluginSpec(
        id = id,
        name = name,
        summary = "$summary (installed on this PC)",
        android = false,
        desktop = true,
        bytes = 0L,
        sources = emptyList(),
        detectCommand = "command -v $binary",
        installScript = "winget install --id $winget -e --accept-package-agreements --accept-source-agreements",
        removeScript = "winget uninstall --id $winget -e",
        licence = licence,
        homepage = homepage,
        windowsDetect = "where $binary",
        windowsInstall = "winget install --id $winget -e --accept-package-agreements --accept-source-agreements",
        windowsRemove = "winget uninstall --id $winget -e"
    )

    private val ALL: List<PluginSpec> = listOf(
        PluginSpec(
            id = "python-office",
            name = "Python document libraries",
            summary = "Python with python-docx, openpyxl, XlsxWriter, python-pptx, pandas and PyMuPDF",
            android = true,
            desktop = true,
            bytes = 0L,
            sources = emptyList(),
            detectCommand = "python3 -c \"import docx, openpyxl, pptx\"",
            installScript = "apt-get update && apt-get install -y --no-install-recommends python3 python3-pip python3-venv && " +
                "pip3 install --break-system-packages --index-url https://pypi.tuna.tsinghua.edu.cn/simple " +
                "python-docx openpyxl XlsxWriter python-pptx pymupdf pandas",
            removeScript = "pip3 uninstall -y python-docx openpyxl XlsxWriter python-pptx pymupdf pandas",
            licence = "Python PSF-2.0; libraries MIT/BSD; PyMuPDF AGPL-3.0",
            homepage = "https://www.python.org",
            windowsDetect = "py -c \"import docx, openpyxl, pptx\"",
            windowsInstall = "winget install --id Python.Python.3.12 -e --accept-package-agreements " +
                "--accept-source-agreements && py -m pip install --no-warn-script-location " +
                "python-docx openpyxl XlsxWriter python-pptx pymupdf pandas",
            windowsRemove = "py -m pip uninstall -y python-docx openpyxl XlsxWriter python-pptx pymupdf pandas"
        ),
        PluginSpec(
            id = "libreoffice",
            name = "LibreOffice",
            summary = "Converts and renders Word, Excel, PowerPoint and PDF so the assistant can check its own work",
            android = true,
            desktop = true,
            bytes = 0L,
            sources = emptyList(),
            detectCommand = "command -v soffice",
            installScript = "apt-get update && apt-get install -y --no-install-recommends " +
                "libreoffice-writer-nogui libreoffice-calc-nogui libreoffice-impress-nogui",
            removeScript = "apt-get remove -y 'libreoffice*'",
            licence = "MPL-2.0",
            homepage = "https://www.libreoffice.org",
            windowsDetect = "if exist \"%ProgramFiles%\\LibreOffice\\program\\soffice.exe\" (exit 0) else (exit 1)",
            windowsInstall = "winget install --id TheDocumentFoundation.LibreOffice -e " +
                "--accept-package-agreements --accept-source-agreements",
            windowsRemove = "winget uninstall --id TheDocumentFoundation.LibreOffice -e"
        ),
        PluginSpec(
            id = "nodejs",
            name = "Node.js",
            summary = "Runs JavaScript tooling and the Playwright browser engine",
            android = true,
            desktop = true,
            bytes = 0L,
            sources = emptyList(),
            detectCommand = "command -v node",
            installScript = "apt-get update && apt-get install -y nodejs npm",
            removeScript = "apt-get remove -y nodejs npm",
            licence = "MIT",
            homepage = "https://nodejs.org",
            windowsDetect = "where node",
            windowsInstall = "winget install --id OpenJS.NodeJS.LTS -e " +
                "--accept-package-agreements --accept-source-agreements",
            windowsRemove = "winget uninstall --id OpenJS.NodeJS.LTS -e"
        ),
        desktopTool("git", "Git", "git", "Git.Git", "Version control for the workspace", "GPL-2.0", "https://git-scm.com"),
        desktopTool("pandoc", "Pandoc", "pandoc", "JohnMacFarlane.Pandoc", "Converts between document formats", "GPL-2.0-or-later", "https://pandoc.org"),
        desktopTool("ffmpeg", "FFmpeg", "ffmpeg", "Gyan.FFmpeg", "Audio and video conversion", "LGPL-2.1/GPL-2.0", "https://ffmpeg.org"),
        desktopTool("rg", "ripgrep", "rg", "BurntSushi.ripgrep.MSVC", "Very fast text search", "MIT/Unlicense", "https://github.com/BurntSushi/ripgrep"),
        desktopTool("7z", "7-Zip", "7z", "7zip.7zip", "Archive handling", "LGPL-2.1 with unRAR restriction", "https://7-zip.org"),
        desktopTool("tesseract", "Tesseract OCR", "tesseract", "UB-Mannheim.TesseractOCR", "Reads text out of images", "Apache-2.0", "https://github.com/tesseract-ocr/tesseract"),
        desktopTool("magick", "ImageMagick", "magick", "ImageMagick.ImageMagick", "Image conversion and editing", "ImageMagick licence", "https://imagemagick.org"),
        desktopTool("qpdf", "qpdf", "qpdf", "QPDF.QPDF", "PDF splitting and merging", "Apache-2.0", "https://qpdf.readthedocs.io"),
        PluginSpec(
            id = "playwright",
            name = "Playwright browser",
            summary = "A real Chromium the assistant can drive when a page needs JavaScript",
            android = false,
            desktop = true,
            bytes = 0L,
            sources = emptyList(),
            detectCommand = "npx --no-install playwright --version",
            installScript = "npm config set registry https://registry.npmmirror.com && " +
                "set \"PLAYWRIGHT_DOWNLOAD_HOST=https://cdn.npmmirror.com/binaries/playwright\" && " +
                "npm install -g playwright && npx playwright install chromium",
            removeScript = "npm uninstall -g playwright",
            licence = "Apache-2.0",
            homepage = "https://playwright.dev",
            windowsDetect = "npx --no-install playwright --version",
            windowsInstall = "npm config set registry https://registry.npmmirror.com && " +
                "set \"PLAYWRIGHT_DOWNLOAD_HOST=https://cdn.npmmirror.com/binaries/playwright\" && " +
                "npm install -g playwright && npx playwright install chromium",
            windowsRemove = "npm uninstall -g playwright"
        )
    )

    fun all(): List<PluginSpec> = ALL

    fun find(id: String): PluginSpec? = ALL.firstOrNull { it.id == id }

    fun forPlatform(android: Boolean): List<PluginSpec> = ALL.filter { if (android) it.android else it.desktop }

    suspend fun effective(): List<PluginSpec> = PluginCatalogRemote.fetchEffective(com.lucent.app.harness.HarnessRuntime.config())

    suspend fun findEffective(id: String): PluginSpec? = effective().firstOrNull { it.id == id }

    suspend fun forPlatformEffective(android: Boolean): List<PluginSpec> = effective().filter { if (android) it.android else it.desktop }
}
