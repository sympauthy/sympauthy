package com.sympauthy.business.model.user.claim

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClaimTest {

    private fun claim(
        id: String = "test_claim",
        consentScope: String? = null,
        readableByUser: Boolean = false,
        writableByUser: Boolean = false,
        readableByClient: Boolean = false,
        writableByClient: Boolean = false,
        readableWithClientScopes: List<String> = emptyList(),
        writableWithClientScopes: List<String> = emptyList()
    ) = Claim(
        id = id,
        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        generated = false,
        userInputted = writableByUser,
        allowedValues = null,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = consentScope,
                readableByUser = readableByUser,
                writableByUser = writableByUser,
                readableByClient = readableByClient,
                writableByClient = writableByClient
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = readableWithClientScopes,
                writableWithClientScopes = writableWithClientScopes
            )
        )
    )

    // region origin

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

    // endregion

    // region belongsToScope

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

    // endregion

    // region canBeReadByUser

    @Test
    fun `canBeReadByUser - true when readable and no scope required`() {
        val claim = claim(readableByUser = true, consentScope = null)
        assertTrue(claim.canBeReadByUser(emptyList()))
    }

    @Test
    fun `canBeReadByUser - true when readable and scope consented`() {
        val claim = claim(readableByUser = true, consentScope = "profile")
        assertTrue(claim.canBeReadByUser(listOf("profile")))
    }

    @Test
    fun `canBeReadByUser - false when not readable`() {
        val claim = claim(readableByUser = false, consentScope = null)
        assertFalse(claim.canBeReadByUser(listOf("profile")))
    }

    @Test
    fun `canBeReadByUser - false when readable but scope not consented`() {
        val claim = claim(readableByUser = true, consentScope = "profile")
        assertFalse(claim.canBeReadByUser(listOf("email")))
    }

    // endregion

    // region canBeWrittenByUser

    @Test
    fun `canBeWrittenByUser - true when writable and no scope required`() {
        val claim = claim(writableByUser = true, consentScope = null)
        assertTrue(claim.canBeWrittenByUser(emptyList()))
    }

    @Test
    fun `canBeWrittenByUser - true when writable and scope consented`() {
        val claim = claim(writableByUser = true, consentScope = "profile")
        assertTrue(claim.canBeWrittenByUser(listOf("profile")))
    }

    @Test
    fun `canBeWrittenByUser - false when not writable`() {
        val claim = claim(writableByUser = false, consentScope = null)
        assertFalse(claim.canBeWrittenByUser(listOf("profile")))
    }

    @Test
    fun `canBeWrittenByUser - false when writable but scope not consented`() {
        val claim = claim(writableByUser = true, consentScope = "profile")
        assertFalse(claim.canBeWrittenByUser(listOf("email")))
    }

    // endregion

    // region canBeReadByClient

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

    // endregion

    // region canBeWrittenByClient

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

    // endregion
}
