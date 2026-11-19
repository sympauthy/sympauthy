package com.sympauthy.api.errorhandler

import com.sympauthy.api.exception.AccessTokenChallengeException
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.exception.UnsupportedMethodException
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.exception.recoverableHttpExceptionOf
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InsufficientScope
import com.sympauthy.security.accessTokenSchemeOrNull
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpStatus.*
import io.micronaut.http.annotation.Body
import io.micronaut.http.exceptions.BufferLengthExceededException
import io.micronaut.http.exceptions.ContentLengthExceededException
import io.micronaut.http.exceptions.HttpStatusException
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
 * Normalizes every exception — those thrown by Micronaut Security and the other frameworks included — into a
 * [LocalizedHttpException] carrying the status to answer with, but an `OAuth2Exception` and a
 * `LocalizedException`, which their own handlers answer. An exception it does not recognise becomes a `500`, as
 * does one wrapping a `LocalizedException`.
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
            is HttpStatusException -> toStatusException(throwable)
            else -> internalServerError(throwable)
        }
    }

    private fun internalServerError(throwable: Throwable) = httpExceptionOf(
        status = INTERNAL_SERVER_ERROR,
        detailsId = "internal_server_error",
        descriptionId = "description.internal_server_error",
        throwable = throwable
    )

    /**
     * The refusal of a request the security rules turned away: `forbidden` where it carried an authentication
     * those rules do not accept, and `unauthorized` where it carried none.
     *
     * Either challenges the access token: `insufficient_scope` on the challenge of the scheme a token turned away
     * came under, and no error where the request presented nothing. A `forbidden` turning away anything but an
     * access token challenges nothing.
     */
    private fun toException(exception: AuthorizationException): LocalizedHttpException {
        val authentication = exception.authentication
        if (authentication == null || !exception.isForbidden) {
            return AccessTokenChallengeException(
                recoverable = true,
                detailsId = "unauthorized",
                descriptionId = "description.unauthorized",
                scheme = null,
                challenges = emptyList()
            )
        }
        val forbidden = httpExceptionOf(
            status = FORBIDDEN,
            detailsId = "forbidden",
            descriptionId = "description.forbidden"
        )
        val scheme = authentication.accessTokenSchemeOrNull ?: return forbidden
        return AccessTokenChallengeException(
            recoverable = false,
            detailsId = "forbidden",
            descriptionId = "description.forbidden",
            scheme = scheme,
            challenges = listOf(InsufficientScope(scopes = emptyList()))
        )
    }

    /**
     * The failure a framework answered with a status of its choosing, answered with that status: under the code
     * this normalizer gives a refusal of that status where it gives one, and `bad_request` where it gives none.
     *
     * A `5xx` is the server failing whatever the framework called it, and is answered as one it does not
     * recognise.
     */
    private fun toStatusException(exception: HttpStatusException): LocalizedHttpException = when {
        exception.status.code >= INTERNAL_SERVER_ERROR.code -> internalServerError(exception)
        exception.status == NOT_FOUND -> httpExceptionOf(NOT_FOUND, "not_found", "description.not_found")
        exception.status == UNAUTHORIZED -> unauthorized()
        exception.status == FORBIDDEN -> httpExceptionOf(FORBIDDEN, "forbidden", "description.forbidden")
        else -> httpExceptionOf(exception.status, "bad_request", "description.bad_request")
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
