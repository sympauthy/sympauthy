package com.sympauthy.business.manager.provider

import com.jayway.jsonpath.JsonPath
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.mapper.ProviderUserInfoMapper
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.ProviderCredentials
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.client.UserInfoEndpointClient
import com.sympauthy.data.repository.ProviderUserInfoRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.*

@Singleton
class ProviderClaimsManager(
    @Inject private val userInfoRepository: ProviderUserInfoRepository,
    @Inject private val userInfoEndpointClient: UserInfoEndpointClient,
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

    suspend fun fetchUserInfo(
        provider: EnabledProvider,
        credentials: ProviderCredentials
    ): RawProviderClaims {
        val userInfo = provider.userInfo
            ?: throw businessExceptionOf("provider.user_info.not_configured", "providerId" to provider.id)
        val rawUserInfoMap = userInfoEndpointClient.fetchUserInfo(
            userInfoConfig = userInfo,
            credentials = credentials
        )

        val document = JsonPath.parse(rawUserInfoMap)
        return userInfo.paths.entries
            .fold(RawProviderClaimsBuilder()) { builder, (pathKey, path) ->
                builder.withUserInfo(document, pathKey, path)
            }
            .build(provider)
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
