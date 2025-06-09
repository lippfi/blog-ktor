package fi.lipp.blog.util

import fi.lipp.blog.data.Language
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class for localizing messages based on the current user's language preference.
 */
object MessageLocalizer {
    // Default language to use when user is not logged in or language can't be determined
    private val defaultLanguage = Language.EN

    // Cache for loaded properties files
    private val propertiesCache = ConcurrentHashMap<Language, Properties>()

    // Initialize properties for all supported languages
    init {
        // Load properties for all languages
        Language.entries.forEach { language ->
            val filename = "exceptions_${language.name.lowercase()}.properties"
            try {
                propertiesCache[language] = loadProperties(filename)
            } catch (e: Exception) {
                // If there's an error loading the properties file, log it but don't crash
                println("Warning: Could not load properties file $filename: ${e.message}")
            }
        }
    }

    /**
     * Get the localized message for the given message key and language.
     * If language is null, the default language (English) is used.
     * Optional parameters can be provided to replace placeholders in the message.
     *
     * @param messageKey The key for the message to localize
     * @param language The language to use for localization, or null to use default
     * @param params Optional vararg of Pairs where the first value is the placeholder name and the second is the replacement value
     * @return The localized message with placeholders replaced
     */
    fun getLocalizedMessage(messageKey: String, language: Language? = null, vararg params: Pair<String, String>): String {
        val actualLanguage = language ?: defaultLanguage
        var message = getLocalizedMessageInternal(messageKey, actualLanguage)

        // Replace parameters in the message
        params.forEach { (key, value) ->
            message = message.replace("{{$key}}", value)
        }

        return message
    }

    /**
     * Get the localized message for the given message key and language.
     *
     * @param messageKey The key for the message to localize
     * @param language The language to use for localization
     * @return The localized message
     */
    private fun getLocalizedMessageInternal(messageKey: String, language: Language): String {
        // Get properties for the requested language
        val properties = propertiesCache[language]

        // If properties exist for this language, use them
        if (properties != null) {
            val message = properties.getProperty(messageKey)
            if (message != null) {
                return message
            }
        }

        // If not found in the requested language, fall back to English
        if (language != defaultLanguage) {
            val defaultProperties = propertiesCache[defaultLanguage]
            if (defaultProperties != null) {
                val defaultMessage = defaultProperties.getProperty(messageKey)
                if (defaultMessage != null) {
                    return defaultMessage
                }
            }
        }

        // If all else fails, return the key itself
        return messageKey
    }

    /**
     * Load properties from a resource file.
     *
     * @param filename The name of the properties file
     * @return The loaded Properties object
     */
    private fun loadProperties(filename: String): Properties {
        val properties = Properties()
        val inputStream = MessageLocalizer::class.java.classLoader.getResourceAsStream(filename)
        if (inputStream != null) {
            properties.load(inputStream)
            inputStream.close()
        }
        return properties
    }
}
