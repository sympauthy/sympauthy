package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.model.filter.ValueFilter
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.manager.user.UserClaimSearchManager.CollectedUserClaim
import com.sympauthy.business.manager.user.UserClaimSearchManager.GeneratedUserClaim
import com.sympauthy.business.model.user.claim.*
import com.sympauthy.config.model.EnabledAuthConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class UserClaimSearchManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var generatedClaimsManager: GeneratedClaimsManager

    private val userId: UUID = UUID.randomUUID()

    private val acl = ClaimAcl(
        consent = ConsentAcl(
            scope = null,
            readableByUser = false,
            writableByUser = false,
            readableByClient = false,
            writableByClient = false
        ),
        unconditional = UnconditionalAcl(emptyList(), emptyList())
    )

    private fun claim(
        id: String,
        verifiedId: String? = null,
        required: Boolean = false,
        generated: Boolean = false
    ) = Claim(
        id = id,
        enabled = true,
        verifiedId = verifiedId,
        dataType = ClaimDataType.STRING,
        group = null,
        required = required,
        generated = generated,
        userInputted = false,
        allowedValues = null,
        acl = acl
    )

    private val emailClaim = claim(OpenIdConnectClaimId.EMAIL, verifiedId = "email_verified", required = true)
    private val emailVerifiedClaim = claim("email_verified")
    private val nameClaim = claim(OpenIdConnectClaimId.NAME)
    private val customClaim = claim("custom_field")
    private val subClaim = claim(OpenIdConnectClaimId.SUB, generated = true)

    private fun collected(claim: Claim, value: Any? = "value", verificationDate: LocalDateTime? = null) =
        CollectedClaim(
            userId = userId,
            claim = claim,
            value = value,
            verified = verificationDate != null,
            collectionDate = LocalDateTime.of(2025, 1, 1, 0, 0),
            verificationDate = verificationDate
        )

    private fun manager() = UserClaimSearchManager(
        claimManager = claimManager,
        collectedClaimManager = collectedClaimManager,
        generatedClaimsManager = generatedClaimsManager,
        uncheckedAuthConfig = EnabledAuthConfig(
            issuer = "test",
            token = mockk(),
            authorizationCode = mockk(),
            identifierClaims = listOf(OpenIdConnectClaimId.EMAIL),
            userMergingEnabled = false,
            byPassword = mockk()
        )
    )

    private fun enabledClaims(vararg claims: Claim, collected: List<CollectedClaim> = emptyList()) {
        every { claimManager.listEnabledClaims() } returns claims.toList()
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns collected
        coEvery { generatedClaimsManager.computeValues(userId) } returns emptyMap()
    }

    private suspend fun listUserClaims(
        claimId: String? = null,
        identifier: Boolean? = null,
        required: Boolean? = null,
        collected: Boolean? = null,
        verified: Boolean? = null,
        origin: ValueFilter<ClaimOrigin> = ValueFilter.Unfiltered,
        pageParams: PageParams = PageParams(page = 0, size = 20)
    ) = manager().listUserClaims(userId, claimId, identifier, required, collected, verified, origin, pageParams)

    @Test
    fun `listUserClaims - List every enabled claim when the criteria name nothing`() = runTest {
        enabledClaims(emailClaim, nameClaim)

        val result = listUserClaims()

        assertEquals(listOf(emailClaim, nameClaim), result.items.map { it.claim })
    }

    @Test
    fun `listUserClaims - Leave out the claim carrying whether another was verified`() = runTest {
        enabledClaims(emailClaim, emailVerifiedClaim, nameClaim)

        val result = listUserClaims()

        assertEquals(listOf(emailClaim, nameClaim), result.items.map { it.claim })
    }

    @Test
    fun `listUserClaims - Keep the claim the identifier names`() = runTest {
        enabledClaims(emailClaim, nameClaim)

        val result = listUserClaims(claimId = nameClaim.id)

        assertEquals(listOf(nameClaim), result.items.map { it.claim })
    }

    @Test
    fun `listUserClaims - Keep the claims a user is identified by`() = runTest {
        enabledClaims(emailClaim, nameClaim)

        val result = listUserClaims(identifier = true)

        assertEquals(listOf(emailClaim), result.items.map { it.claim })
        assertTrue(result.items.single().identifier)
    }

    @Test
    fun `listUserClaims - Keep the claims the end-user must provide`() = runTest {
        enabledClaims(emailClaim, nameClaim)

        val result = listUserClaims(required = true)

        assertEquals(listOf(emailClaim), result.items.map { it.claim })
    }

    @Test
    fun `listUserClaims - Keep the claims of the origin the criterion names`() = runTest {
        enabledClaims(nameClaim, customClaim)

        val result = listUserClaims(origin = ValueFilter.Matching(ClaimOrigin.CUSTOM))

        assertEquals(listOf(customClaim), result.items.map { it.claim })
    }

    @Test
    fun `listUserClaims - Keep no claim when the origin criterion matches nothing`() = runTest {
        enabledClaims(nameClaim, customClaim)

        val result = listUserClaims(origin = ValueFilter.MatchesNothing)

        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `listUserClaims - Keep the claims a value was collected for`() = runTest {
        enabledClaims(nameClaim, customClaim, collected = listOf(collected(nameClaim)))

        val result = listUserClaims(collected = true)

        assertEquals(listOf(nameClaim), result.items.map { it.claim })
    }

    @Test
    fun `listUserClaims - Keep the claims nothing was collected for`() = runTest {
        enabledClaims(nameClaim, customClaim, collected = listOf(collected(nameClaim)))

        val result = listUserClaims(collected = false)

        assertEquals(listOf(customClaim), result.items.map { it.claim })
    }

    @Test
    fun `listUserClaims - Keep the claims this server verified`() = runTest {
        val verifiedAt = LocalDateTime.of(2025, 6, 1, 0, 0)
        enabledClaims(
            nameClaim, customClaim,
            collected = listOf(collected(nameClaim, verificationDate = verifiedAt), collected(customClaim))
        )

        val result = listUserClaims(verified = true)

        assertEquals(listOf(nameClaim), result.items.map { it.claim })
    }

    @Test
    fun `listUserClaims - Answer a collected claim with what was collected`() = runTest {
        val collectedName = collected(nameClaim)
        enabledClaims(nameClaim, customClaim, collected = listOf(collectedName))

        val result = listUserClaims()

        assertEquals(collectedName, (result.items.first { it.claim == nameClaim } as CollectedUserClaim).collectedClaim)
        assertNull((result.items.first { it.claim == customClaim } as CollectedUserClaim).collectedClaim)
    }

    @Test
    fun `listUserClaims - Answer a generated claim with the value computed for the user`() = runTest {
        every { claimManager.listEnabledClaims() } returns listOf(subClaim)
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns emptyList()
        coEvery { generatedClaimsManager.computeValues(userId) } returns mapOf(subClaim.id to "$userId")

        val result = listUserClaims()

        assertEquals("$userId", (result.items.single() as GeneratedUserClaim).value)
    }

    @Test
    fun `listUserClaims - Order by claim identifier before slicing`() = runTest {
        // Handed name-first, the first page of one still holds the claim the order puts first.
        enabledClaims(nameClaim, customClaim)

        val result = listUserClaims(pageParams = PageParams(page = 0, size = 1))

        assertEquals(listOf(customClaim), result.items.map { it.claim })
    }

    @Test
    fun `listUserClaims - Return the page the parameters name, out of everything the criteria kept`() = runTest {
        enabledClaims(nameClaim, customClaim, subClaim)

        val result = listUserClaims(pageParams = PageParams(page = 1, size = 2))

        assertEquals(listOf(subClaim), result.items.map { it.claim })
        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(3, result.total)
    }
}
