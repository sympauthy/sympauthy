package com.sympauthy.api.mapper.admin

import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.CollectedUserClaim
import com.sympauthy.business.model.user.GeneratedUserClaim
import com.sympauthy.business.model.user.claim.*
import java.time.LocalDateTime
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers

class AdminUserClaimResourceMapperTest {

    private val mapper: AdminUserClaimResourceMapper =
        Mappers.getMapper(AdminUserClaimResourceMapper::class.java)

    private val collectedAt: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)
    private val verifiedAt: LocalDateTime = LocalDateTime.of(2025, 6, 1, 0, 0)

    private fun claim(id: String, generated: Boolean = false) = Claim(
        id = id,
        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        generated = generated,
        userInputted = false,
        allowedValues = null,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = null,
                readableByUser = false,
                writableByUser = false,
                readableByClient = false,
                writableByClient = false
            ),
            unconditional = UnconditionalAcl(emptyList(), emptyList())
        )
    )

    @Test
    fun `toResource - Publish a collected claim with the dates it was collected and verified on`() {
        val emailClaim = claim(OpenIdConnectClaimId.EMAIL)

        val resource = mapper.toResource(
            CollectedUserClaim(
                claim = emailClaim,
                identifier = true,
                collectedClaim = CollectedClaim(
                    userId = UUID.randomUUID(),
                    claim = emailClaim,
                    value = "jane@example.com",
                    verified = true,
                    collectionDate = collectedAt,
                    verificationDate = verifiedAt
                )
            )
        )

        assertEquals("email", resource.claimId)
        assertEquals("jane@example.com", resource.value)
        assertEquals(collectedAt, resource.collectedAt)
        assertEquals(verifiedAt, resource.verifiedAt)
        assertEquals(true, resource.identifier)
    }

    @Test
    fun `toResource - Publish a generated claim with the value computed for the user, and no date`() {
        val userId = UUID.randomUUID()

        val resource = mapper.toResource(
            GeneratedUserClaim(
                claim = claim(OpenIdConnectClaimId.SUB, generated = true),
                identifier = false,
                value = userId.toString()
            )
        )

        assertEquals("sub", resource.claimId)
        assertEquals(userId.toString(), resource.value)
        assertNull(resource.collectedAt)
        assertNull(resource.verifiedAt)
    }
}
