package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimPublication
import com.sympauthy.business.model.user.claim.ClaimDataType.BOOLEAN
import com.sympauthy.business.model.user.claim.ClaimDataType.NUMBER
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.data.model.CollectedClaimEntity
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.micronaut.serde.ObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class CollectedClaimMapperTest {

    @MockK
    lateinit var claimManager: ClaimManager

    private val mapper = Mappers.getMapper(CollectedClaimMapper::class.java)

    @BeforeEach
    fun wire() {
        mapper.claimManager = claimManager
        mapper.claimValueMapper = ClaimValueMapper(ObjectMapper.getDefault())
    }

    @Test
    fun `toCollectedClaim - Read a value as the type its claim declares`() {
        stubClaim(claim("age", NUMBER))

        val collected = mapper.toCollectedClaim(entity("age", "42"))

        assertEquals(42L, collected?.value)
    }

    @Test
    fun `toCollectedClaim - Map a claim holding no value`() {
        stubClaim(claim("age", NUMBER))

        val collected = mapper.toCollectedClaim(entity("age", null))

        assertNull(collected?.value)
    }

    @Test
    fun `toCollectedClaim - Return null where the configuration no longer declares the claim`() {
        // An operator disabling a claim meant the rows it collected to stop being read, not to fail.
        every { claimManager.findByIdOrNull("retired") } returns null

        assertNull(mapper.toCollectedClaim(entity("retired", "\"value\"")))
    }

    @Test
    fun `toCollectedClaim - Throw where the value does not read as the type its claim declares`() {
        stubClaim(claim("age", NUMBER))

        val exception = assertThrows<BusinessException> {
            mapper.toCollectedClaim(entity("age", "\"forty-two\""))
        }

        assertEquals("mapper.collected_claim.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
        assertFalse(exception.recoverable)
        assertEquals("age", exception.values["claim"])
        assertEquals("number", exception.values["type"])
    }

    @Test
    fun `toCollectedClaim - Throw where a boolean claim holds something that is not one`() {
        stubClaim(claim("optin", BOOLEAN))

        val exception = assertThrows<BusinessException> {
            mapper.toCollectedClaim(entity("optin", "\"maybe\""))
        }

        assertEquals("mapper.collected_claim.invalid_property", exception.detailsId)
    }

    private fun stubClaim(claim: Claim) {
        every { claimManager.findByIdOrNull(claim.id) } returns claim
    }

    private fun entity(claim: String, value: String?) = CollectedClaimEntity(
        userId = UUID.randomUUID(),
        claim = claim,
        value = value,
        foldedEqualityHash = null,
        verified = null,
        collectionDate = LocalDateTime.now(),
        verificationDate = null,
        sessionId = null
    )

    private fun claim(id: String, dataType: ClaimDataType) = Claim(
        id = id,
        enabled = true,
        verifiedId = null,
        dataType = dataType,
        group = null,
        required = false,
        generated = false,
        userInputted = true,
        allowedValues = null,
        audienceId = null,
        publishedIn = ClaimPublication.entries.toSet(),
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
