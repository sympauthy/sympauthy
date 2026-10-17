package com.sympauthy.api.resource.admin

import com.sympauthy.api.resource.allowableValuesOf
import com.sympauthy.business.model.client.AuthorizationWebhookOnFailure
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.collection.CollectionFieldType
import com.sympauthy.business.model.collection.CollectionOperator
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.business.model.mfa.MfaMethodType
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.business.model.oauth2.ScopeOrigin
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ClaimOrigin
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.util.wireName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.enums.EnumEntries
import kotlin.reflect.KClass

/**
 * Holds the sets the admin resources publish to the enums their mappers publish them from, the way
 * [AdminInteractiveFlowSessionAllowableValuesTest] does for the interactive flow session resources.
 */
class AdminAllowableValuesTest {

    @Test
    fun `Every claim data type is published where a claim's type is`() {
        assertPublished(ClaimDataType.entries, AdminClaimResource::class, "type")
        assertPublished(ClaimDataType.entries, AdminUserClaimResource::class, "type")
    }

    @Test
    fun `Every claim origin is published where a claim's origin is`() {
        assertPublished(ClaimOrigin.entries, AdminClaimResource::class, "origin")
        assertPublished(ClaimOrigin.entries, AdminUserClaimResource::class, "origin")
    }

    @Test
    fun `Every claim kind is published where a claim's kind is`() {
        assertPublished(ClaimKind.entries, AdminClaimResource::class, "kind")
        assertPublished(ClaimKind.entries, AdminUserClaimResource::class, "kind")
    }

    @Test
    fun `Every claim group is published where a claim's group is`() {
        assertPublished(ClaimGroup.entries, AdminClaimResource::class, "group")
        assertPublished(ClaimGroup.entries, AdminUserClaimResource::class, "group")
    }

    @Test
    fun `Every place a claim is published in is published on the claim`() {
        assertPublished(ClaimPublicationPlace.entries, AdminClaimResource::class, "publishedIn")
    }

    @Test
    fun `Every scope type and origin is published on the scope`() {
        assertPublished(ScopeType.entries, AdminScopeResource::class, "type")
        assertPublished(ScopeOrigin.entries, AdminScopeResource::class, "origin")
    }

    @Test
    fun `Every user status is published where a user's status is`() {
        assertPublished(UserStatus.entries, AdminUserResource::class, "status")
        assertPublished(UserStatus.entries, AdminUserDetailResource::class, "status")
    }

    @Test
    fun `Every revoker of a consent is published on the consent`() {
        assertPublished(ConsentRevokedBy.entries, AdminConsentResource::class, "revokedBy")
    }

    @Test
    fun `Every invitation status and creator is published on the invitation`() {
        assertPublished(InvitationStatus.entries, AdminInvitationResource::class, "status")
        assertPublished(InvitationCreatedBy.entries, AdminInvitationResource::class, "createdBy")
    }

    @Test
    fun `Every MFA method type is published on a user's MFA method`() {
        assertPublished(MfaMethodType.entries, AdminUserMfaMethodResource::class, "type")
    }

    @Test
    fun `Every grant type is published on the client`() {
        assertPublished(GrantType.entries, AdminClientResource::class, "allowedGrantTypes")
    }

    @Test
    fun `Every behaviour on a failed webhook call is published on the webhook`() {
        assertPublished(
            AuthorizationWebhookOnFailure.entries,
            AdminAuthorizationWebhookResource::class,
            "onFailure"
        )
    }

    @Test
    fun `Every field type and operator is published on a filter of the capabilities`() {
        assertPublished(CollectionFieldType.entries, AdminCollectionFilterResource::class, "type")
        assertPublished(CollectionOperator.entries, AdminCollectionFilterResource::class, "operators")
    }

    private fun assertPublished(entries: EnumEntries<*>, resource: KClass<*>, property: String) {
        assertEquals(
            entries.map { it.wireName }.toSet(),
            allowableValuesOf(resource, property).toSet(),
            "${resource.simpleName}.$property"
        )
    }
}
