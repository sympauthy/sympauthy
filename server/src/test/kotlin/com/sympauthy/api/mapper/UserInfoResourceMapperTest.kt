package com.sympauthy.api.mapper

import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimDataType.EMAIL
import com.sympauthy.business.model.user.claim.ClaimDataType.NUMBER
import com.sympauthy.business.model.user.claim.ClaimDataType.STRING
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class UserInfoResourceMapperTest {

    @MockK
    lateinit var generatedClaimsManager: GeneratedClaimsManager

    @InjectMockKs
    lateinit var mapper: UserInfoResourceMapper

    private val userId = UUID.randomUUID()

    private val emailClaim by lazy {
        claim(OpenIdConnectClaimId.EMAIL, EMAIL, verifiedId = OpenIdConnectClaimId.EMAIL_VERIFIED)
    }

    @Test
    fun `toResource - Answer the verified companion of a verified claim as true`() = runTest {
        stubGenerated()

        val resource = mapper.toResource(userId, listOf(collected(emailClaim, "ada@example.com", true)))

        assertEquals(true, resource.emailVerified)
    }

    @Test
    fun `toResource - Answer the verified companion of an unverified claim as false`() = runTest {
        // The id token claims false for the same account, and a client reading both is owed one shape.
        stubGenerated()

        val resource = mapper.toResource(userId, listOf(collected(emailClaim, "ada@example.com", null)))

        assertEquals(false, resource.emailVerified)
    }

    @Test
    fun `toResource - Answer no verified companion where the claim it answers for is absent`() = runTest {
        stubGenerated()

        val resource = mapper.toResource(userId, emptyList())

        assertNull(resource.emailVerified)
    }

    @Test
    fun `toResource - Render an address component of every type as a string`() = runTest {
        stubGenerated()

        val resource = mapper.toResource(
            userId,
            listOf(
                collected(addressClaim(OpenIdConnectClaimId.STREET_ADDRESS, STRING), "5 Rue Ada"),
                collected(addressClaim(OpenIdConnectClaimId.LOCALITY, STRING), "Brussels"),
                collected(addressClaim(OpenIdConnectClaimId.POSTAL_CODE, NUMBER), 1000L)
            )
        )

        val address = requireNotNull(resource.address)
        assertEquals("1000", address.postalCode)
        assertEquals("5 Rue Ada\nBrussels, 1000", address.formatted)
    }

    @Test
    fun `toResource - Answer no address where no component carries a value`() = runTest {
        stubGenerated()

        val resource = mapper.toResource(
            userId,
            listOf(collected(addressClaim(OpenIdConnectClaimId.LOCALITY, STRING), null))
        )

        assertNull(resource.address)
    }

    private fun stubGenerated() {
        every { generatedClaimsManager.computeSubject(userId) } returns userId.toString()
        coEvery { generatedClaimsManager.computeUpdatedAt(userId) } returns null
    }

    private fun collected(claim: Claim, value: Any?, verified: Boolean? = null) = CollectedClaim(
        userId = userId,
        claim = claim,
        value = value,
        verified = verified,
        collectionDate = LocalDateTime.now(),
        verificationDate = null
    )

    private fun addressClaim(id: String, dataType: ClaimDataType) =
        claim(id, dataType, ClaimGroup.ADDRESS)

    private fun claim(
        id: String,
        dataType: ClaimDataType,
        group: ClaimGroup? = null,
        verifiedId: String? = null
    ) = Claim(
        id = id,
        enabled = true,
        verifiedId = verifiedId,
        dataType = dataType,
        group = group,
        required = false,
        generated = false,
        userInputted = true,
        allowedValues = null,
        audienceId = null,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = null,
                readableByUser = true,
                writableByUser = true,
                readableByClient = true,
                writableByClient = false
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = emptyList(),
                writableWithClientScopes = emptyList()
            )
        )
    )
}
