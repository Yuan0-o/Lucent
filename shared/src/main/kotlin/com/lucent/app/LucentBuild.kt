package com.lucent.app

object LucentBuild {

    const val VERSION = "3.0.0"

    const val BUILD_NUMBER = "301"

    const val PRODUCT_NAME = "Lucent"

    const val COPYRIGHT = "Copyright \u00A9 2026-2027 Jessica Martinez"

    const val DEVELOPER = "Jessica Martinez"

    const val SUPPORT_EMAIL = "yuan47578@gmail.com"

    const val HOMEPAGE = "https://github.com/Yuan0-o/Lucent"

    const val LICENSES_PAGE = "https://github.com/Yuan0-o/Lucent/blob/main/docs/THIRD-PARTY-NOTICES.md"

    const val RELEASES_API = "https://api.github.com/repos/Yuan0-o/Lucent/releases/latest"

    private const val PRIVACY_PAGE = "https://github.com/Yuan0-o/Lucent/blob/main/PRIVACY/PRIVACY"

    fun privacyPage(languageKey: String): String = when (languageKey) {
        "zh" -> "$PRIVACY_PAGE.zh-CN.md"
        "ja" -> "$PRIVACY_PAGE.ja.md"
        "ko" -> "$PRIVACY_PAGE.ko.md"
        else -> "$PRIVACY_PAGE.md"
    }
}
