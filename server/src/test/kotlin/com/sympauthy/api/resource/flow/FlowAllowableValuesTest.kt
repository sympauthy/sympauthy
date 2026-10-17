package com.sympauthy.api.resource.flow

import com.sympauthy.api.resource.allowableValuesOf
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.flow.ConfirmActionType
import com.sympauthy.business.model.mfa.MfaMethodType
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.util.wireName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * Holds the sets the flow resources publish to the enums their mappers publish them from.
 */
class FlowAllowableValuesTest {

    @Test
    fun `Every claim data type is published where a claim's type is`() {
        assertPublished(ClaimDataType.entries.map { it.wireName }, CollectableClaimResource::class, "type")
        assertPublished(ClaimDataType.entries.map { it.wireName }, ClaimValueResource::class, "type")
    }

    @Test
    fun `Every claim group is published where a claim's group is`() {
        assertPublished(ClaimGroup.entries.map { it.wireName }, CollectableClaimResource::class, "group")
        assertPublished(ClaimGroup.entries.map { it.wireName }, ClaimValueResource::class, "group")
    }

    @Test
    fun `Every media is published where a validation code's media is`() {
        val media = ValidationCodeMedia.entries.map { it.wireName }
        assertPublished(media, ClaimsValidationFlowResource::class, "media")
        assertPublished(media, ClaimValidationInputResource::class, "media")
        assertPublished(media, ResendClaimsValidationInputResource::class, "media")
        assertPublished(media, ResendClaimsValidationCodeResultResource::class, "media")
        assertPublished(media, ValidationCodeResource::class, "media")
    }

    @Test
    fun `Every confirmation action is published on the confirmation step`() {
        assertPublished(ConfirmActionType.entries.map { it.wireName }, ConfirmFlowResource::class, "action")
    }

    @Test
    fun `Every MFA method type is published where an MFA method to choose is`() {
        assertPublished(MfaMethodType.entries.map { it.wireName }, MfaMethodResource::class, "method")
    }

    private fun assertPublished(values: List<String>, resource: KClass<*>, property: String) {
        assertEquals(values.toSet(), allowableValuesOf(resource, property).toSet(), "${resource.simpleName}.$property")
    }
}
