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
        return userInfoRepository.findByProviderIdAndSubject(
            providerId = provider.id,
            subject = subject
        )?.let(userInfoMapper::toProviderUserInfo)
    }

    suspend fun findByUserId(userId: UUID): List<ProviderUserInfo> {
        return userInfoRepository.findByUserId(userId)
            .map(userInfoMapper::toProviderUserInfo)
    }

    suspend fun findByUserIdAndProviderIdOrNull(userId: UUID, providerId: String): ProviderUserInfo? {
        return userInfoRepository.findByProviderIdAndUserId(providerId, userId)
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
            changeDate = now
        )
        userInfoRepository.save(entity)
        return userInfoMapper.toProviderUserInfo(entity)
    }

    fun refreshUserInfo(
        existingUserInfo: ProviderUserInfo,
        newUserInfo: RawProviderClaims
    ) {
        if (existingUserInfo.userInfo == newUserInfo) {
            return
        }
        TODO()
    }
}
