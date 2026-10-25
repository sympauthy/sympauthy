package com.sympauthy.business.model.user.claim

import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.BuiltInClientScopeId
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedOpenIdConnectClaimTest {

    private val claimAclManager = ClaimAclManager()

    private val claim = GeneratedClaim(
        id = GeneratedOpenIdConnectClaim.UPDATED_AT.id,
        verifiedId = null,
        dataType = GeneratedOpenIdConnectClaim.UPDATED_AT.dataType,
        group = null,
        publishedIn = GeneratedOpenIdConnectClaim.UPDATED_AT.publishedIn,
        acl = GeneratedOpenIdConnectClaim.UPDATED_AT.acl
    )

    private fun doors(consentedScopes: List<String> = emptyList(), grantedScopes: List<String> = emptyList()) =
        claimAclManager.getTokenDoors(
            ClaimAclContext(mockk<Client>(), consentedScopes, grantedScopes, authenticationDate = null, bound = false)
        )

    @Test
    fun `acl - Open the client read door on consent to the scope or on the claim read scope`() {
        assertTrue(doors(consentedScopes = listOf("profile")).canBeReadByClient(claim))
        assertTrue(doors(grantedScopes = listOf(BuiltInClientScopeId.USERS_CLAIMS_READ)).canBeReadByClient(claim))
        assertFalse(doors(grantedScopes = listOf(BuiltInClientScopeId.USERS_READ)).canBeReadByClient(claim))
    }

    @Test
    fun `acl - Open the person read door on consent to the scope alone`() {
        assertTrue(doors(consentedScopes = listOf("profile")).canBeReadByPerson(claim))
        assertFalse(doors(grantedScopes = listOf(BuiltInClientScopeId.USERS_CLAIMS_READ)).canBeReadByPerson(claim))
    }
}
