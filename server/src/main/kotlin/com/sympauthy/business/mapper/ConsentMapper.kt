package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.data.model.ConsentEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [ConsentEntity] to the [Consent] business model.
 *
 * If the row holds no identifier, or an actor [ConsentRevokedBy] does not name, an internal
 * [BusinessException] "mapper.consent.invalid_property" is thrown: a row this server wrote and cannot
 * read back is its own failure rather than the caller's.
 */
@Singleton
class ConsentMapper {

    fun toConsent(entity: ConsentEntity): Consent {
        return Consent(
            id = entity.id ?: throw invalidProperty("id"),
            userId = entity.userId,
            audienceId = entity.audienceId,
            promptedByClientId = entity.promptedByClientId,
            scopes = toScopeList(entity.scopes),
            consentedAt = entity.consentedAt,
            revokedAt = entity.revokedAt,
            revokedBy = toConsentRevokedBy(entity.revokedBy),
            revokedById = entity.revokedById,
        )
    }

    fun toScopeList(scopes: Array<String>): List<String> = scopes.toList()

    /**
     * The actor [value] names, or null where the consent is still active.
     *
     * A value [ConsentRevokedBy] does not name throws an internal [BusinessException]
     * "mapper.consent.invalid_property": a row this server wrote and cannot read back is its own
     * failure rather than the caller's.
     */
    fun toConsentRevokedBy(value: String?): ConsentRevokedBy? = value?.let {
        try {
            ConsentRevokedBy.valueOf(it)
        } catch (_: IllegalArgumentException) {
            throw invalidProperty("revokedBy")
        }
    }

    private fun invalidProperty(invalidProperty: String): BusinessException {
        return internalBusinessExceptionOf(
            detailsId = "mapper.consent.invalid_property",
            values = arrayOf("property" to invalidProperty)
        )
    }
}
