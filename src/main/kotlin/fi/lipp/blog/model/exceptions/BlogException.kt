package fi.lipp.blog.model.exceptions

import fi.lipp.blog.data.Language
import fi.lipp.blog.util.MessageLocalizer

/**
 * Base class for all blog-specific exceptions.
 * Supports localization of error messages based on the user's preferred language.
 *
 * @param messageKey The key for the localized message
 * @param code The HTTP status code to return
 */
abstract class BlogException(
    val messageKey: String,
    val code: Int
) : Exception(MessageLocalizer.getLocalizedMessage(messageKey)) {

    /**
     * Get the localized message for this exception.
     *
     * @param language The language to use for localization, or null to use default
     * @return The localized message
     */
    fun getLocalizedMessage(language: Language? = null): String {
        return MessageLocalizer.getLocalizedMessage(messageKey, language)
    }

    override fun equals(other: Any?): Boolean {
        return other is BlogException && other.message == this.message && other.code == this.code
    }

    override fun hashCode(): Int {
        return 31 * message.hashCode() + code.hashCode()
    }
}
