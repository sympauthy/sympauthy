package com.sympauthy.business.mapper

import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimAclExpression
import com.sympauthy.business.model.user.claim.ClaimDataType.EMAIL
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.PersonalClaim
import com.sympauthy.data.model.CollectedClaimEntity
import io.micronaut.serde.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class CollectedClaimUpdateMapperTest {

    private val claimValueMapper = ClaimValueMapper(ObjectMapper.getDefault())

    private val mapper = CollectedClaimUpdateMapper(claimValueMapper)

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
        value = claimValueMapper.toEntity(value),
        foldedEqualityHash = claimValueMapper.toFoldedEqualityHash(value),
        verified = true,
        collectionDate = LocalDateTime.now().minusDays(1),
        verificationDate = LocalDateTime.now().minusDays(1),
        sessionId = null
    )

    private val emailClaim = PersonalClaim(
        id = "email",
        verifiedId = "email_verified",
        dataType = EMAIL,
        group = null,
        required = false,
        allowedValues = null,
        audienceId = null,
        publishedIn = ClaimPublicationPlace.entries.toSet(),
        publishedInWhenRequested = emptySet(),
        acl = ClaimAcl(
            collectedInFlow = ClaimAclExpression("true"),
            readableByPerson = ClaimAclExpression("true"),
            writableByPerson = null,
            readableByClient = ClaimAclExpression("true"),
            writableByClient = null
        )
    )
}
