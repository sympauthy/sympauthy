package com.sympauthy.api.errorhandler

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.exception.UnsupportedMethodException
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.exception.recoverableHttpExceptionOf
import com.sympauthy.api.exception.toHttpException
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.exception.LocalizedException
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpStatus.*
import io.micronaut.http.annotation.Body
import io.micronaut.http.exceptions.BufferLengthExceededException
import io.micronaut.http.exceptions.ContentLengthExceededException
import io.micronaut.http.server.exceptions.NotAcceptableException
import io.micronaut.http.server.exceptions.NotAllowedException
import io.micronaut.http.server.exceptions.NotFoundException
import io.micronaut.http.server.exceptions.UnsupportedMediaException
import io.micronaut.json.JsonSyntaxException
import io.micronaut.security.authentication.AuthenticationException
import io.micronaut.security.authentication.AuthorizationException
import io.micronaut.web.router.exceptions.UnsatisfiedRequestAttributeRouteException
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException
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
            is AuthenticationException -> unauthorized()
            is NotFoundException -> httpExceptionOf(NOT_FOUND, "not_found", "description.not_found")
            is NotAllowedException -> UnsupportedMethodException(throwable.requestMethod, throwable.allowedMethods)
            is UnsupportedMediaException -> recoverableHttpExceptionOf(
                UNSUPPORTED_MEDIA_TYPE, "content_type.unsupported", "description.content_type.unsupported",
                "contentType" to throwable.contentType.orEmpty(),
                "supportedContentTypes" to throwable.acceptableContentTypes.joinToString(", ")
            )
            is NotAcceptableException -> recoverableHttpExceptionOf(
                NOT_ACCEPTABLE, "accept.unsupported", "description.accept.unsupported",
                "accept" to throwable.acceptedTypes.joinToString(", "),
                "producedContentTypes" to throwable.produceableContentTypes.joinToString(", ")
            )
            is ConversionErrorException -> toMalformedException(throwable)
            is JsonSyntaxException -> bodyMalformed()
            // A request attribute is set by a filter of this server, never sent by the caller.
            is UnsatisfiedRequestAttributeRouteException -> internalServerError(throwable)
            is UnsatisfiedRouteException -> toMissingException(throwable.argument)
            is UnsatisfiedArgumentException -> toMissingException(throwable.argument)
            is ContentLengthExceededException, is BufferLengthExceededException -> recoverableHttpExceptionOf(
                REQUEST_ENTITY_TOO_LARGE, "body.too_large", "description.body.too_large"
            )
            is LocalizedHttpException -> throwable
            is BusinessException -> throwable.toHttpException(throwable.recommendedStatus ?: INTERNAL_SERVER_ERROR)
            is LocalizedException -> throwable.toHttpException(INTERNAL_SERVER_ERROR)
            else -> internalServerError(throwable)
        }
    }

    private fun internalServerError(throwable: Throwable) = httpExceptionOf(
        status = INTERNAL_SERVER_ERROR,
        detailsId = "internal_server_error",
        descriptionId = "description.internal_server_error",
        throwable = throwable
    )

    private fun toException(exception: AuthorizationException): LocalizedHttpException {
        return if (exception.authentication != null && exception.isForbidden) {
            httpExceptionOf(
                status = FORBIDDEN,
                detailsId = "forbidden",
                descriptionId = "description.forbidden"
            )
        } else {
            unauthorized()
        }
    }

    private fun unauthorized() = recoverableHttpExceptionOf(
        status = UNAUTHORIZED,
        detailsId = "unauthorized",
        descriptionId = "description.unauthorized"
    )

    /**
     * The refusal of a request whose value for the argument of [exception] does not convert to the type
     * the operation reads it as: `body.malformed` where that argument is the body, and
     * `parameter.malformed`, naming the parameter and the value sent, where it is anything else.
     *
     * A body that is not JSON at all reaches [normalize] as a [JsonSyntaxException] instead, and is
     * answered with the same code: either way, the body cannot be read as the one the operation takes.
     */
    private fun toMalformedException(exception: ConversionErrorException): LocalizedHttpException {
        val argument = exception.argument
        if (argument.isBody()) {
            return bodyMalformed()
        }
        return recoverableHttpExceptionOf(
            BAD_REQUEST, "parameter.malformed", "description.parameter.malformed",
            "parameter" to argument.wireName(),
            "value" to (exception.conversionError.originalValue.orElse(null)?.toString() ?: "")
        )
    }

    /**
     * The refusal of a request carrying nothing for an [argument] the operation requires:
     * `body.missing` where the [argument] is the body, and `parameter.missing`, naming the parameter,
     * where it is anything else.
     */
    private fun toMissingException(argument: Argument<*>): LocalizedHttpException {
        if (argument.isBody()) {
            return recoverableHttpExceptionOf(BAD_REQUEST, "body.missing", "description.body.missing")
        }
        return recoverableHttpExceptionOf(
            BAD_REQUEST, "parameter.missing", "description.parameter.missing",
            "parameter" to argument.wireName()
        )
    }

    private fun bodyMalformed() = recoverableHttpExceptionOf(
        BAD_REQUEST, "body.malformed", "description.body.malformed"
    )

    private fun Argument<*>.isBody() = annotationMetadata.hasAnnotation(Body::class.java)

    /**
     * The name the request carries this argument under, which the binding annotation declares where it
     * differs from the Kotlin name.
     */
    private fun Argument<*>.wireName(): String =
        annotationMetadata.stringValue(Bindable::class.java).filter(String::isNotEmpty).orElse(name)
}
