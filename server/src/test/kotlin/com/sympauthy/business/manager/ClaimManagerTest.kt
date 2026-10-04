package com.sympauthy.business.manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.EnabledClaimsConfig
import io.micronaut.serde.ObjectMapper
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClaimManagerTest {

    private val claimManager = ClaimManager(
        uncheckedClaimsConfig = EnabledClaimsConfig(
            listOf(claimOf("loyalty_tier"), claimOf("shoe_size"), claimOf("turned_off", enabled = false))
        ),
        uncheckedAuthConfig = mockk<AuthConfig>(),
        objectMapper = ObjectMapper.getDefault()
    )

    @Test
    fun `parseRequestedClaims - Nothing requested when the parameter is absent`() {
        assertEquals(RequestedClaims.NONE, claimManager.parseRequestedClaims(null))
    }

    @Test
    fun `parseRequestedClaims - Nothing requested when the parameter is blank`() {
        assertEquals(RequestedClaims.NONE, claimManager.parseRequestedClaims("  "))
    }

    @Test
    fun `parseRequestedClaims - The names of each member, per channel`() {
        val requested = claimManager.parseRequestedClaims(
            """{"id_token":{"loyalty_tier":null},"userinfo":{"shoe_size":null}}"""
        )
        assertEquals(setOf("loyalty_tier"), requested.idTokenClaimIds)
        assertEquals(setOf("shoe_size"), requested.userInfoClaimIds)
    }

    @Test
    fun `parseRequestedClaims - One claim named in both channels`() {
        val requested = claimManager.parseRequestedClaims(
            """{"id_token":{"loyalty_tier":null},"userinfo":{"loyalty_tier":null}}"""
        )
        assertEquals(setOf("loyalty_tier"), requested.idTokenClaimIds)
        assertEquals(setOf("loyalty_tier"), requested.userInfoClaimIds)
    }

    @Test
    fun `parseRequestedClaims - Nothing requested in a member the parameter leaves out`() {
        val requested = claimManager.parseRequestedClaims("""{"id_token":{"loyalty_tier":null}}""")
        assertEquals(emptySet<String>(), requested.userInfoClaimIds)
    }

    @Test
    fun `parseRequestedClaims - Nothing requested in a member written as null`() {
        val requested = claimManager.parseRequestedClaims("""{"id_token":null,"userinfo":null}""")
        assertEquals(RequestedClaims.NONE, requested)
    }

    @Test
    fun `parseRequestedClaims - Nothing requested by an empty member`() {
        val requested = claimManager.parseRequestedClaims("""{"id_token":{},"userinfo":{}}""")
        assertEquals(RequestedClaims.NONE, requested)
    }

    @Test
    fun `parseRequestedClaims - A name matching no configured claim is dropped`() {
        val requested = claimManager.parseRequestedClaims(
            """{"id_token":{"loyalty_tier":null,"not_a_claim":null}}"""
        )
        assertEquals(setOf("loyalty_tier"), requested.idTokenClaimIds)
    }

    @Test
    fun `parseRequestedClaims - A name matching a disabled claim is dropped`() {
        val requested = claimManager.parseRequestedClaims("""{"id_token":{"turned_off":null}}""")
        assertEquals(emptySet<String>(), requested.idTokenClaimIds)
    }

    @Test
    fun `parseRequestedClaims - The essential, value and values members are ignored`() {
        val requested = claimManager.parseRequestedClaims(
            """{"id_token":{"loyalty_tier":{"essential":true,"value":"gold","values":["gold","silver"]}}}"""
        )
        assertEquals(setOf("loyalty_tier"), requested.idTokenClaimIds)
    }

    @Test
    fun `parseRequestedClaims - A member the specification does not define is ignored`() {
        val requested = claimManager.parseRequestedClaims(
            """{"id_token":{"loyalty_tier":null},"access_token":{"shoe_size":null}}"""
        )
        assertEquals(setOf("loyalty_tier"), requested.idTokenClaimIds)
        assertEquals(emptySet<String>(), requested.userInfoClaimIds)
    }

    @Test
    fun `parseRequestedClaims - Refuses a value that is not JSON`() {
        assertInvalid { claimManager.parseRequestedClaims("not-json") }
    }

    @Test
    fun `parseRequestedClaims - Refuses JSON that is not an object`() {
        assertInvalid { claimManager.parseRequestedClaims("""["loyalty_tier"]""") }
    }

    @Test
    fun `parseRequestedClaims - Refuses a member that is neither null nor an object`() {
        assertInvalid { claimManager.parseRequestedClaims("""{"id_token":"loyalty_tier"}""") }
    }

    private fun assertInvalid(block: () -> Unit) {
        val exception = assertThrows<BusinessException>(block)
        assertEquals("claim.parse_requested.invalid", exception.detailsId)
    }

    private companion object {
        fun claimOf(id: String, enabled: Boolean = true) = Claim(
            id = id,
            enabled = enabled,
            verifiedId = null,
            dataType = ClaimDataType.STRING,
            kind = ClaimKind.PERSONAL,
            group = null,
            required = false,
            generated = false,
            collectedInFlow = false,
            allowedValues = null,
            publishedIn = emptySet(),
            publishedInWhenRequested = emptySet(),
            acl = ClaimAcl(
                consent = ConsentAcl(
                    scope = null,
                    readableByPerson = false,
                    collectedInFlow = false,
                    writableByPerson = false,
                    readableByClient = false,
                    writableByClient = false,
                    writeMaxAuthenticationAge = null
                ),
                unconditional = UnconditionalAcl(
                    readableWithClientScopes = emptyList(),
                    writableWithClientScopes = emptyList()
                )
            )
        )
    }
}
