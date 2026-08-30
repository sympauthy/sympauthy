package com.sympauthy.business.manager.mail

import com.sympauthy.util.renderOrNull
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

    companion object {
        const val TITLE_MODEL_KEY = "title"
        const val PREVIEW_TEXT_MODEL_KEY = "previewText"
    }
}
