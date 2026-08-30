package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminClaimResourceMapper
import com.sympauthy.api.resource.admin.AdminClaimResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.DEFAULT_PAGE_SIZE
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.user.claim.*
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminClaimControllerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var claimMapper: AdminClaimResourceMapper

    @InjectMockKs
    lateinit var controller: AdminClaimController

    private val acl = ClaimAcl(
        consent = ConsentAcl(
            scope = null,
            readableByUser = true,
            writableByUser = true,
            readableByClient = true,
            writableByClient = false
        ),
        unconditional = UnconditionalAcl(emptyList(), emptyList())
    )

    private fun claim(id: String, enabled: Boolean = true) = Claim(
        id = id,
        enabled = enabled,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        generated = false,
        userInputted = true,
        allowedValues = null,
        acl = acl
    )

    @Test
    fun `listClaims - Return paginated list with defaults`() {
        val email = claim("email")
        val name = claim("name")
        val emailResource = mockk<AdminClaimResource>()
        val nameResource = mockk<AdminClaimResource>()

        every { claimManager.listAllClaims() } returns listOf(email, name)
        every { claimMapper.toResource(email) } returns emailResource
        every { claimMapper.toResource(name) } returns nameResource

        val result = controller.listClaims(null, null, null, null, null)

        assertEquals(DEFAULT_PAGE, result.page)
        assertEquals(DEFAULT_PAGE_SIZE, result.size)
        assertEquals(2, result.total)
        assertSame(emailResource, result.claims[0])
        assertSame(nameResource, result.claims[1])
    }

    @Test
    fun `listClaims - Order enabled claims first, then by identifier`() {
        val disabledAlpha = claim("alpha", enabled = false)
        val enabledZulu = claim("zulu")
        val enabledBravo = claim("bravo")
        val disabledAlphaResource = mockk<AdminClaimResource>()
        val enabledZuluResource = mockk<AdminClaimResource>()
        val enabledBravoResource = mockk<AdminClaimResource>()

        every { claimManager.listAllClaims() } returns listOf(disabledAlpha, enabledZulu, enabledBravo)
        every { claimMapper.toResource(disabledAlpha) } returns disabledAlphaResource
        every { claimMapper.toResource(enabledZulu) } returns enabledZuluResource
        every { claimMapper.toResource(enabledBravo) } returns enabledBravoResource

        val result = controller.listClaims(null, null, null, null, null)

        assertSame(enabledBravoResource, result.claims[0])
        assertSame(enabledZuluResource, result.claims[1])
        assertSame(disabledAlphaResource, result.claims[2])
    }

    @Test
    fun `listClaims - Apply page and size`() {
        val claims = listOf("e", "d", "c", "b", "a").map { claim(it) }
        val thirdResource = mockk<AdminClaimResource>()
        val fourthResource = mockk<AdminClaimResource>()

        every { claimManager.listAllClaims() } returns claims
        // Ordered by identifier, the second page of two holds c and d.
        every { claimMapper.toResource(claim("c")) } returns thirdResource
        every { claimMapper.toResource(claim("d")) } returns fourthResource

        val result = controller.listClaims(1, 2, null, null, null)

        assertEquals(1, result.page)
        assertEquals(5, result.total)
        assertSame(thirdResource, result.claims[0])
        assertSame(fourthResource, result.claims[1])
    }

    @Test
    fun `listClaims - Filter by enabled status`() {
        val enabled = claim("email")
        val disabled = claim("nickname", enabled = false)
        val enabledResource = mockk<AdminClaimResource>()

        every { claimManager.listAllClaims() } returns listOf(enabled, disabled)
        every { claimMapper.toResource(enabled) } returns enabledResource

        val result = controller.listClaims(null, null, true, null, null)

        assertEquals(1, result.total)
        assertSame(enabledResource, result.claims.single())
    }

    @Test
    fun `listClaims - Return empty page when page exceeds total`() {
        every { claimManager.listAllClaims() } returns listOf(claim("email"))

        val result = controller.listClaims(5, 20, null, null, null)

        assertEquals(5, result.page)
        assertEquals(1, result.total)
        assertTrue(result.claims.isEmpty())
    }
}
