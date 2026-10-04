package com.sympauthy.business.mapper

import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType.EMAIL
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.data.model.CollectedClaimEntity
import io.micronaut.serde.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

class CollectedClaimUpdateMapperTest {

    private val mapper = Mappers.getMapper(CollectedClaimUpdateMapper::class.java)

    @BeforeEach
    fun wire() {
        mapper.claimValueMapper = ClaimValueMapper(ObjectMapper.getDefault())
    }

    @Test
    fun `toEntity - Collect a value this server has verified nothing about`() {
        val entity = mapper.toEntity(UUID.randomUUID(), null, update("first@example.com"))

        assertEquals("\"first@example.com\"", entity.value)
        assertNull(entity.verified)
        assertNull(entity.verificationDate)
    }

    @Test
    fun `updateEntity - Drop the verification of the value it replaces`() {
        val entity = verifiedEntity("proven@example.com")

        mapper.updateEntity(entity, update("other@example.com"))

        assertEquals("\"other@example.com\"", entity.value)
        assertNull(entity.verified)
        assertNull(entity.verificationDate)
    }

    private fun update(value: Any?) = CollectedClaimUpdate(
        claim = emailClaim,
        value = Optional.ofNullable(value)
    )

    private fun verifiedEntity(value: String) = CollectedClaimEntity(
        userId = UUID.randomUUID(),
        claim = emailClaim.id,
        value = mapper.claimValueMapper.toEntity(value),
        foldedEqualityHash = mapper.claimValueMapper.toFoldedEqualityHash(value),
        verified = true,
        collectionDate = LocalDateTime.now().minusDays(1),
        verificationDate = LocalDateTime.now().minusDays(1),
        sessionId = null
    )

    private val emailClaim = Claim(
        id = "email",
        enabled = true,
        verifiedId = "email_verified",
        dataType = EMAIL,
        kind = ClaimKind.PERSONAL,
        group = null,
        required = false,
        generated = false,
        collectedInFlow = true,
        allowedValues = null,
        audienceId = null,
        publishedIn = ClaimPublicationPlace.entries.toSet(),
        publishedInWhenRequested = emptySet(),
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = null,
                readableByPerson = true,
                collectedInFlow = true,
                writableByPerson = false,
                readableByClient = true,
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
