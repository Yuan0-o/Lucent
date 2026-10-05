package com.lucent.app.harness.plugins

object PluginCatalog {

    private const val APT_SETUP = "F=/etc/apt/sources.list.d/ubuntu.sources; " +
        "echo 'force-unsafe-io' > /etc/dpkg/dpkg.cfg.d/unsafe-io 2>/dev/null; " +
        "chmod -R u+rwX /var/lib/dpkg 2>/dev/null || true; " +
        "rm -f /var/lib/dpkg/status-old 2>/dev/null; " +
        "echo \"[FIX-M] marker\"; " +
        "lucent_apt() { _t=0; _l=/tmp/lucent-apt-\$\$.log; " +
        "while [ \$_t -lt 40 ]; do \"\$@\" 2>&1 | tee \"\$_l\"; _r=\${PIPESTATUS[0]}; " +
        "if [ \$_r -eq 0 ]; then rm -f \"\$_l\"; return 0; fi; " +
        "if grep -qE \"Unable to acquire the dpkg frontend lock|Could not get lock|dpkg frontend lock was locked\" \"\$_l\"; then " +
        "_t=\$((_t+1)); echo \"[lucent_apt] lock busy, waiting \$_t/40\"; " +
        "if [ \$_t -eq 1 ]; then for _d in /proc/[0-9]*/cmdline; do _c0=\$(tr '\\0' '\\n' <\"\$_d\" 2>/dev/null | head -1); _c0=\${_c0##*/}; " +
        "case \"\$_c0\" in apt-get|apt|dpkg) _p=\${_d#/proc/}; _c=\$(tr '\\0' ' ' <\"\$_d\" 2>/dev/null); " +
        "echo \"[lucent_apt] holder \${_p%/cmdline}: \$_c\";; esac; done; fi; sleep 15; " +
        "else rm -f \"\$_l\"; return \$_r; fi; done; rm -f \"\$_l\"; return \$_r; }; " +
        "lucent_apt dpkg --configure -a 2>&1 | tee /tmp/lucent-dca.log; echo \${PIPESTATUS[0]} > /tmp/lucent-dcarc; " +
        "trap 'echo \"[FIX-M] configure exit:\"; cat /tmp/lucent-dcarc 2>/dev/null; echo \"[FIX-M] dca tail:\"; tail -8 /tmp/lucent-dca.log 2>/dev/null; echo \"[FIX-M] pending:\"; ls /var/lib/dpkg/updates/ 2>/dev/null | head -5' EXIT; " +
        "echo \"[Diagnostics] id=\$(id -u):\$(id -g), free=\$(df -k /var/lib/dpkg | awk 'NR==2{print \$4}')KB\"; " +
        "echo \"[Diagnostics] dpkg dir:\"; ls -ld /var/lib/dpkg 2>/dev/null; ls -la /var/lib/dpkg 2>/dev/null | head -n 5; " +
        "touch /var/lib/dpkg/probe 2>/dev/null && echo \"[Diagnostics] probe=ok\" || echo \"[Diagnostics] probe=fail\"; " +
        "rm -f /var/lib/dpkg/probe 2>/dev/null; " +
        "if [ ! -s \"\$F\" ]; then mkdir -p /etc/apt/sources.list.d && " +
        "printf '%s\\n' 'Types: deb' 'URIs: {aptMirror}/ubuntu-ports/' 'Suites: noble noble-updates noble-backports' " +
        "'Components: main universe restricted multiverse' 'Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg' '' " +
        "'Types: deb' 'URIs: {aptMirror}/ubuntu-ports/' 'Suites: noble-security' " +
        "'Components: main universe restricted multiverse' 'Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg' > \"\$F\"; fi && " +
        "sed -i 's|http://ports.ubuntu.com|{aptMirror}|g; " +
        "s|http://mirrors.tuna.tsinghua.edu.cn|{aptMirror}|g; " +
        "s|https://ports.ubuntu.com|{aptMirror}|g' \"\$F\" && " +
        "(lucent_apt apt-get update -o Acquire::Retries=3 && ls /var/lib/apt/lists/*_Packages > /dev/null 2>&1 || " +
        "(sed -i 's|{aptMirror}|{aptFallback}|g' \"\$F\" && " +
        "lucent_apt apt-get update -o Acquire::Retries=3 && ls /var/lib/apt/lists/*_Packages > /dev/null 2>&1)) && "

    fun aptMirror(region: String): String =
        if (region == "cn") "http://mirrors.tuna.tsinghua.edu.cn" else "http://ports.ubuntu.com"

    fun aptFallback(region: String): String =
        if (region == "cn") "http://ports.ubuntu.com" else "http://mirrors.tuna.tsinghua.edu.cn"

    fun pipIndex(region: String): String =
        if (region == "cn") "https://pypi.tuna.tsinghua.edu.cn/simple" else "https://pypi.org/simple"

    fun pipFallbackIndex(region: String): String =
        if (region == "cn") "https://pypi.org/simple" else "https://pypi.tuna.tsinghua.edu.cn/simple"

    fun effectiveRegion(region: String): String {
        if (region != "auto") return region
        val country = try {
            java.util.Locale.getDefault().country
        } catch (_: Throwable) {
            ""
        }
        if (country.equals("CN", ignoreCase = true)) return "cn"
        val zone = try {
            java.util.TimeZone.getDefault().id
        } catch (_: Throwable) {
            ""
        }
        return if (zone == "Asia/Shanghai" || zone == "Asia/Chongqing" || zone == "Asia/Chungking" ||
            zone == "Asia/Harbin" || zone == "Asia/Urumqi" || zone == "Asia/Kashgar"
        ) {
            "cn"
        } else {
            "global"
        }
    }

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
            installScript = APT_SETUP +
                "lucent_apt apt-get install -y --no-install-recommends python3 python3-pip python3-venv ca-certificates && " +
                "(pip3 install --break-system-packages --index-url {pipIndex} " +
                "python-docx openpyxl XlsxWriter python-pptx pymupdf pandas || " +
                "pip3 install --break-system-packages --index-url {pipFallback} " +
                "python-docx openpyxl XlsxWriter python-pptx pymupdf pandas)",
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
            installScript = APT_SETUP +
                "lucent_apt apt-get install -y --no-install-recommends " +
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
            installScript = APT_SETUP + "lucent_apt apt-get install -y nodejs npm",
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
