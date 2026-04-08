package com.sympauthy.api.errorhandler

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.exception.toHttpException
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.exception.LocalizedException
import io.micronaut.http.HttpStatus.*
import io.micronaut.security.authentication.AuthorizationException
import jakarta.inject.Singleton

/**
 * Normalize all kinds of exceptions thrown by various frameworks like Micronaut Security
 * into our common [LocalizedHttpException] with proper http status to respond and details/description
 * for the end-user.
 *
 * Currently unhandled exception will be converted into 500 errors.
 */
@Singleton
class ExceptionNormalizer {

    fun normalize(throwable: Throwable): LocalizedHttpException {
        return when (throwable) {
            is AuthorizationException -> toException(throwable)
            is LocalizedHttpException -> throwable
            is BusinessException -> throwable.toHttpException(throwable.recommendedStatus ?: INTERNAL_SERVER_ERROR)
            is LocalizedException -> throwable.toHttpException(INTERNAL_SERVER_ERROR)
            else -> httpExceptionOf(
                status = INTERNAL_SERVER_ERROR,
                detailsId = "internal_server_error",
                descriptionId = "description.internal_server_error",
                throwable = throwable
            )
        }
    }

    private fun toException(exception: AuthorizationException): LocalizedHttpException {
        return if (exception.authentication != null && exception.isForbidden) {
            httpExceptionOf(
                status = FORBIDDEN,
                detailsId = "forbidden",
                descriptionId = "description.forbidden"
            )
        } else {
            httpExceptionOf(
                status = UNAUTHORIZED,
                detailsId = "unauthorized",
                descriptionId = "description.unauthorized"
            )
        }
    }
}
