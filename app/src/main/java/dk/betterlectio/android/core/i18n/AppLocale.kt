package dk.betterlectio.android.core.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dk.betterlectio.android.feature.settings.AppLanguage

/**
 * Applies the user's language preference via AndroidX per-app locales.
 * Empty list = follow system language.
 */
object AppLocale {
    fun apply(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.DANISH -> LocaleListCompat.forLanguageTags("da")
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
