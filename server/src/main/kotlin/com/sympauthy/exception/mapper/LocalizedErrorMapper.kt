package com.sympauthy.exception.mapper

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.orNull
import com.sympauthy.exception.LocalizedException
import com.sympauthy.exception.model.LocalizedError
import com.sympauthy.exception.model.LocalizedPropertyError
import com.sympauthy.server.ErrorMessages
import com.sympauthy.util.renderOrNull
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

    /**
     * The failure [exception] rendered against [locale]: its code, the message the end-user is shown
     * and the code that message was read under, and the technical message where it may be printed.
     *
     * **[alwaysPrintDetails] is passed by a caller whose reader is the audience the technical message
     * is written for.** `features.print-details-in-error` keeps the server's internals away from a
     * caller nobody vouched for, and the API standard names the surfaces that are not one. It only
     * ever turns the message on: a deployment that asked for it is not answered with less because a
     * caller said so.
     */
    fun toLocalizedError(
        exception: LocalizedException,
        locale: Locale,
        alwaysPrintDetails: Boolean = false
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
        val printDetails = alwaysPrintDetails || featuresConfig.orNull()?.printDetailsInError == true

        val localizedDescription = messageSource.renderOrNull(descriptionId, locale, exception.values)
        val localizedDetails = if (printDetails) {
            messageSource.renderOrNull(exception.detailsId, locale, exception.values)
        } else null

        return LocalizedError(
            httpStatus = status,
            errorCode = exception.detailsId,
            descriptionId = descriptionId,
            description = localizedDescription,
            details = localizedDetails,
            properties = (exception as? LocalizedHttpException)?.propertyErrors.orEmpty()
                .map { (path, propertyException) ->
                    toLocalizedPropertyError(path, propertyException, locale, printDetails)
                }
        )
    }

    /**
     * The failure [exception] of the property at [path], rendered against [locale] the way the error
     * holding it is: its own code, its own description and its own technical message, behind the
     * same flag [printDetails] carries.
     *
     * A failure naming no description contributes none. The fallback above answers for the response
     * as a whole, and answering it per property would print *an unexpected error occurred* against a
     * field a reader can correct.
     */
    private fun toLocalizedPropertyError(
        path: String,
        exception: LocalizedException,
        locale: Locale,
        printDetails: Boolean
    ) = LocalizedPropertyError(
        path = path,
        errorCode = exception.detailsId,
        description = exception.descriptionId?.let { messageSource.renderOrNull(it, locale, exception.values) },
        details = if (printDetails) {
            messageSource.renderOrNull(exception.detailsId, locale, exception.values)
        } else null
    )
}
