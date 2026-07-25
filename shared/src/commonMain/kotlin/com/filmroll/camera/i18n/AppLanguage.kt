package com.filmroll.camera.i18n

/**
 * Languages the app ships translations for. [tag] is a BCP-47 tag that matches the
 * `values-<qualifier>` folders under composeResources, and [endonym] is the language's own
 * name so the picker stays readable no matter which locale is currently active.
 */
enum class AppLanguage(
    val tag: String,
    val endonym: String,
    val englishName: String,
    val flag: String,
) {
    ENGLISH("en", "English", "English", "🇺🇸"),
    VIETNAMESE("vi", "Tiếng Việt", "Vietnamese", "🇻🇳"),
    SPANISH("es", "Español", "Spanish", "🇪🇸"),
    FRENCH("fr", "Français", "French", "🇫🇷"),
    GERMAN("de", "Deutsch", "German", "🇩🇪"),
    PORTUGUESE_BR("pt-BR", "Português (Brasil)", "Portuguese (Brazil)", "🇧🇷"),
    JAPANESE("ja", "日本語", "Japanese", "🇯🇵"),
    KOREAN("ko", "한국어", "Korean", "🇰🇷"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文", "Chinese (Simplified)", "🇨🇳"),
    INDONESIAN("id", "Bahasa Indonesia", "Indonesian", "🇮🇩"),
    HINDI("hi", "हिन्दी", "Hindi", "🇮🇳");

    companion object {
        val DEFAULT = ENGLISH

        /** Shown above the divider in the picker, before the alphabetical remainder. */
        val SUGGESTED = listOf(ENGLISH, VIETNAMESE, SPANISH, PORTUGUESE_BR, HINDI)

        fun fromTag(tag: String?): AppLanguage? =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }

        /**
         * Resolves a device locale tag such as `pt-BR`, `zh-Hans-CN` or `in_ID` to a shipped
         * language, falling back to a same-language match before giving up.
         */
        fun fromDeviceTag(deviceTag: String): AppLanguage? {
            val normalised = deviceTag.replace('_', '-')
            fromTag(normalised)?.let { return it }
            val primary = normalised.substringBefore('-').lowercase()
            // Java still reports Indonesian with the legacy `in` code.
            val alias = if (primary == "in") "id" else primary
            return entries.firstOrNull { it.tag.substringBefore('-').lowercase() == alias }
        }
    }
}
