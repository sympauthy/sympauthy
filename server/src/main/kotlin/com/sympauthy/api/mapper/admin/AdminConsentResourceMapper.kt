package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminConsentResource
import com.sympauthy.business.manager.collection.ConsentCollectionManager.ConsentWithClient
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.util.wireName
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class AdminConsentResourceMapper(
    @Inject private val clientMapper: AdminClientResourceMapper
) {

    fun toResource(consentWithClient: ConsentWithClient): AdminConsentResource {
        val (consent, promptedByClient) = consentWithClient
        return AdminConsentResource(
            consentId = consent.id,
            userId = consent.userId,
            audienceId = consent.audienceId,
            promptedByClient = clientMapper.toReferenceResource(promptedByClient),
            scopes = consent.scopes,
            consentedAt = consent.consentedAt,
            revokedAt = consent.revokedAt,
            revokedBy = toRevokedByString(consent.revokedBy),
            revokedById = consent.revokedById,
        )
    }

    fun toRevokedByString(value: ConsentRevokedBy?): String? = value?.wireName
}
