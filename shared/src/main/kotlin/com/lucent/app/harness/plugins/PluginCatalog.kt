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
    val needsShell: Boolean = true
)

object PluginCatalog {

    private const val UBUNTU_TARBALL = "ubuntu-base-24.04.5-base-arm64.tar.gz"
    private const val UBUNTU_SIZE = 29936675L
    private const val PY_EMBED = "python-3.13.7-embed-amd64.zip"
    private const val PY_SIZE = 10922561L
    private const val LO_MSI = "LibreOffice_26.8.0_Win_x86-64.msi"
    private const val LO_SIZE = 374906880L

    private fun termux(id: String, name: String, pkg: String, summary: String, licence: String, homepage: String) =
        PluginSpec(
            id = id,
            name = name,
            summary = "$summary (installed in Termux)",
            android = true,
            desktop = false,
            bytes = 0L,
            sources = emptyList(),
            detectCommand = "command -v ${pkg.substringAfterLast('-')}",
            installScript = "pkg install -y $pkg",
            removeScript = "pkg uninstall -y $pkg",
            licence = licence,
            homepage = homepage
        )

    private fun desktopTool(
        id: String,
        name: String,
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
        detectCommand = "command -v $id",
        installScript = "winget install --id $winget -e --accept-package-agreements --accept-source-agreements",
        removeScript = "winget uninstall --id $winget -e",
        licence = licence,
        homepage = homepage
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
            detectCommand = "test -d ~/lucent/ubuntu/rootfs",
            installScript = "mkdir -p ~/lucent/ubuntu/rootfs && " +
                "pkg install -y proot tar xz-utils || true; " +
                "tar -xzf {file} -C ~/lucent/ubuntu/rootfs && " +
                "printf 'nameserver 223.5.5.5\\n' > ~/lucent/ubuntu/rootfs/etc/resolv.conf && " +
                "sed -i 's|ports.ubuntu.com|mirrors.tuna.tsinghua.edu.cn|g' ~/lucent/ubuntu/rootfs/etc/apt/sources.list 2>/dev/null || true",
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
            bytes = PY_SIZE,
            sources = listOf(
                PluginSource(
                    "python.org",
                    "python.org (official)",
                    "https://www.python.org/ftp/python/3.13.7/$PY_EMBED",
                    official = true,
                    bytes = PY_SIZE
                ),
                PluginSource(
                    "huawei",
                    "Huawei Cloud",
                    "https://repo.huaweicloud.com/python/3.13.7/$PY_EMBED",
                    bytes = PY_SIZE
                ),
                PluginSource(
                    "tuna-pypi",
                    "TUNA PyPI",
                    "https://pypi.tuna.tsinghua.edu.cn/simple",
                    bytes = 0L
                )
            ),
            detectCommand = "python3 -c \"import docx, openpyxl, pptx\"",
            installScript = "python3 -m pip install --index-url https://pypi.tuna.tsinghua.edu.cn/simple " +
                "python-docx openpyxl XlsxWriter python-pptx pymupdf pandas",
            removeScript = "python3 -m pip uninstall -y python-docx openpyxl XlsxWriter python-pptx pymupdf pandas",
            licence = "Python PSF-2.0; libraries MIT/BSD; PyMuPDF AGPL-3.0",
            homepage = "https://www.python.org"
        ),
        PluginSpec(
            id = "libreoffice",
            name = "LibreOffice",
            summary = "Converts and renders Word, Excel, PowerPoint and PDF so the assistant can check its own work",
            android = true,
            desktop = true,
            bytes = LO_SIZE,
            sources = listOf(
                PluginSource(
                    "tdf",
                    "The Document Foundation (official)",
                    "https://download.documentfoundation.org/libreoffice/stable/26.8.0/win/x86_64/$LO_MSI",
                    official = true,
                    bytes = LO_SIZE
                ),
                PluginSource(
                    "tuna",
                    "Tsinghua TUNA",
                    "https://mirrors.tuna.tsinghua.edu.cn/tdf-pub/libreoffice/stable/26.8.0/win/x86_64/$LO_MSI",
                    bytes = LO_SIZE
                )
            ),
            detectCommand = "command -v soffice",
            installScript = "apt-get update && apt-get install -y --no-install-recommends " +
                "libreoffice-writer-nogui libreoffice-calc-nogui libreoffice-impress-nogui",
            removeScript = "apt-get remove -y 'libreoffice*'",
            licence = "MPL-2.0",
            homepage = "https://www.libreoffice.org"
        ),
        PluginSpec(
            id = "nodejs",
            name = "Node.js",
            summary = "Runs JavaScript tooling and the Playwright browser engine",
            android = true,
            desktop = true,
            bytes = 0L,
            sources = listOf(
                PluginSource("nodejs", "nodejs.org (official)", "https://nodejs.org/dist/", official = true),
                PluginSource("tuna", "Tsinghua TUNA", "https://mirrors.tuna.tsinghua.edu.cn/nodejs-release/"),
                PluginSource("npmmirror", "npmmirror", "https://registry.npmmirror.com")
            ),
            detectCommand = "command -v node",
            installScript = "pkg install -y nodejs-lts || winget install --id OpenJS.NodeJS.LTS -e " +
                "--accept-package-agreements --accept-source-agreements",
            removeScript = "pkg uninstall -y nodejs-lts",
            licence = "MIT",
            homepage = "https://nodejs.org"
        ),
        desktopTool("git", "Git", "Git.Git", "Version control for the workspace", "GPL-2.0", "https://git-scm.com"),
        desktopTool("pandoc", "Pandoc", "JohnMacFarlane.Pandoc", "Converts between document formats", "GPL-2.0-or-later", "https://pandoc.org"),
        desktopTool("ffmpeg", "FFmpeg", "Gyan.FFmpeg", "Audio and video conversion", "LGPL-2.1/GPL-2.0", "https://ffmpeg.org"),
        desktopTool("rg", "ripgrep", "BurntSushi.ripgrep.MSVC", "Very fast text search", "MIT/Unlicense", "https://github.com/BurntSushi/ripgrep"),
        desktopTool("7z", "7-Zip", "7zip.7zip", "Archive handling", "LGPL-2.1 with unRAR restriction", "https://7-zip.org"),
        desktopTool("tesseract", "Tesseract OCR", "UB-Mannheim.TesseractOCR", "Reads text out of images", "Apache-2.0", "https://github.com/tesseract-ocr/tesseract"),
        desktopTool("magick", "ImageMagick", "ImageMagick.ImageMagick", "Image conversion and editing", "ImageMagick licence", "https://imagemagick.org"),
        desktopTool("qpdf", "qpdf", "QPDF.QPDF", "PDF splitting and merging", "Apache-2.0", "https://qpdf.readthedocs.io"),
        termux("git-termux", "Git for Termux", "git", "Version control inside Termux", "GPL-2.0", "https://git-scm.com"),
        termux("pandoc-termux", "Pandoc for Termux", "pandoc", "Document conversion inside Termux", "GPL-2.0-or-later", "https://pandoc.org"),
        termux("ffmpeg-termux", "FFmpeg for Termux", "ffmpeg", "Media conversion inside Termux", "LGPL-2.1/GPL-2.0", "https://ffmpeg.org"),
        termux("ripgrep-termux", "ripgrep for Termux", "ripgrep", "Fast search inside Termux", "MIT/Unlicense", "https://github.com/BurntSushi/ripgrep"),
        termux("tesseract-termux", "Tesseract for Termux", "tesseract", "OCR inside Termux", "Apache-2.0", "https://github.com/tesseract-ocr/tesseract"),
        termux("poppler-termux", "Poppler for Termux", "poppler", "PDF text and image tools", "GPL-2.0/3", "https://poppler.freedesktop.org"),
        termux("ytdlp-termux", "yt-dlp for Termux", "yt-dlp", "Downloads media from the web", "Unlicense", "https://github.com/yt-dlp/yt-dlp"),
        PluginSpec(
            id = "playwright",
            name = "Playwright browser",
            summary = "A real Chromium the assistant can drive when a page needs JavaScript",
            android = false,
            desktop = true,
            bytes = 150000000L,
            sources = listOf(
                PluginSource("npm", "npm (official)", "https://registry.npmjs.org/playwright", official = true),
                PluginSource("npmmirror", "npmmirror", "https://registry.npmmirror.com/playwright")
            ),
            detectCommand = "npx --no-install playwright --version",
            installScript = "npm config set registry https://registry.npmmirror.com && " +
                "set PLAYWRIGHT_DOWNLOAD_HOST=https://cdn.npmmirror.com/binaries/playwright&& npm install -g playwright && " +
                "npx playwright install chromium",
            removeScript = "npm uninstall -g playwright",
            licence = "Apache-2.0",
            homepage = "https://playwright.dev"
        )
    )

    fun all(): List<PluginSpec> = ALL

    fun find(id: String): PluginSpec? = ALL.firstOrNull { it.id == id }

    fun forPlatform(android: Boolean): List<PluginSpec> = ALL.filter { if (android) it.android else it.desktop }
}
