package com.sympauthy.business.manager.auth

import com.sympauthy.data.model.AuthorizeAttemptEntity
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.data.repository.AuthorizeAttemptRepository
import com.sympauthy.data.repository.ProviderUserInfoRepository
import com.sympauthy.data.repository.ValidationCodeRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Components in charge of cleaning expired authorize attempt, its direct dependencies and incomplete user.
 */
@Singleton
open class AuthorizeAttemptCleaner(
    @Inject private val authorizeAttemptRepository: AuthorizeAttemptRepository,
    @Inject private val validationCodeRepository: ValidationCodeRepository,
    @Inject private val authorizationCodeRepository: AuthorizationCodeRepository,
    @Inject private val providerUserInfoRepository: ProviderUserInfoRepository,
) {

    @Transactional
    open suspend fun clean(): CleanResult = coroutineScope {
        val expiredAttempts = authorizeAttemptRepository.findExpired()
        val expiredAttemptsIds = expiredAttempts.mapNotNull(AuthorizeAttemptEntity::id)

        val deferredAuthorizationCodesCount = async {
            authorizationCodeRepository.deleteByAttemptIdIn(expiredAttemptsIds)
        }
        val deferredValidationCodesCount = async {
            validationCodeRepository.deleteByAttemptIdIn(expiredAttemptsIds)
        }
        // Discard any provisional provider identity scoped to an expired attempt (abandoned interactive attach).
        val deferredProvisionalProviderCount = async {
            providerUserInfoRepository.deleteByAuthorizeAttemptIdIn(expiredAttemptsIds)
        }

        val authorizationCodesCount = deferredAuthorizationCodesCount.await()
        val validationCodesCount = deferredValidationCodesCount.await()
        val provisionalProviderCount = deferredProvisionalProviderCount.await()
        val authorizeAttemptsCount = authorizeAttemptRepository.deleteByIds(expiredAttemptsIds)

        CleanResult(
            authorizeAttemptCount = authorizeAttemptsCount,
            authorizationCodeCount = authorizationCodesCount,
            validationCodesCount = validationCodesCount,
            provisionalProviderCount = provisionalProviderCount
        )
    }

    data class CleanResult(
        val authorizeAttemptCount: Int,
        val authorizationCodeCount: Int,
        val validationCodesCount: Int,
        val provisionalProviderCount: Int,
    )
}
