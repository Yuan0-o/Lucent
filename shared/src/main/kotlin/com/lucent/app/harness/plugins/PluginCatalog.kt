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

    private const val UBUNTU_TARBALL = "ubuntu-base-24.04.5-base-arm64.tar.gz"
    private const val UBUNTU_SIZE = 29936675L

    private fun termux(
        id: String,
        name: String,
        pkg: String,
        binary: String,
        summary: String,
        licence: String,
        homepage: String
    ) = PluginSpec(
        id = id,
        name = name,
        summary = "$summary (installed in Termux)",
        android = true,
        desktop = false,
        bytes = 0L,
        sources = emptyList(),
        detectCommand = "command -v $binary",
        installScript = "pkg install -y $pkg",
        removeScript = "pkg uninstall -y $pkg",
        licence = licence,
        homepage = homepage
    )

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
            id = "termux",
            name = "Termux bridge",
            summary = "The only supported way to run real commands on Android",
            android = true,
            desktop = false,
            bytes = 0L,
            sources = listOf(
                PluginSource(
                    "fdroid",
                    "F-Droid (official)",
                    "https://f-droid.org/packages/com.termux/",
                    official = true
                ),
                PluginSource("github", "GitHub releases", "https://github.com/termux/termux-app/releases")
            ),
            detectCommand = "",
            installScript = "",
            licence = "GPL-3.0",
            homepage = "https://termux.dev",
            needsShell = false
        ),
        PluginSpec(
            id = "ubuntu",
            name = "Linux userland (Ubuntu 24.04)",
            summary = "A full Ubuntu userland in PRoot, for anything the phone cannot do natively",
            android = true,
            desktop = false,
            bytes = UBUNTU_SIZE,
            sources = listOf(
                PluginSource(
                    "cdimage",
                    "Ubuntu official",
                    "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04.5/release/$UBUNTU_TARBALL",
                    official = true,
                    bytes = UBUNTU_SIZE
                ),
                PluginSource(
                    "tuna",
                    "Tsinghua TUNA",
                    "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04.5/release/$UBUNTU_TARBALL",
                    bytes = UBUNTU_SIZE
                ),
                PluginSource(
                    "aliyun",
                    "Aliyun",
                    "https://mirrors.aliyun.com/ubuntu-cdimage/ubuntu-base/releases/24.04.5/release/$UBUNTU_TARBALL",
                    bytes = UBUNTU_SIZE
                )
            ),
            detectCommand = "test -x ~/lucent/ubuntu/rootfs/bin/bash",
            installScript = "set -e; " +
                "mkdir -p ~/lucent/ubuntu/rootfs; " +
                "pkg install -y proot tar xz-utils; " +
                "tar -xzf {file} -C ~/lucent/ubuntu/rootfs; " +
                "printf 'nameserver 223.5.5.5\\nnameserver 119.29.29.29\\n' > ~/lucent/ubuntu/rootfs/etc/resolv.conf; " +
                "mkdir -p ~/lucent/ubuntu/rootfs/etc/apt/sources.list.d; " +
                "printf 'Types: deb\\nURIs: https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/\\n" +
                "Suites: noble noble-updates noble-backports\\nComponents: main universe restricted multiverse\\n" +
                "Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg\\n' " +
                "> ~/lucent/ubuntu/rootfs/etc/apt/sources.list.d/ubuntu.sources; " +
                "rm -f ~/lucent/ubuntu/rootfs/etc/apt/sources.list",
            removeScript = "rm -rf ~/lucent/ubuntu",
            licence = "Ubuntu base image, mixed free licences",
            homepage = "https://cdimage.ubuntu.com/ubuntu-base/"
        ),
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
            installScript = "pkg install -y nodejs-lts",
            removeScript = "pkg uninstall -y nodejs-lts",
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
        termux("git-termux", "Git for Termux", "git", "git", "Version control inside Termux", "GPL-2.0", "https://git-scm.com"),
        termux("pandoc-termux", "Pandoc for Termux", "pandoc", "pandoc", "Document conversion inside Termux", "GPL-2.0-or-later", "https://pandoc.org"),
        termux("ffmpeg-termux", "FFmpeg for Termux", "ffmpeg", "ffmpeg", "Media conversion inside Termux", "LGPL-2.1/GPL-2.0", "https://ffmpeg.org"),
        termux("ripgrep-termux", "ripgrep for Termux", "ripgrep", "rg", "Fast search inside Termux", "MIT/Unlicense", "https://github.com/BurntSushi/ripgrep"),
        termux("tesseract-termux", "Tesseract for Termux", "tesseract", "tesseract", "OCR inside Termux", "Apache-2.0", "https://github.com/tesseract-ocr/tesseract"),
        termux("poppler-termux", "Poppler for Termux", "poppler", "pdftotext", "PDF text and image tools", "GPL-2.0/3", "https://poppler.freedesktop.org"),
        termux("ytdlp-termux", "yt-dlp for Termux", "yt-dlp", "yt-dlp", "Downloads media from the web", "Unlicense", "https://github.com/yt-dlp/yt-dlp"),
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
}
