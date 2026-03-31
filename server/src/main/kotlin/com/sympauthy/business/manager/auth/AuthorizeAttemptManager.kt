package com.sympauthy.business.manager.auth

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.AuthorizeAttemptMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.AuthorizeAttemptEntity
import com.sympauthy.data.repository.AuthorizeAttemptRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.time.LocalDateTime
import java.util.*

/**
 * Manager in charge of the lifecycle of an authorization attempt.
 * It provides methods to:
 * - create an authorization attempt after validation.
 * - verify the state of an authorization attempt.
 * - modify the attempt: user id, granted scopes, error.
 *
 * Note: For separation of concerns, this manager does not handle any logic of the authorization flow.
 * Managers handle those logics are in the flow package.
 */
@Singleton
class AuthorizeAttemptManager(
    @Inject private val userManager: UserManager,
    @Inject private val jwtManager: JwtManager,
    @Inject private val authorizeAttemptRepository: AuthorizeAttemptRepository,
    @Inject private val authorizeAttemptMapper: AuthorizeAttemptMapper,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    /**
     * Create a new [AuthorizeAttempt] for the end-user and save it in the database.
     *
     * The [AuthorizeAttempt] is saved even if the validation of some parameters failed.
     */
    suspend fun newAuthorizeAttempt(
        client: Client? = null,
        clientState: String? = null,
        clientNonce: String? = null,
        authorizationFlow: AuthorizationFlow? = null,
        scopes: List<Scope>? = null,
        redirectUri: URI? = null,
        codeChallenge: String? = null,
        codeChallengeMethod: CodeChallengeMethod? = null,
        error: BusinessException? = null
    ): AuthorizeAttempt {
        val now = LocalDateTime.now()

        // When no error, auto-consent consentable scopes at creation time
        val consentableScopes = if (error == null) {
            (scopes ?: emptyList()).filterIsInstance<ConsentableUserScope>()
        } else null

        val entity = AuthorizeAttemptEntity(
            clientId = client?.id,
            authorizationFlowId = authorizationFlow?.id,
            redirectUri = redirectUri.toString(),
            requestedScopes = (scopes ?: emptyList()).map(Scope::scope).toTypedArray(),
            state = clientState,
            nonce = clientNonce,

            errorDate = error?.let { now },
            errorDetailsId = error?.detailsId,
            errorDescriptionId = error?.descriptionId,
            errorValues = error?.values,

            consentedScopes = consentableScopes?.map(Scope::scope)?.toTypedArray(),
            consentedAt = consentableScopes?.let { now },
            consentedBy = consentableScopes?.let { ConsentedBy.AUTO.name },

            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod?.value,

            attemptDate = now,
            expirationDate = now.plus(uncheckedAuthConfig.orThrow().authorizationCode.expiration)
        )
        authorizeAttemptRepository.save(entity)

        return authorizeAttemptMapper.toAuthorizeAttempt(entity)
    }

    suspend fun checkIsExistingAttemptWithState(
        uncheckedClientState: String?
    ) {
        val existingAttempt = uncheckedClientState?.let {
            authorizeAttemptRepository.findByState(it)
        }
        if (existingAttempt != null) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "authorize.existing_state", "description.oauth2.replay")
        }
    }

    suspend fun encodeState(authorizeAttempt: AuthorizeAttempt): String {
        return jwtManager.create(STATE_KEY_NAME) {
            subject(authorizeAttempt.id.toString())
        }
    }

    /**
     * Store the third-party provider the user is authenticating with.
     * For OIDC providers, generates a signed nonce JWT bound to the authorize attempt.
     * Only the random part (jti) is stored in the database; the full JWT is reconstructed
     * on callback via [buildProviderNonce].
     *
     * @return the full nonce JWT if [generateNonce] is true, null otherwise.
     */
    suspend fun setProvider(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        providerId: String,
        generateNonce: Boolean = false
    ): String? {
        val jti = if (generateNonce) UUID.randomUUID() else null

        authorizeAttemptRepository.updateProviderIdProviderNonceJsonWebTokenId(
            id = authorizeAttempt.id,
            providerId = providerId,
            providerNonceJsonWebTokenId = jti
        )

        return if (jti != null) buildProviderNonce(authorizeAttempt.id, jti) else null
    }

    /**
     * Reconstruct the full provider nonce JWT from the authorize attempt's stored jti.
     * Returns null if the attempt has no provider nonce.
     */
    suspend fun buildProviderNonceOrNull(authorizeAttempt: OnGoingAuthorizeAttempt): String? {
        val jti = authorizeAttempt.providerNonceJsonWebTokenId ?: return null
        return buildProviderNonce(authorizeAttempt.id, jti)
    }

    private suspend fun buildProviderNonce(attemptId: UUID, jti: UUID): String {
        return jwtManager.create(PROVIDER_NONCE_KEY_NAME) {
            subject(attemptId.toString())
            jwtID(jti.toString())
        }
    }

    /**
     * Return a [SuccessVerifyEncodedStateResult] containing the [AuthorizeAttempt] that created the [state]
     * after verifying the [state] has not been tempered with.
     * Otherwise, return a [FailedVerifyEncodedStateResult] with the appropriate error details.
     *
     * Note: This method does not check the status of the [AuthorizeAttempt] in order to let the
     */
    suspend fun verifyEncodedInternalState(state: String?): VerifyEncodedStateResult {
        if (state.isNullOrBlank()) {
            return FailedVerifyEncodedStateResult(
                detailsId = "auth.authorize_attempt.validate.missing_state",
                descriptionId = "description.oauth2.invalid_state"
            )
        }
        val jwt = jwtManager.decodeAndVerifyOrNull(STATE_KEY_NAME, state) ?: return FailedVerifyEncodedStateResult(
            detailsId = "auth.authorize_attempt.validate.wrong_signature",
            descriptionId = "description.oauth2.invalid_state"
        )
        val attemptId = try {
            UUID.fromString(jwt.subject)
        } catch (e: IllegalArgumentException) {
            return FailedVerifyEncodedStateResult(
                detailsId = "auth.authorize_attempt.validate.invalid_subject",
                descriptionId = "description.oauth2.invalid_state"
            )
        }
        val authorizeAttempt = authorizeAttemptRepository.findById(attemptId)
            ?.let(authorizeAttemptMapper::toAuthorizeAttempt)

        return if (authorizeAttempt != null) {
            SuccessVerifyEncodedStateResult(authorizeAttempt)
        } else {
            FailedVerifyEncodedStateResult(
                detailsId = "auth.authorize_attempt.validate.missing_attempt",
                descriptionId = "description.oauth2.expired",
                values = mapOf("attemptId" to attemptId.toString())
            )
        }
    }

    /**
     * Associate the user that have been authenticated to its [AuthorizeAttempt].
     * Do nothing if the [authorizeAttempt] has already been completed or is already in error.
     */
    suspend fun setAuthenticatedUserId(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        userId: UUID
    ): OnGoingAuthorizeAttempt {
        authorizeAttemptRepository.updateUserId(
            id = authorizeAttempt.id,
            userId = userId
        )
        return authorizeAttempt.copy(
            userId = userId
        )
    }

    /**
     * Records that the end-user has passed the MFA step for this [authorizeAttempt].
     * Persists the [mfaPassedDate] and returns the updated attempt.
     */
    suspend fun setMfaPassed(
        authorizeAttempt: OnGoingAuthorizeAttempt
    ): OnGoingAuthorizeAttempt {
        val mfaPassedDate = LocalDateTime.now()
        authorizeAttemptRepository.updateMfaPassedDate(
            id = authorizeAttempt.id,
            mfaPassedDate = mfaPassedDate
        )
        return authorizeAttempt.copy(mfaPassedDate = mfaPassedDate)
    }

    /**
     * Set and save the grantable scopes granted through granting rules for this authorize attempt.
     */
    suspend fun setGrantedScopes(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        grantedScopes: List<Scope>,
        grantedBy: GrantedBy
    ): OnGoingAuthorizeAttempt {
        val grantedScopeIds = grantedScopes.map(Scope::scope)
        val grantedAt = LocalDateTime.now()
        authorizeAttemptRepository.updateGrantedScopes(
            id = authorizeAttempt.id,
            grantedScopes = grantedScopeIds,
            grantedAt = grantedAt,
            grantedBy = grantedBy.name
        )
        return authorizeAttempt.copy(
            grantedScopes = grantedScopeIds,
            grantedAt = grantedAt,
            grantedBy = grantedBy
        )
    }

    /**
     * Set and save the consentable scopes obtained through user consent for this authorize attempt.
     */
    suspend fun setConsentedScopes(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        consentedScopes: List<Scope>,
        consentedBy: ConsentedBy
    ): OnGoingAuthorizeAttempt {
        val consentedScopeIds = consentedScopes.map(Scope::scope)
        val consentedAt = LocalDateTime.now()
        authorizeAttemptRepository.updateConsentedScopes(
            id = authorizeAttempt.id,
            consentedScopes = consentedScopeIds,
            consentedAt = consentedAt,
            consentedBy = consentedBy.name
        )
        return authorizeAttempt.copy(
            consentedScopes = consentedScopeIds,
            consentedAt = consentedAt,
            consentedBy = consentedBy
        )
    }

    /**
     * Set and save the error if it is non-recoverable to prevent further usage of the [authorizeAttempt].
     * Do nothing if the [authorizeAttempt] has already been completed or is already in error.
     */
    suspend fun markAsFailedIfNotRecoverable(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        error: BusinessException
    ): AuthorizeAttempt {
        if (error.recoverable) return authorizeAttempt

        val errorDate = LocalDateTime.now()
        authorizeAttemptRepository.updateError(
            id = authorizeAttempt.id,
            errorDate = errorDate,
            errorDetailsId = error.detailsId,
            errorDescriptionId = error.descriptionId,
            errorValues = error.values
        )
        return FailedAuthorizeAttempt(
            id = authorizeAttempt.id,
            authorizationFlowId = authorizeAttempt.authorizationFlowId,
            errorDate = errorDate,
            errorDetailsId = error.detailsId,
            errorDescriptionId = error.descriptionId,
            errorValues = error.values,
            expirationDate = authorizeAttempt.expirationDate
        )
    }

    /**
     * Set and save the completion date of the [authorizeAttempt] and return the modified [authorizeAttempt].
     * Do nothing if the [authorizeAttempt] has already been completed or has failed.
     */
    suspend fun markAsComplete(
        authorizeAttempt: OnGoingAuthorizeAttempt
    ): CompletedAuthorizeAttempt {
        authorizeAttemptRepository.updateCompleteDate(
            id = authorizeAttempt.id,
            completeDate = LocalDateTime.now()
        )
        val userId = authorizeAttempt.userId ?: throw businessExceptionOf(
            "auth.authorize_attempt.complete.missing_user"
        )
        return CompletedAuthorizeAttempt(
            id = authorizeAttempt.id,
            authorizationFlowId = authorizeAttempt.authorizationFlowId,
            expirationDate = authorizeAttempt.expirationDate,
            attemptDate = authorizeAttempt.attemptDate,
            clientId = authorizeAttempt.clientId,
            redirectUri = authorizeAttempt.redirectUri,
            requestedScopes = authorizeAttempt.requestedScopes,
            state = authorizeAttempt.state,
            nonce = authorizeAttempt.nonce,
            codeChallenge = authorizeAttempt.codeChallenge,
            codeChallengeMethod = authorizeAttempt.codeChallengeMethod,
            userId = userId,
            consentedScopes = authorizeAttempt.consentedScopes ?: emptyList(),
            consentedAt = authorizeAttempt.consentedAt ?: throw businessExceptionOf(
                "auth.authorize_attempt.complete.missing_consented_at"
            ),
            consentedBy = authorizeAttempt.consentedBy ?: throw businessExceptionOf(
                "auth.authorize_attempt.complete.missing_consented_by"
            ),
            grantedScopes = authorizeAttempt.grantedScopes ?: emptyList(),
            grantedAt = authorizeAttempt.grantedAt ?: throw businessExceptionOf(
                "auth.authorize_attempt.complete.missing_granted_at"
            ),
            grantedBy = authorizeAttempt.grantedBy ?: throw businessExceptionOf(
                "auth.authorize_attempt.complete.missing_granted_by"
            ),
            completeDate = LocalDateTime.now()
        )
    }

    /**
     * Return the [User] associated to the [authorizeAttempt] or null if:
     * - there is not [OnGoingAuthorizeAttempt.userId] associated to the ongoing [authorizeAttempt].
     * - the [authorizeAttempt] has failed.
     *
     * Throws an unrecoverable [BusinessException] if the user id is corrupted and cannot be found in the database
     * anymore.
     */
    suspend fun getUserOrNull(authorizeAttempt: AuthorizeAttempt): User? {
        return when (authorizeAttempt) {
            is OnGoingAuthorizeAttempt -> authorizeAttempt.userId?.let { userManager.findByIdOrNull(it) }
            is CompletedAuthorizeAttempt -> {
                userManager.findByIdOrNull(authorizeAttempt.userId)
            }

            is FailedAuthorizeAttempt -> null
        }
    }

    /**
     * Return the [User] associated to the [authorizeAttempt] or throw an unrecoverable [BusinessException] if:
     * - there is not [OnGoingAuthorizeAttempt.userId] associated to the ongoing [authorizeAttempt].
     * - the [authorizeAttempt] has failed.
     * - the user id is corrupted and cannot be found in the database anymore.
     */
    suspend fun getUser(
        authorizeAttempt: AuthorizeAttempt
    ): User {
        return getUserOrNull(authorizeAttempt) ?: throw businessExceptionOf(
            detailsId = "auth.authorize_attempt.user.missing"
        )
    }

    suspend fun findByCodeOrNull(code: String): AuthorizeAttempt? {
        val authorizeAttempt = authorizeAttemptRepository.findByCode(code)
            ?.let(authorizeAttemptMapper::toAuthorizeAttempt)
        return if (authorizeAttempt?.expired == false) {
            authorizeAttempt
        } else null
    }

    companion object {
        /**
         * Name of the cryptographic key used to sign the state.
         */
        const val STATE_KEY_NAME = "state"
        const val PROVIDER_NONCE_KEY_NAME = "provider_nonce"
    }
}

sealed class VerifyEncodedStateResult

class SuccessVerifyEncodedStateResult(
    val authorizeAttempt: AuthorizeAttempt
) : VerifyEncodedStateResult()

class FailedVerifyEncodedStateResult(
    val detailsId: String,
    val descriptionId: String? = null,
    val values: Map<String, String>? = null
) : VerifyEncodedStateResult()
