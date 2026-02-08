package com.sympauthy.business.model.oauth2

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.Expirable
import java.time.LocalDateTime
import java.util.*

/**
 * Hold every information about an end-user authentication attempt.
 *
 * Designer note: By defining a sealed class, we ensure that business logic can only interact on ongoing attempts
 * without having to check the status of the attempt in every business method. It forces the developer to check the
 * status of the attempt before interacting with it.
 */
sealed class AuthorizeAttempt(
    /**
     * An uniq identifier for the flow.
     */
    val id: UUID,

    /**
     * The identifier of the authorization flow the user is going through.
     * null for non-interactive flows.
     */
    val authorizationFlowId: String?,

    override val expirationDate: LocalDateTime
) : Expirable

/**
 * Represents an authentication attempt that is ongoing.
 */
class OnGoingAuthorizeAttempt(
    id: UUID,
    authorizationFlowId: String?,
    expirationDate: LocalDateTime,
    /**
     * The identifier of the client that initiated the authentication.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-2.2">Client identifier</a>
     */
    val clientId: String,
    /**
     * Sanitized scopes requested by the client.
     * Un-allowed and invalid scopes have already been filtered out.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-3.3">Scope</a>
     */
    val requestedScopes: List<String>,
    /**
     * The URI where we must redirect the user once the authentication flow is finished.
     */
    val redirectUri: String,
    /**
     * The state passed by the client to the authorize endpoint.
     */
    val state: String? = null,
    /**
     * The nonce passed by the client to the authorize endpoint.
     */
    val nonce: String? = null,
    /**
     * The identifier of the user that was connected at the end of the authentication process.
     */
    val userId: UUID?,
    /*
     * The scopes that were granted to the user during the authorization process.
     */
    val grantedScopes: List<String>?,
    /**
     * When the user initiated the authentication.
     */
    val attemptDate: LocalDateTime,
) : AuthorizeAttempt(
    id = id,
    authorizationFlowId = authorizationFlowId,
    expirationDate = expirationDate
)

/**
 * Represents an authentication attempt that has completed.
 *
 * Once the [AuthorizeAttempt] is completed, it can be considered as a successful authentication and this authorization
 * server will emit an [AuthenticationToken].
 */
class CompletedAuthorizeAttempt(
    id: UUID,
    authorizationFlowId: String?,
    expirationDate: LocalDateTime,
    /**
     * The identifier of the client that initiated the authentication.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-2.2">Client identifier</a>
     */
    val clientId: String,
    /**
     * Sanitized scopes requested by the client.
     * Un-allowed and invalid scopes have already been filtered out.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-3.3">Scope</a>
     */
    val requestedScopes: List<String>,
    /**
     * The URI where we must redirect the user once the authentication flow is finished.
     */
    val redirectUri: String,
    /**
     * The state passed by the client to the authorize endpoint.
     */
    val state: String? = null,
    /**
     * The nonce passed by the client to the authorize endpoint.
     */
    val nonce: String? = null,
    /**
     * The identifier of the user that was connected at the end of the authentication process.
     */
    val userId: UUID,
    /*
     * The scopes that were granted to the user during the authorization process.
     */
    val grantedScopes: List<String>,
    /**
     * When the user initiated the authentication.
     */
    val attemptDate: LocalDateTime,
    /**
     * When the user has completed the authorization flow.
     */
    val completeDate: LocalDateTime,
) : AuthorizeAttempt(
    id = id,
    authorizationFlowId = authorizationFlowId,
    expirationDate = expirationDate
)

/**
 * Represents an authentication attempt that failed.
 */
class FailedAuthorizeAttempt(
    id: UUID,
    authorizationFlowId: String?,
    expirationDate: LocalDateTime,
    /**
     * Identifier of the message detailing, in a technical way, the error which caused this attempt to fail.
     * This value is copied from the non-recoverable business exception thrown during the execution of the flow.
     * Empty if the attempt has not failed yet.
     */
    val errorDetailsId: String,

    /**
     * Identifier of the message detailing, for the end-user, the error which caused this attempt to fail.
     * This value is copied from the non-recoverable business exception thrown during the execution of the flow.
     * Empty if the attempt has not failed yet.
     */
    val errorDescriptionId: String? = null,

    /**
     * Value to expose to the mustache template to inject values into the localized error messages.
     */
    val errorValues: Map<String, String>? = null,

    /**
     * When the authentication process failed.
     */
    val errorDate: LocalDateTime,
) : AuthorizeAttempt(
    id = id,
    authorizationFlowId = authorizationFlowId,
    expirationDate = expirationDate
) {
    /**
     * The business exception that caused this attempt to fail.
     */
    val businessException: BusinessException
        get() = businessExceptionOf(
            detailsId = errorDetailsId,
            descriptionId = errorDescriptionId,
            values = errorValues?.map { it.key to it.value }?.toTypedArray() ?: emptyArray(),
        )
}
