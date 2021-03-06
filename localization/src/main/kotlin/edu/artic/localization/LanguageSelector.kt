package edu.artic.localization

import io.reactivex.subjects.Subject
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * This class hosts zero or more [RX subjects][Subject] related to language.
 *
 * Use this instead of going directly to [the default Locale][java.util.Locale.getDefault].
 *
 * @see SpecifiesLanguage
 */
class LanguageSelector(private val prefs: LocalizationPreferences) {

    private val appLocaleRef: AtomicReference<Locale> = AtomicReference(prefs.preferredAppLocale)

    fun setDefaultLanguageForApplication(lang: Locale) {
        if (lang.hasNoLanguage()) {
            if (BuildConfig.DEBUG) {
                throw IllegalArgumentException("Please ensure your chosen locale (\"${lang.language}\") actually includes a language.")
            }
        } else {
            prefs.preferredAppLocale = lang
            appLocaleRef.set(lang)
        }
    }

    /**
     * Returns the first translation we can find in `languages` that belongs to the
     * same locale as [appLocaleRef]. If no such exists, we return the first language
     * in the list.
     *
     * As per the requirements laid out in [Locale.getLanguage], we only convert
     * languages that have been normalized through [Locale.forLanguageTag]. We
     * cannot necessarily trust the original Strings returned by the API.
     */
    fun <T : SpecifiesLanguage> selectFrom(languages: List<T>): T {
        val appLocale: Locale = appLocaleRef.get()

        // TODO: Investigate possibility of replacing the list with a `LocaleList`
        return languages.firstOrNull {
            // This is very much intentional. Read the method docs fully before changing.
            it.underlyingLocale().language == appLocale.language
        } ?: languages[0]
    }

}