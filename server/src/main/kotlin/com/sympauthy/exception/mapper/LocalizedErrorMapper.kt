package com.sympauthy.exception.mapper

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.orNull
import com.sympauthy.exception.LocalizedException
import com.sympauthy.exception.model.LocalizedError
import com.sympauthy.server.ErrorMessages
import io.micronaut.context.MessageSource
import io.micronaut.http.HttpStatus
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

@Singleton
class LocalizedErrorMapper(
    @Inject @param:ErrorMessages private val messageSource: MessageSource,
    @Inject private val featuresConfig: FeaturesConfig
) {

    fun toLocalizedError(
        exception: LocalizedException,
        locale: Locale
    ): LocalizedError {
        val exceptionStatus = when (exception) {
            is LocalizedHttpException -> exception.status
            is BusinessException -> exception.recommendedStatus
            else -> null
        }
        val status = when {
            exceptionStatus != null -> exceptionStatus
            exception.recoverable -> HttpStatus.BAD_REQUEST
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
        val descriptionId = when {
            exception.descriptionId != null -> exception.descriptionId
            exception.recoverable -> "description.bad_request"
            else -> "description.internal_server_error"
        }

        val localizedDescription = messageSource.getMessage(descriptionId, locale, exception.values).orElse(null)
        val localizedDetails = if (featuresConfig.orNull()?.printDetailsInError == true) {
            messageSource.getMessage(exception.detailsId, locale, exception.values).orElse(null)
        } else null

        return LocalizedError(
            httpStatus = status,
            errorCode = exception.detailsId,
            description = localizedDescription,
            details = localizedDetails
        )
    }
}
