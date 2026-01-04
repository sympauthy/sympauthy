package com.sympauthy.api.mapper

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.resource.error.ErrorResource
import com.sympauthy.api.resource.error.PropertyErrorResource
import com.sympauthy.exception.LocalizedException
import com.sympauthy.server.ErrorMessages
import io.micronaut.context.MessageSource
import io.micronaut.http.HttpStatus
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

@Singleton
class ErrorResourceMapper(
    @Inject @param:ErrorMessages private val messageSource: MessageSource
) {

    fun toResource(
        status: HttpStatus,
        exception: LocalizedException,
        locale: Locale
    ): ErrorResource {
        val descriptionId = when {
            exception.descriptionId != null -> exception.descriptionId
            status.code in 500 until 600 -> "description.internal_server_error"
            else -> null
        }

        return ErrorResource(
            status = status.code,
            errorCode = exception.detailsId,
            description = descriptionId?.let { messageSource.getMessage(it, locale, exception.values) }?.orElse(null),
            details = messageSource.getMessage(exception.detailsId, locale, exception.values).orElse(null),
            properties = null
        )
    }

    fun toResource(
        exception: LocalizedHttpException,
        locale: Locale
    ): ErrorResource {
        return toResource(exception.status, exception, locale).copy(
            properties = toPropertyResources(exception, locale)
        )
    }

    private fun toPropertyResources(
        exception: LocalizedHttpException,
        locale: Locale
    ): List<PropertyErrorResource>? {
        return exception.additionalInfo
            .mapNotNull {
                PropertyErrorResource(
                    path = it.path ?: return@mapNotNull null,
                    description = it.descriptionId
                        ?.let { messageSource.getMessage(it, locale, exception.values) }
                        ?.orElse(null)
                )
            }
            .takeIf { it.isNotEmpty() }
    }

    /*
    fun toErrorResource(
        exception: ConstraintViolationException,
        locale: Locale
    ): ErrorResource {
        val messageResourceName = "exception.validation.invalid"
        return ErrorResource(
            status = HttpStatus.BAD_REQUEST.code,
            errorCode = messageResourceName,
            message = messageSource.getMessage(messageResourceName, locale).orElse(null),
            additionalMessages = exception.constraintViolations
                .map { toAdditionalMessageResource(it, locale) }
                .ifEmpty { null }
        )
    }

    private fun toAdditionalMessageResource(
        constraintViolation: ConstraintViolation<*>,
        locale: Locale
    ): AdditionalMessageResource {
        val context = MessageContext.of(locale)
        return AdditionalMessageResource(
            path = constraintViolation.pathAsString,
            message = messageSource.interpolate(constraintViolation.messageTemplate, context)
        )
    }
     */
}
