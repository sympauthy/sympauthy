package com.sympauthy.business.manager

import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.claim.*
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ClaimSearchManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @InjectMockKs
    lateinit var claimSearchManager: ClaimSearchManager

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

    private fun claim(id: String, enabled: Boolean = true, required: Boolean = false) = Claim(
        id = id,
        enabled = enabled,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = required,
        generated = false,
        userInputted = false,
        allowedValues = null,
        acl = acl
    )

    private val customClaim = claim("custom")
    private val disabledClaim = claim("custom_disabled", enabled = false)
    private val requiredClaim = claim("custom_required", required = true)
    private val openIdClaim = claim(OpenIdConnectClaimId.EMAIL)

    private val firstPage = PageParams(page = 0, size = 20)

    @Test
    fun `listClaims - Keep every claim when the criteria name nothing`() = runTest {
        knownClaims(customClaim, disabledClaim, requiredClaim, openIdClaim)

        val result = claimSearchManager.listClaims(null, null, null, firstPage)

        assertEquals(listOf(customClaim, requiredClaim, openIdClaim, disabledClaim), result.items)
    }

    @Test
    fun `listClaims - Keep the claims this deployment serves`() = runTest {
        knownClaims(customClaim, disabledClaim)

        val result = claimSearchManager.listClaims(true, null, null, firstPage)

        assertEquals(listOf(customClaim), result.items)
    }

    @Test
    fun `listClaims - Keep the claims this deployment turned off`() = runTest {
        knownClaims(customClaim, disabledClaim)

        val result = claimSearchManager.listClaims(false, null, null, firstPage)

        assertEquals(listOf(disabledClaim), result.items)
    }

    @Test
    fun `listClaims - Keep the claims the end-user must provide`() = runTest {
        knownClaims(customClaim, requiredClaim)

        val result = claimSearchManager.listClaims(null, true, null, firstPage)

        assertEquals(listOf(requiredClaim), result.items)
    }

    @Test
    fun `listClaims - Keep the claims of the origin the criterion names`() = runTest {
        knownClaims(customClaim, openIdClaim)

        val result = claimSearchManager.listClaims(null, null, ClaimOrigin.OPENID_CONNECT, firstPage)

        assertEquals(listOf(openIdClaim), result.items)
    }

    @Test
    fun `listClaims - Keep the claims every criterion names`() = runTest {
        knownClaims(customClaim, disabledClaim, requiredClaim, openIdClaim)

        val result = claimSearchManager.listClaims(true, true, ClaimOrigin.CUSTOM, firstPage)

        assertEquals(listOf(requiredClaim), result.items)
    }

    @Test
    fun `listClaims - Order the claims this deployment serves first, then by identifier`() = runTest {
        val disabledFirstByIdentifier = claim("a_disabled", enabled = false)
        val enabledSecond = claim("b")
        val enabledFirst = claim("a")
        knownClaims(disabledFirstByIdentifier, enabledSecond, enabledFirst)

        val result = claimSearchManager.listClaims(null, null, null, firstPage)

        assertEquals(listOf("a", "b", "a_disabled"), result.items.map { it.id })
    }

    @Test
    fun `listClaims - Return the page the parameters name, out of everything the criteria kept`() = runTest {
        knownClaims(claim("a"), claim("b"), claim("c"))

        val result = claimSearchManager.listClaims(null, null, null, PageParams(1, 2))

        assertEquals(listOf("c"), result.items.map { it.id })
        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(3, result.total)
    }

    private fun knownClaims(vararg claims: Claim) {
        every { claimManager.listAllClaims() } returns claims.toList()
    }
}
