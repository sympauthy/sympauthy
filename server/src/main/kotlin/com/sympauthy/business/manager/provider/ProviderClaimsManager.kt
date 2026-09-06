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
 * belongs to [ProviderClaimsResolver]. This separation mirrors how [com.sympauthy.business.manager.flow.InteractiveFlowSessionManager]
 * manages the lifecycle of interactive flow sessions without handling the authorization flow logic.
 */
@Singleton
class ProviderClaimsManager(
    @Inject private val userInfoRepository: ProviderUserInfoRepository,
    @Inject private val userInfoMapper: ProviderUserInfoMapper
) {

    /**
     * Find the committed link of [provider] to the account it knows as [subject], or null when there is none.
     *
     * This is how a returning end-user is recognised, so a link a session is still signing up is invisible
     * here: two sign-ups may hold the same provider subject at once, and the collision is settled when the
     * first of them promotes. See [com.sympauthy.data.model.SessionScoped].
     */
    suspend fun findByProviderAndSubject(
        provider: EnabledProvider,
        subject: String
    ): ProviderUserInfo? {
        return userInfoRepository.findByProviderIdAndSubjectAndSessionIdIsNull(
            providerId = provider.id,
            subject = subject
        )?.let(userInfoMapper::toProviderUserInfo)
    }

    suspend fun findByUserId(userId: UUID): List<ProviderUserInfo> {
        return userInfoRepository.findByUserId(userId)
            .map(userInfoMapper::toProviderUserInfo)
    }

    suspend fun listByUserIds(userIds: List<UUID>): List<ProviderUserInfo> {
        return userInfoRepository.findByUserIdInList(userIds)
            .map(userInfoMapper::toProviderUserInfo)
    }

    suspend fun findByUserIdAndProviderIdOrNull(userId: UUID, providerId: String): ProviderUserInfo? {
        return userInfoRepository.findByProviderIdAndUserId(providerId, userId)
            ?.let(userInfoMapper::toProviderUserInfo)
    }

    suspend fun deleteProviderLink(userId: UUID, providerId: String): Int {
        return userInfoRepository.deleteByProviderIdAndUserId(providerId, userId)
    }

    /**
     * Link [provider] to the user identified by [userId] under the claims it just returned.
     *
     * [sessionId] is the session the **user** is provisional for, not the session the request is serving:
     * a link made to an already-committed account is permanent even when an interactive flow is what made
     * it. See [com.sympauthy.data.model.SessionScoped].
     */
    suspend fun saveUserInfo(
        provider: EnabledProvider,
        userId: UUID,
        sessionId: UUID?,
        rawProviderClaims: RawProviderClaims
    ): ProviderUserInfo {
        val now = LocalDateTime.now()
        val entity = userInfoMapper.toEntity(
            providerId = provider.id,
            userId = userId,
            sessionId = sessionId,
            userInfo = rawProviderClaims,
            linkDate = now,
            fetchDate = now,
            changeDate = now
        )
        userInfoRepository.save(entity)
        return userInfoMapper.toProviderUserInfo(entity)
    }

    /**
     * Update the stored provider claims with the latest data from the provider.
     * Always updates the fetch date. Only updates the change date and claims if they differ.
     * Never moves the link date: refreshing is a sign-in with a provider already linked, not a new link.
     * Nor its session id: this writes the whole row, and dropping that would promote a provisional link
     * on a sign-in that has not completed yet.
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
            sessionId = existingUserInfo.sessionId,
            userInfo = if (changed) newUserInfo else existingUserInfo.userInfo,
            linkDate = existingUserInfo.linkDate,
            fetchDate = now,
            changeDate = if (changed) now else existingUserInfo.changeDate
        )
        userInfoRepository.update(entity)
    }
}
