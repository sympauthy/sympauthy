package com.sympauthy.mail

import com.sympauthy.util.renderOrNull
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateModelException
import freemarker.template.TemplateScalarModel
import io.micronaut.context.MessageSource
import io.micronaut.email.*
import io.micronaut.email.template.TemplateBody
import io.micronaut.views.ModelAndView
import java.util.*

/**
 *
 * The sender is configured using the Jakarta mail properties: mail.from.
 */
class TemplatedMailBuilder(
    htmlTemplate: String? = null,
    textTemplate: String? = null,
    private val locale: Locale,
    private val messageSource: MessageSource,
) {
    val builder: Email.Builder
    private val model = mutableMapOf<String, Any>()

    init {
        val htmlBody = htmlTemplate
            ?.let { ModelAndView(it, model) }
            ?.let { TemplateBody(BodyType.HTML, it) }
        val textBody = textTemplate
            ?.let { ModelAndView(it, model) }
            ?.let { TemplateBody(BodyType.TEXT, it) }
        val body = when {
            htmlBody != null && textBody != null -> MultipartBody(htmlBody, textBody)
            htmlBody != null -> htmlBody
            textBody != null -> textBody
            else -> StringBody("")
        }

        builder = Email.builder()
            .body(body)

        model[MESSAGE_MODEL_KEY] = MessageMethod()
        model[LANG_MODEL_KEY] = locale.toLanguageTag()
    }

    fun sender(email: String) = email.let(::Contact).let { this.sender(it) }

    fun sender(contact: Contact) = this.apply {
        builder.from(contact)
    }

    fun receiver(email: String) = this.apply {
        builder.to(email)
    }

    fun localizedSubject(messageKey: String) = this.apply {
        localizeMessage(messageKey)?.let(builder::subject)
    }

    /**
     * Get the localized message using the [messageKey] and set it as title of the email.
     * The title is often shown in email notifications, like on Android devices.
     */
    fun localizedTitle(messageKey: String) = this.apply {
        setLocalizedText(TITLE_MODEL_KEY, messageKey)
    }

    /**
     * Get the localized message using the [messageKey] and set it as preview text of the email.
     */
    fun localizedPreviewText(messageKey: String) = this.apply {
        setLocalizedText(PREVIEW_TEXT_MODEL_KEY, messageKey)
    }

    fun set(key: String, value: Any) = this.apply { this.model[key] = value }

    /**
     * Get the localized message using the [messageKey] and set it in the model with the [key].
     */
    fun setLocalizedText(key: String, messageKey: String) = this.apply {
        localizeMessage(messageKey)?.let { set(key, it) }
    }

    internal fun localizeMessage(messageKey: String): String? {
        return messageSource.renderOrNull(messageKey, locale, model)
    }

    /**
     * `msg(key)` in a template: the message [key] names in the mail bundle, in the locale of the mail and
     * interpolated with its model, so that a template holds the structure and the bundle every string.
     *
     * A key the bundle does not hold fails the rendering rather than mailing a dotted identifier.
     */
    private inner class MessageMethod : TemplateMethodModelEx {
        override fun exec(arguments: MutableList<Any?>?): Any {
            val key = (arguments?.singleOrNull() as? TemplateScalarModel)?.asString
                ?: throw TemplateModelException("msg takes the key of one message.")
            return localizeMessage(key) ?: throw TemplateModelException("The mail bundle has no message $key.")
        }
    }

    companion object {
        const val TITLE_MODEL_KEY = "title"
        const val PREVIEW_TEXT_MODEL_KEY = "previewText"
        const val BRAND_MODEL_KEY = "brand"
        const val DISPLAY_NAME_MODEL_KEY = "displayName"
        const val MESSAGE_MODEL_KEY = "msg"
        const val LANG_MODEL_KEY = "lang"
    }
}
