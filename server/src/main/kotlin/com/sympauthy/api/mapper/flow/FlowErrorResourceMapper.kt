package com.sympauthy.api.mapper.flow

import com.sympauthy.api.resource.flow.FlowErrorResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.server.ErrorMessages
import io.micronaut.context.MessageSource
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.util.*

@Singleton
class FlowErrorResourceMapper(
    @Inject @param:ErrorMessages private val messageSource: MessageSource
) {

    fun toResource(
        exception: BusinessException,
        locale: Locale
    ): FlowErrorResource {
        val descriptionId = when {
            exception.descriptionId != null -> exception.descriptionId
            exception.recommendedStatus?.code in 500 until 600 -> "description.internal_server_error"
            else -> null
        }

        return FlowErrorResource(
            errorCode = exception.detailsId,
            description = descriptionId?.let { messageSource.getMessage(it, locale, exception.values) }?.orElse(null),
            details = messageSource.getMessage(exception.detailsId, locale, exception.values).orElse(null),
        )
    }

    fun toResource(redirectUri: URI): FlowErrorResource {
        return FlowErrorResource(
            redirectUrl = redirectUri.toString(),
        )
    }
}
