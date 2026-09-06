package com.sympauthy.api.mapper.admin

import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.manager.user.UserSearchManager.UserWithClaims
import com.sympauthy.business.model.user.claim.*
import java.time.LocalDateTime
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AdminUserResourceMapperTest {

    private val mapper = AdminUserResourceMapper()

    private val creationDate: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)

    private val user = User(
        id = UUID.randomUUID(),
        status = UserStatus.ENABLED,
        creationDate = creationDate,
        sessionId = null
    )

    private fun claim(id: String) = Claim(
        id = id,
        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        generated = false,
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

    private fun collected(claim: Claim, value: Any?) = CollectedClaim(
        userId = user.id,
        claim = claim,
        value = value,
        verified = null,
        collectionDate = creationDate,
        verificationDate = null
    )

    private fun userWithClaims(
        collectedClaims: List<CollectedClaim> = emptyList(),
        generatedClaimValues: Map<String, Any?> = emptyMap()
    ) = UserWithClaims(user, collectedClaims, generatedClaimValues)

    @Test
    fun `toResource - Publish no claim at all when none was selected`() {
        val result = mapper.toResource(userWithClaims(), null)

        assertNull(result.claims)
        assertEquals(user.id, result.userId)
        assertEquals("enabled", result.status)
    }

    @Test
    fun `toResource - Publish the value collected for each selected claim`() {
        val email = claim("email")
        val name = claim("name")

        val result = mapper.toResource(
            userWithClaims(listOf(collected(email, "jane@example.com"), collected(name, "Jane"))),
            listOf(email)
        )

        assertEquals(mapOf("email" to "jane@example.com"), result.claims)
    }

    @Test
    fun `toResource - Publish a selected claim the user has no value for`() {
        val result = mapper.toResource(userWithClaims(), listOf(claim("email")))

        assertEquals(mapOf("email" to null), result.claims)
    }

    @Test
    fun `toResource - Publish the generated value of a selected claim`() {
        val result = mapper.toResource(
            userWithClaims(generatedClaimValues = mapOf("sub" to user.id.toString())),
            listOf(claim("sub"))
        )

        assertEquals(mapOf("sub" to user.id.toString()), result.claims)
    }
}
