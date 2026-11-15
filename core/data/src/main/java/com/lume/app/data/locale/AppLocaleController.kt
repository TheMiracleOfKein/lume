package com.lume.app.data.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.lume.app.R

/**
 * App language preference. Empty / [SYSTEM] follows the device locale (Android standard).
 * Add a new language: values-xx/strings.xml + entry here + locales_config.xml.
 */
enum class AppLanguage(
    /** BCP-47 tag stored in prefs; [SYSTEM] uses empty LocaleList. */
    val tag: String,
    val labelRes: Int,
) {
    SYSTEM("system", R.string.lang_system),
    ENGLISH("en", R.string.lang_english),
    RUSSIAN("ru", R.string.lang_russian),
    ;

    companion object {
        fun fromStored(value: String?): AppLanguage {
            val v = value?.trim().orEmpty()
            if (v.isEmpty() || v.equals("system", ignoreCase = true)) return SYSTEM
            return entries.firstOrNull { it.tag.equals(v, ignoreCase = true) } ?: SYSTEM
        }
    }
}

object AppLocaleController {
    fun apply(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun applyStoredTag(tag: String?) {
        apply(AppLanguage.fromStored(tag))
    }
}
