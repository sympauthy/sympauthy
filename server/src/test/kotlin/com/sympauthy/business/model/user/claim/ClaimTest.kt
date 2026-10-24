package com.sympauthy.business.model.user.claim

import com.sympauthy.expression.ScopeIsConsentedFunction.Companion.FUNCTION_NAME as SCOPE_IS_CONSENTED
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClaimTest {

    private fun claim(
        id: String = "test_claim",
        publishedIn: Set<ClaimPublicationPlace> = ClaimPublicationPlace.entries.toSet(),
        publishedInWhenRequested: Set<ClaimPublicationPlace> = emptySet(),
        acl: ClaimAcl = ClaimAcl.SHUT
    ) = PersonalClaim(
        id = id,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        allowedValues = null,
        publishedIn = publishedIn,
        publishedInWhenRequested = publishedInWhenRequested,
        acl = acl
    )

    private fun consentedTo(scope: String) =
        ClaimAclExpression("$SCOPE_IS_CONSENTED(\"$scope\")", consentedScopes = setOf(scope))

    private fun disabledClaim() = DisabledClaim(
        id = "test_claim",
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        kind = ClaimKind.PERSONAL,
        group = null,
        required = false,
        allowedValues = null,
        acl = ClaimAcl(
            collectedInFlow = ClaimAclExpression("true"),
            readableByPerson = ClaimAclExpression("true"),
            writableByPerson = ClaimAclExpression("true"),
            readableByClient = ClaimAclExpression("true"),
            writableByClient = ClaimAclExpression("true")
        )
    )

    @Test
    fun `origin - OPENID_CONNECT for known OpenID claim id`() {
        val claim = claim(id = OpenIdConnectClaimId.EMAIL)
        assertEquals(ClaimOrigin.OPENID_CONNECT, claim.origin)
    }

    @Test
    fun `origin - CUSTOM for unknown claim id`() {
        val claim = claim(id = "custom_field")
        assertEquals(ClaimOrigin.CUSTOM, claim.origin)
    }

    @Test
    fun `isPublishedIn - true for a place the claim names`() {
        val claim = claim(publishedIn = setOf(ClaimPublicationPlace.USERINFO))
        assertTrue(claim.isPublishedIn(ClaimPublicationPlace.USERINFO))
    }

    @Test
    fun `isPublishedIn - false for a place the claim does not name`() {
        val claim = claim(publishedIn = setOf(ClaimPublicationPlace.USERINFO))
        assertFalse(claim.isPublishedIn(ClaimPublicationPlace.ID_TOKEN))
    }

    @Test
    fun `isPublishedIn - false for every place when the claim names none`() {
        val claim = claim(publishedIn = emptySet())
        ClaimPublicationPlace.entries.forEach { assertFalse(claim.isPublishedIn(it)) }
    }

    @Test
    fun `isPublishedIn - true for a place the claim names, whatever the request named`() {
        val claim = claim(publishedIn = setOf(ClaimPublicationPlace.USERINFO))
        assertTrue(claim.isPublishedIn(ClaimPublicationPlace.USERINFO, RequestedClaims.NONE))
    }

    @Test
    fun `isPublishedIn - true for an on-request place the request named`() {
        val claim = claim(
            publishedIn = emptySet(),
            publishedInWhenRequested = setOf(ClaimPublicationPlace.ID_TOKEN)
        )
        val requested = RequestedClaims(idTokenClaimIds = setOf("test_claim"), userInfoClaimIds = emptySet())
        assertTrue(claim.isPublishedIn(ClaimPublicationPlace.ID_TOKEN, requested))
    }

    @Test
    fun `isPublishedIn - false for an on-request place no request named`() {
        val claim = claim(
            publishedIn = emptySet(),
            publishedInWhenRequested = setOf(ClaimPublicationPlace.ID_TOKEN)
        )
        assertFalse(claim.isPublishedIn(ClaimPublicationPlace.ID_TOKEN, RequestedClaims.NONE))
    }

    @Test
    fun `isPublishedIn - false for a place the request named and the claim opened to no request`() {
        val claim = claim(publishedIn = emptySet(), publishedInWhenRequested = emptySet())
        val requested = RequestedClaims(idTokenClaimIds = setOf("test_claim"), userInfoClaimIds = emptySet())
        assertFalse(claim.isPublishedIn(ClaimPublicationPlace.ID_TOKEN, requested))
    }

    @Test
    fun `isPublishedIn - false where the request named the claim in the other channel`() {
        val claim = claim(
            publishedIn = emptySet(),
            publishedInWhenRequested = setOf(ClaimPublicationPlace.ID_TOKEN)
        )
        val requested = RequestedClaims(idTokenClaimIds = emptySet(), userInfoClaimIds = setOf("test_claim"))
        assertFalse(claim.isPublishedIn(ClaimPublicationPlace.ID_TOKEN, requested))
    }

    @Test
    fun `isPublishedIn - false where the request named another claim`() {
        val claim = claim(
            publishedIn = emptySet(),
            publishedInWhenRequested = setOf(ClaimPublicationPlace.ID_TOKEN)
        )
        val requested = RequestedClaims(idTokenClaimIds = setOf("other_claim"), userInfoClaimIds = emptySet())
        assertFalse(claim.isPublishedIn(ClaimPublicationPlace.ID_TOKEN, requested))
    }

    @Test
    fun `isPublishedIn - false for every place on a claim the deployment turned off`() {
        val claim = disabledClaim()
        ClaimPublicationPlace.entries.forEach {
            assertFalse(claim.isPublishedIn(it), "Disabled claim should not be published in $it")
        }
    }

    @Test
    fun `isPublishedInWhenRequested - false for every place on a claim the deployment turned off`() {
        val claim = disabledClaim()
        ClaimPublicationPlace.entries.forEach {
            assertFalse(claim.isPublishedInWhenRequested(it), "Disabled claim should not open $it to a request")
        }
    }

    @Test
    fun `belongsToScope - true when a door opens on consent to the scope`() {
        val claim = claim(acl = ClaimAcl.SHUT.copy(readableByClient = consentedTo("profile")))
        assertTrue(claim.belongsToScope("profile"))
    }

    @Test
    fun `belongsToScope - true for each scope its doors open on`() {
        val claim = claim(
            acl = ClaimAcl.SHUT.copy(
                readableByPerson = consentedTo("profile"),
                writableByPerson = consentedTo("profile:write")
            )
        )
        assertTrue(claim.belongsToScope("profile"))
        assertTrue(claim.belongsToScope("profile:write"))
    }

    @Test
    fun `belongsToScope - false when no door opens on consent to the scope`() {
        val claim = claim(acl = ClaimAcl.SHUT.copy(readableByClient = consentedTo("profile")))
        assertFalse(claim.belongsToScope("email"))
    }

    @Test
    fun `belongsToScope - false when no door opens on consent`() {
        val claim = claim(acl = ClaimAcl.SHUT.copy(readableByClient = ClaimAclExpression("true")))
        assertFalse(claim.belongsToScope("profile"))
    }

    @Test
    fun `collectedInFlow - true where the flow's door is opened by any expression`() {
        assertTrue(claim(acl = ClaimAcl.SHUT.copy(collectedInFlow = consentedTo("profile"))).collectedInFlow)
    }

    @Test
    fun `collectedInFlow - false where the flow's door is shut`() {
        assertFalse(claim(acl = ClaimAcl.SHUT.copy(readableByPerson = consentedTo("profile"))).collectedInFlow)
    }
}
