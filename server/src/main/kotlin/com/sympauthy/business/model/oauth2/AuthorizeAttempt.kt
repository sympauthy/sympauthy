package com.sympauthy.business.model.oauth2

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

    // Attempt metadata
    /**
     * When the user initiated the authentication.
     */
    val attemptDate: LocalDateTime,

    // Authorize endpoint fields
    /**
     * The identifier of the client that initiated the authentication.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-2.2">Client identifier</a>
     */
    val clientId: String,
    /**
     * The URI where we must redirect the user once the authentication flow is finished.
     */
    val redirectUri: String,
    /**
     * Sanitized scopes requested by the client.
     * Un-allowed and invalid scopes have already been filtered out.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-3.3">Scope</a>
     */
    val requestedScopes: List<String>,
    /**
     * The state passed by the client to the authorize endpoint.
     */
    val state: String? = null,
    /**
     * The nonce passed by the client to the authorize endpoint.
     */
    val nonce: String? = null,
    /**
     * The PKCE code challenge provided during authorization (RFC 7636).
     */
    val codeChallenge: String? = null,
    /**
     * The PKCE code challenge method used (RFC 7636).
     */
    val codeChallengeMethod: CodeChallengeMethod? = null,

    // Third-party provider
    /**
     * The identifier of the third-party provider the user is authenticating with.
     */
    val providerId: String? = null,
    /**
     * The random part (jti) of the nonce JWT sent to the OIDC provider in the authorization request.
     * Only the jti is stored; the full nonce JWT can be reconstructed using
     * [com.sympauthy.business.manager.auth.AuthorizeAttemptManager.buildProviderNonceOrNull].
     */
    val providerNonceJsonWebTokenId: UUID? = null,

    // User identification
    /**
     * The identifier of the user that was connected at the end of the authentication process.
     */
    val userId: UUID?,

    // Consent
    /**
     * Consentable scopes that the user consented to during the authorization process.
     */
    val consentedScopes: List<String>?,
    /**
     * When the consentable scopes were consented.
     */
    val consentedAt: LocalDateTime? = null,
    /**
     * How the consentable scopes were consented (auto or user).
     */
    val consentedBy: ConsentedBy? = null,

    // MFA
    /**
     * When the end-user successfully completed the MFA step for this authorization attempt.
     * Null if MFA has not been completed yet.
     */
    val mfaPassedDate: LocalDateTime? = null,

    // Granting / Authorization
    /**
     * Grantable scopes that were granted to the user through granting rules during the authorization process.
     */
    val grantedScopes: List<String>?,
    /**
     * When the grantable scopes were granted.
     */
    val grantedAt: LocalDateTime? = null,
    /**
     * How the grantable scopes were granted (auto or rule).
     */
    val grantedBy: GrantedBy? = null,
) : AuthorizeAttempt(
    id = id,
    authorizationFlowId = authorizationFlowId,
    expirationDate = expirationDate
) {
    /**
     * True if the end-user has successfully completed the MFA step for this authorization attempt.
     */
    val mfaPassed: Boolean get() = mfaPassedDate != null

    fun copy(
        userId: UUID? = null,
        consentedScopes: List<String>? = null,
        consentedAt: LocalDateTime? = null,
        consentedBy: ConsentedBy? = null,
        mfaPassedDate: LocalDateTime? = null,
        grantedScopes: List<String>? = null,
        grantedAt: LocalDateTime? = null,
        grantedBy: GrantedBy? = null,
    ) = OnGoingAuthorizeAttempt(
        id = this.id,
        authorizationFlowId = this.authorizationFlowId,
        expirationDate = this.expirationDate,
        attemptDate = this.attemptDate,
        clientId = this.clientId,
        redirectUri = this.redirectUri,
        requestedScopes = this.requestedScopes,
        state = this.state,
        nonce = this.nonce,
        codeChallenge = this.codeChallenge,
        codeChallengeMethod = this.codeChallengeMethod,
        providerId = this.providerId,
        providerNonceJsonWebTokenId = this.providerNonceJsonWebTokenId,
        userId = userId ?: this.userId,
        consentedScopes = consentedScopes ?: this.consentedScopes,
        consentedAt = consentedAt ?: this.consentedAt,
        consentedBy = consentedBy ?: this.consentedBy,
        mfaPassedDate = mfaPassedDate ?: this.mfaPassedDate,
        grantedScopes = grantedScopes ?: this.grantedScopes,
        grantedAt = grantedAt ?: this.grantedAt,
        grantedBy = grantedBy ?: this.grantedBy,
    )
}

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

    // Attempt metadata
    /**
     * When the user initiated the authentication.
     */
    val attemptDate: LocalDateTime,

    // Authorize endpoint fields
    /**
     * The identifier of the client that initiated the authentication.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-2.2">Client identifier</a>
     */
    val clientId: String,
    /**
     * The URI where we must redirect the user once the authentication flow is finished.
     */
    val redirectUri: String,
    /**
     * Sanitized scopes requested by the client.
     * Un-allowed and invalid scopes have already been filtered out.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-3.3">Scope</a>
     */
    val requestedScopes: List<String>,
    /**
     * The state passed by the client to the authorize endpoint.
     */
    val state: String? = null,
    /**
     * The nonce passed by the client to the authorize endpoint.
     */
    val nonce: String? = null,
    /**
     * The PKCE code challenge provided during authorization (RFC 7636).
     */
    val codeChallenge: String? = null,
    /**
     * The PKCE code challenge method used (RFC 7636).
     */
    val codeChallengeMethod: CodeChallengeMethod? = null,

    // User identification
    /**
     * The identifier of the user that was connected at the end of the authentication process.
     */
    val userId: UUID,

    // Consent
    /**
     * Consentable scopes that the user consented to during the authorization process.
     */
    val consentedScopes: List<String>,
    /**
     * When the consentable scopes were consented.
     */
    val consentedAt: LocalDateTime,
    /**
     * How the consentable scopes were consented (auto or user).
     */
    val consentedBy: ConsentedBy,

    // Granting / Authorization
    /**
     * Grantable scopes that were granted to the user through granting rules during the authorization process.
     */
    val grantedScopes: List<String>,
    /**
     * When the grantable scopes were granted.
     */
    val grantedAt: LocalDateTime,
    /**
     * How the grantable scopes were granted (auto or rule).
     */
    val grantedBy: GrantedBy,
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
)
