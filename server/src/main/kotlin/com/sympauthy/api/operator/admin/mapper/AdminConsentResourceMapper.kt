package com.sympauthy.api.operator.admin.mapper

import com.sympauthy.api.operator.admin.resource.AdminConsentResource
import com.sympauthy.business.collection.manager.ConsentCollectionManager.ConsentWithClient
import com.sympauthy.business.consent.model.ConsentRevokedBy
import com.sympauthy.platform.util.wireName
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
