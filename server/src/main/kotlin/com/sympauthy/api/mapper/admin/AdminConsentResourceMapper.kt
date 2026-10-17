package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminConsentResource
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.util.wireName
import jakarta.inject.Singleton

@Singleton
class AdminConsentResourceMapper {

    fun toResource(consent: Consent): AdminConsentResource {
        return AdminConsentResource(
            consentId = consent.id,
            userId = consent.userId,
            audienceId = consent.audienceId,
            promptedByClientId = consent.promptedByClientId,
            scopes = consent.scopes,
            consentedAt = consent.consentedAt,
            revokedAt = consent.revokedAt,
            revokedBy = toRevokedByString(consent.revokedBy),
            revokedById = consent.revokedById,
        )
    }

    fun toRevokedByString(value: ConsentRevokedBy?): String? = value?.wireName
}
