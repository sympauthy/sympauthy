package com.sympauthy.business.model.user.claim

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class ClaimTest {

    private fun claim(
        id: String = "test_claim",
        publishedIn: Set<ClaimPublicationPlace> = ClaimPublicationPlace.entries.toSet(),
        publishedInWhenRequested: Set<ClaimPublicationPlace> = emptySet(),
        consentScope: String? = null,
        readableByPerson: Boolean = false,
        collectedInFlow: Boolean = false,
        readableByClient: Boolean = false,
        writableByClient: Boolean = false,
        readableWithClientScopes: List<String> = emptyList(),
        writableWithClientScopes: List<String> = emptyList(),
        writableByPerson: Boolean = false,
        writeMaxAuthenticationAge: Duration? = null
    ) = Claim(
        id = id,
        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        kind = ClaimKind.PERSONAL,
        group = null,
        required = false,
        generated = false,
        collectedInFlow = collectedInFlow,
        allowedValues = null,
        publishedIn = publishedIn,
        publishedInWhenRequested = publishedInWhenRequested,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = consentScope,
                readableByPerson = readableByPerson,
                collectedInFlow = collectedInFlow,
                writableByPerson = writableByPerson,
                readableByClient = readableByClient,
                writableByClient = writableByClient,
                writeMaxAuthenticationAge = writeMaxAuthenticationAge
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = readableWithClientScopes,
                writableWithClientScopes = writableWithClientScopes
            )
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
    fun `belongsToScope - true when consent scope matches`() {
        val claim = claim(consentScope = "profile")
        assertTrue(claim.belongsToScope("profile"))
    }

    @Test
    fun `belongsToScope - false when consent scope does not match`() {
        val claim = claim(consentScope = "profile")
        assertFalse(claim.belongsToScope("email"))
    }

    @Test
    fun `belongsToScope - false when no consent scope`() {
        val claim = claim(consentScope = null)
        assertFalse(claim.belongsToScope("profile"))
    }

    @Test
    fun `canBeReadByPerson - true when readable and no scope required`() {
        val claim = claim(readableByPerson = true, consentScope = null)
        assertTrue(claim.canBeReadByPerson(emptyList()))
    }

    @Test
    fun `canBeReadByPerson - true when readable and scope consented`() {
        val claim = claim(readableByPerson = true, consentScope = "profile")
        assertTrue(claim.canBeReadByPerson(listOf("profile")))
    }

    @Test
    fun `canBeReadByPerson - false when not readable`() {
        val claim = claim(readableByPerson = false, consentScope = null)
        assertFalse(claim.canBeReadByPerson(listOf("profile")))
    }

    @Test
    fun `canBeReadByPerson - false when readable but scope not consented`() {
        val claim = claim(readableByPerson = true, consentScope = "profile")
        assertFalse(claim.canBeReadByPerson(listOf("email")))
    }

    @Test
    fun `isCollectedInFlow - true when writable and no scope required`() {
        val claim = claim(collectedInFlow = true, consentScope = null)
        assertTrue(claim.isCollectedInFlow(emptyList()))
    }

    @Test
    fun `isCollectedInFlow - true when writable and scope consented`() {
        val claim = claim(collectedInFlow = true, consentScope = "profile")
        assertTrue(claim.isCollectedInFlow(listOf("profile")))
    }

    @Test
    fun `isCollectedInFlow - false when not writable`() {
        val claim = claim(collectedInFlow = false, consentScope = null)
        assertFalse(claim.isCollectedInFlow(listOf("profile")))
    }

    @Test
    fun `isCollectedInFlow - false when writable but scope not consented`() {
        val claim = claim(collectedInFlow = true, consentScope = "profile")
        assertFalse(claim.isCollectedInFlow(listOf("email")))
    }

    @Test
    fun `canBeReadByClient - true via consent path when readable and scope consented`() {
        val claim = claim(readableByClient = true, consentScope = "profile")
        assertTrue(claim.canBeReadByClient(listOf("profile"), emptyList()))
    }

    @Test
    fun `canBeReadByClient - true via consent path when readable and no scope required`() {
        val claim = claim(readableByClient = true, consentScope = null)
        assertTrue(claim.canBeReadByClient(emptyList(), emptyList()))
    }

    @Test
    fun `canBeReadByClient - true via unconditional path`() {
        val claim = claim(readableWithClientScopes = listOf("users:claims:read"))
        assertTrue(claim.canBeReadByClient(emptyList(), listOf("users:claims:read")))
    }

    @Test
    fun `canBeReadByClient - true when both paths match`() {
        val claim = claim(
            readableByClient = true,
            consentScope = "profile",
            readableWithClientScopes = listOf("users:claims:read")
        )
        assertTrue(claim.canBeReadByClient(listOf("profile"), listOf("users:claims:read")))
    }

    @Test
    fun `canBeReadByClient - false when neither path matches`() {
        val claim = claim(readableByClient = false, readableWithClientScopes = listOf("users:claims:read"))
        assertFalse(claim.canBeReadByClient(emptyList(), listOf("other:scope")))
    }

    @Test
    fun `canBeReadByClient - false via consent when scope not consented`() {
        val claim = claim(readableByClient = true, consentScope = "profile")
        assertFalse(claim.canBeReadByClient(listOf("email"), emptyList()))
    }

    @Test
    fun `canBeWrittenByClient - true via consent path when writable and scope consented`() {
        val claim = claim(writableByClient = true, consentScope = "profile")
        assertTrue(claim.canBeWrittenByClient(listOf("profile"), emptyList()))
    }

    @Test
    fun `canBeWrittenByClient - true via unconditional path`() {
        val claim = claim(writableWithClientScopes = listOf("users:claims:write"))
        assertTrue(claim.canBeWrittenByClient(emptyList(), listOf("users:claims:write")))
    }

    @Test
    fun `canBeWrittenByClient - false when neither path matches`() {
        val claim = claim(writableByClient = false, writableWithClientScopes = listOf("users:claims:write"))
        assertFalse(claim.canBeWrittenByClient(emptyList(), listOf("other:scope")))
    }

    @Test
    fun `canBeWrittenByClient - unconditional does not use consentedScopes`() {
        val claim = claim(writableWithClientScopes = listOf("users:claims:write"))
        assertFalse(claim.canBeWrittenByClient(listOf("users:claims:write"), emptyList()))
    }

    @Test
    fun `canBeWrittenByPerson - true when the flag is set and no scope gates it`() {
        val claim = claim(writableByPerson = true)
        assertTrue(claim.canBeWrittenByPerson(emptyList()))
    }

    @Test
    fun `canBeWrittenByPerson - true when the consent scope was consented`() {
        val claim = claim(consentScope = "profile", writableByPerson = true)
        assertTrue(claim.canBeWrittenByPerson(listOf("profile")))
    }

    @Test
    fun `canBeWrittenByPerson - false when the consent scope was not consented`() {
        val claim = claim(consentScope = "profile", writableByPerson = true)
        assertFalse(claim.canBeWrittenByPerson(listOf("email")))
    }

    @Test
    fun `canBeWrittenByPerson - false where only the flow may write the claim`() {
        val claim = claim(collectedInFlow = true, writableByPerson = false)
        assertFalse(claim.canBeWrittenByPerson(emptyList()))
    }

    @Test
    fun `isAuthenticationRecentEnough - true where the claim declares no maximum age`() {
        val claim = claim(writableByPerson = true)
        assertTrue(claim.isAuthenticationRecentEnough(NOW.minusDays(30), NOW))
    }

    @Test
    fun `isAuthenticationRecentEnough - true where the claim declares none and nothing authenticated`() {
        val claim = claim(writableByPerson = true)
        assertTrue(claim.isAuthenticationRecentEnough(null, NOW))
    }

    @Test
    fun `isAuthenticationRecentEnough - true within the age the claim declares`() {
        val claim = claim(writableByPerson = true, writeMaxAuthenticationAge = Duration.ofMinutes(5))
        assertTrue(claim.isAuthenticationRecentEnough(NOW.minusMinutes(4), NOW))
    }

    @Test
    fun `isAuthenticationRecentEnough - true at exactly the age the claim declares`() {
        val claim = claim(writableByPerson = true, writeMaxAuthenticationAge = Duration.ofMinutes(5))
        assertTrue(claim.isAuthenticationRecentEnough(NOW.minusMinutes(5), NOW))
    }

    @Test
    fun `isAuthenticationRecentEnough - false past the age the claim declares`() {
        val claim = claim(writableByPerson = true, writeMaxAuthenticationAge = Duration.ofMinutes(5))
        assertFalse(claim.isAuthenticationRecentEnough(NOW.minusMinutes(6), NOW))
    }

    @Test
    fun `isAuthenticationRecentEnough - false where the token states no authentication at all`() {
        val claim = claim(writableByPerson = true, writeMaxAuthenticationAge = Duration.ofMinutes(5))
        assertFalse(claim.isAuthenticationRecentEnough(null, NOW))
    }

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2025, 6, 1, 12, 0, 0)
    }
}
