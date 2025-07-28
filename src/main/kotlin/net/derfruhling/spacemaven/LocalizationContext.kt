package net.derfruhling.spacemaven

import gg.jte.support.LocalizationSupport
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle

val locales = mutableMapOf<String, LocalizationContext>()
val supportedLanguages = listOf("en", "en_us")

fun getLocalizationContext(availableLanguages: Iterable<String>): LocalizationContext {
    val language = availableLanguages.first { it in supportedLanguages }

    return locales.computeIfAbsent(language) {
        LocalizationContext(language)
    }
}

class LocalizationContext(val language: String) : LocalizationSupport {
    private val locale = ResourceBundle.getBundle("locale", Locale.of(language))!!
    private val log = KotlinLogging.logger {}

    override fun lookup(key: String): String {
        try {
            return locale.getString(key)
        } catch (e: MissingResourceException) {
            log.error(e) { "Missing resource for language $language with $key" }
            return key
        }
    }
}
