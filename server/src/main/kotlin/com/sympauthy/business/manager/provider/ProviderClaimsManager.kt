package com.sympauthy.business.manager.provider

import com.sympauthy.business.mapper.ProviderUserInfoMapper
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.data.repository.ProviderUserInfoRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.*

/**
 * Manages the lifecycle (lookup, persistence, refresh) of provider claims stored in the database.
 *
 * This manager does not handle how claims are resolved from providers — that responsibility
 * belongs to [ProviderClaimsResolver]. This separation mirrors how [com.sympauthy.business.manager.auth.AuthorizeAttemptManager]
 * manages the lifecycle of authorize attempts without handling the authorization flow logic.
 *
 * A provider identity may be **provisional**: stored while scoped to an in-progress authorization attempt (pending
 * an interactive attach + forced re-login). Provisional rows are invisible to the confirmed lookups below; they are
 * either promoted ([confirmProvisionalUserInfo]) once ownership of the account is proven, or discarded
 * ([deleteProvisionalByAttempt]).
 */
@Singleton
class ProviderClaimsManager(
    @Inject private val userInfoRepository: ProviderUserInfoRepository,
    @Inject private val userInfoMapper: ProviderUserInfoMapper
) {

    suspend fun findByProviderAndSubject(
        provider: EnabledProvider,
        subject: String
    ): ProviderUserInfo? {
        return userInfoRepository.findByProviderIdAndSubjectAndAuthorizeAttemptIdIsNull(
            providerId = provider.id,
            subject = subject
        )?.let(userInfoMapper::toProviderUserInfo)
    }

    suspend fun findByUserId(userId: UUID): List<ProviderUserInfo> {
        return userInfoRepository.findByUserIdAndAuthorizeAttemptIdIsNull(userId)
            .map(userInfoMapper::toProviderUserInfo)
    }

    suspend fun findByUserIdAndProviderIdOrNull(userId: UUID, providerId: String): ProviderUserInfo? {
        return userInfoRepository.findByProviderIdAndUserIdAndAuthorizeAttemptIdIsNull(providerId, userId)
            ?.let(userInfoMapper::toProviderUserInfo)
    }

    suspend fun deleteProviderLink(userId: UUID, providerId: String): Int {
        return userInfoRepository.deleteByProviderIdAndUserId(providerId, userId)
    }

    suspend fun saveUserInfo(
        provider: EnabledProvider,
        userId: UUID,
        rawProviderClaims: RawProviderClaims
    ): ProviderUserInfo {
        val now = LocalDateTime.now()
        val entity = userInfoMapper.toEntity(
            providerId = provider.id,
            userId = userId,
            userInfo = rawProviderClaims,
            fetchDate = now,
            changeDate = now,
            authorizeAttemptId = null
        )
        userInfoRepository.save(entity)
        return userInfoMapper.toProviderUserInfo(entity)
    }

    /**
     * Persist a **provisional** provider identity scoped to [authorizeAttemptId]. The row is stored under
     * [targetUserId] but stays invisible to the confirmed lookups until it is promoted by
     * [confirmProvisionalUserInfo].
     */
    suspend fun saveProvisionalUserInfo(
        provider: EnabledProvider,
        targetUserId: UUID,
        rawProviderClaims: RawProviderClaims,
        authorizeAttemptId: UUID
    ): ProviderUserInfo {
        val now = LocalDateTime.now()
        val entity = userInfoMapper.toEntity(
            providerId = provider.id,
            userId = targetUserId,
            userInfo = rawProviderClaims,
            fetchDate = now,
            changeDate = now,
            authorizeAttemptId = authorizeAttemptId
        )
        userInfoRepository.save(entity)
        return userInfoMapper.toProviderUserInfo(entity)
    }

    /**
     * Return the provisional provider identity scoped to [authorizeAttemptId], or null if there is none.
     */
    suspend fun findProvisionalByAttempt(authorizeAttemptId: UUID): ProviderUserInfo? {
        return userInfoRepository.findByAuthorizeAttemptId(authorizeAttemptId)
            ?.let(userInfoMapper::toProviderUserInfo)
    }

    /**
     * Promote the provisional provider identity scoped to [authorizeAttemptId] into a permanent provider identity
     * by clearing its scope. Does nothing if there is no provisional row for the attempt.
     */
    suspend fun confirmProvisionalUserInfo(authorizeAttemptId: UUID) {
        val entity = userInfoRepository.findByAuthorizeAttemptId(authorizeAttemptId) ?: return
        userInfoRepository.update(entity.copy(authorizeAttemptId = null))
    }

    /**
     * Discard the provisional provider identity scoped to [authorizeAttemptId].
     */
    suspend fun deleteProvisionalByAttempt(authorizeAttemptId: UUID): Int {
        return userInfoRepository.deleteByAuthorizeAttemptId(authorizeAttemptId)
    }

    /**
     * Update the stored provider claims with the latest data from the provider.
     * Always updates the fetch date. Only updates the change date and claims if they differ.
     */
    suspend fun refreshUserInfo(
        existingUserInfo: ProviderUserInfo,
        newUserInfo: RawProviderClaims
    ) {
        val now = LocalDateTime.now()
        val changed = existingUserInfo.userInfo != newUserInfo
        val entity = userInfoMapper.toEntity(
            providerId = existingUserInfo.providerId,
            userId = existingUserInfo.userId,
            userInfo = if (changed) newUserInfo else existingUserInfo.userInfo,
            fetchDate = now,
            changeDate = if (changed) now else existingUserInfo.changeDate,
            authorizeAttemptId = null
        )
        userInfoRepository.update(entity)
    }
}
