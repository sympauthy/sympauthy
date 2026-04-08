package com.sympauthy.api.mapper

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.resource.error.OAuth2ErrorResource
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.orNull
import com.sympauthy.server.ErrorMessages
import io.micronaut.context.MessageSource
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

@Singleton
class OAuth2ErrorResourceMapper(
    @Inject @param:ErrorMessages private val messageSource: MessageSource,
    @Inject private val featuresConfig: FeaturesConfig
) {

    fun toResource(
        error: OAuth2Exception,
        locale: Locale
    ): OAuth2ErrorResource {
        val descriptionId = error.descriptionId ?: error.errorCode.defaultDescriptionId
        return OAuth2ErrorResource(
            errorCode = error.errorCode.errorCode,
            details = if (featuresConfig.orNull()?.printDetailsInError == true) {
                error.detailsId.translate(error, locale)
            } else null,
            description = descriptionId.translate(error, locale)
        )
    }

    private fun String?.translate(error: OAuth2Exception, locale: Locale): String? {
        return this?.let { messageSource.getMessage(this, locale, error.values) }
            ?.orElse(null)
    }
}
