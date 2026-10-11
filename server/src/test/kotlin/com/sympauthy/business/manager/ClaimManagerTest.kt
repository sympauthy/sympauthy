package com.sympauthy.business.manager

import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.DisabledClaim
import com.sympauthy.business.model.user.claim.PersonalClaim
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.EnabledClaimsConfig
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ClaimManagerTest {

    private val acl = ClaimAcl(
        consent = ConsentAcl(
            scope = null,
            readableByPerson = true,
            collectedInFlow = true,
            writableByPerson = true,
            readableByClient = true,
            writableByClient = true,
            writeMaxAuthenticationAge = null
        ),
        unconditional = UnconditionalAcl(emptyList(), emptyList())
    )

    private val servedClaim = PersonalClaim(
        id = "nickname",
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = true,
        allowedValues = null,
        publishedIn = emptySet(),
        publishedInWhenRequested = emptySet(),
        acl = acl
    )

    private val turnedOffClaim = DisabledClaim(
        id = "shoe_size",
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        kind = ClaimKind.PERSONAL,
        group = null,
        required = true,
        allowedValues = null,
        acl = acl
    )

    private val manager = ClaimManager(
        uncheckedClaimsConfig = EnabledClaimsConfig(listOf(servedClaim, turnedOffClaim)),
        uncheckedAuthConfig = mockk<AuthConfig>()
    )

    @Test
    fun `findByIdOrNull - Answer a claim this server serves`() {
        assertSame(servedClaim, manager.findByIdOrNull("nickname"))
    }

    @Test
    fun `findByIdOrNull - Answer nothing for a claim the deployment turned off`() {
        assertNull(manager.findByIdOrNull("shoe_size"))
    }

    @Test
    fun `listEnabledClaims - Leave out a claim the deployment turned off`() {
        assertEquals(listOf(servedClaim), manager.listEnabledClaims())
    }

    @Test
    fun `listRequiredClaims - Hold nobody to a claim the deployment turned off`() {
        assertEquals(listOf(servedClaim), manager.listRequiredClaims())
    }

    @Test
    fun `listAllClaims - List a claim the deployment turned off`() {
        assertEquals(listOf(servedClaim, turnedOffClaim), manager.listAllClaims())
    }
}
