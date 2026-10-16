package com.sympauthy.business.mapper

import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.*

@Singleton
class ProviderUserInfoMapper {

    fun toProviderUserInfo(entity: ProviderUserInfoEntity): ProviderUserInfo {
        return ProviderUserInfo(
            providerId = entity.id.providerId,
            userId = entity.id.userId,
            linkDate = entity.linkDate,
            fetchDate = entity.fetchDate,
            changeDate = entity.changeDate,
            sessionId = entity.sessionId,
            userInfo = toRawProviderUserInfo(entity),
        )
    }

    fun toRawProviderUserInfo(entity: ProviderUserInfoEntity): RawProviderClaims {
        return RawProviderClaims(
            subject = entity.subject,
            name = entity.name,
            givenName = entity.givenName,
            familyName = entity.familyName,
            middleName = entity.middleName,
            nickname = entity.nickname,
            preferredUsername = entity.preferredUsername,
            profile = entity.profile,
            picture = entity.picture,
            website = entity.website,
            email = entity.email,
            emailVerified = entity.emailVerified,
            gender = entity.gender,
            birthDate = entity.birthDate,
            zoneInfo = entity.zoneInfo,
            locale = entity.locale,
            phoneNumber = entity.phoneNumber,
            phoneNumberVerified = entity.phoneNumberVerified,
            streetAddress = entity.streetAddress,
            locality = entity.locality,
            region = entity.region,
            postalCode = entity.postalCode,
            country = entity.country,
            updatedAt = entity.updatedAt,
        )
    }

    fun toEntity(
        providerId: String,
        userId: UUID,
        sessionId: UUID?,
        userInfo: RawProviderClaims,
        linkDate: LocalDateTime,
        fetchDate: LocalDateTime,
        changeDate: LocalDateTime
    ): ProviderUserInfoEntity {
        return ProviderUserInfoEntity(
            id = toEntityId(providerId, userId),
            linkDate = linkDate,
            fetchDate = fetchDate,
            changeDate = changeDate,
            subject = userInfo.subject,
            sessionId = sessionId,
            name = userInfo.name,
            givenName = userInfo.givenName,
            familyName = userInfo.familyName,
            middleName = userInfo.middleName,
            nickname = userInfo.nickname,
            preferredUsername = userInfo.preferredUsername,
            profile = userInfo.profile,
            picture = userInfo.picture,
            website = userInfo.website,
            email = userInfo.email,
            emailVerified = userInfo.emailVerified,
            gender = userInfo.gender,
            birthDate = userInfo.birthDate,
            zoneInfo = userInfo.zoneInfo,
            locale = userInfo.locale,
            phoneNumber = userInfo.phoneNumber,
            phoneNumberVerified = userInfo.phoneNumberVerified,
            streetAddress = userInfo.streetAddress,
            locality = userInfo.locality,
            region = userInfo.region,
            postalCode = userInfo.postalCode,
            country = userInfo.country,
            updatedAt = userInfo.updatedAt,
        )
    }

    fun toEntityId(
        providerId: String,
        userId: UUID
    ): ProviderUserInfoEntityId {
        return ProviderUserInfoEntityId(
            providerId = providerId,
            userId = userId,
        )
    }
}
