package com.sympauthy.api.mapper

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.resource.error.OAuth2ErrorResource
import com.sympauthy.server.ErrorMessages
import io.micronaut.context.MessageSource
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

@Singleton
class OAuth2ErrorResourceMapper(
    @Inject @param:ErrorMessages private val messageSource: MessageSource
) {

    fun toResource(
        error: OAuth2Exception,
        locale: Locale
    ): OAuth2ErrorResource {
        return OAuth2ErrorResource(
            errorCode = error.errorCode.errorCode,
            details = error.detailsId.translate(error, locale),
            description = error.descriptionId.translate(error, locale)
        )
    }

    private fun String?.translate(error: OAuth2Exception, locale: Locale): String? {
        return this?.let { messageSource.getMessage(this, locale, error.values) }
            ?.orElse(null)
    }
}
