package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.api.util.criteriaOf
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
class ClaimCollectionManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var audienceManager: AudienceManager

    @InjectMockKs
    lateinit var claimCollectionManager: ClaimCollectionManager

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
        publishedIn = ClaimPublication.entries.toSet(),
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

        val result = claimCollectionManager.listClaims(criteriaOf(), firstPage)

        assertEquals(listOf(customClaim, requiredClaim, openIdClaim, disabledClaim), result.items)
    }

    @Test
    fun `listClaims - Keep the claims this deployment serves`() = runTest {
        knownClaims(customClaim, disabledClaim)

        val result = claimCollectionManager.listClaims(criteriaOf("enabled" to "true"), firstPage)

        assertEquals(listOf(customClaim), result.items)
    }

    @Test
    fun `listClaims - Keep the claims this deployment turned off`() = runTest {
        knownClaims(customClaim, disabledClaim)

        val result = claimCollectionManager.listClaims(criteriaOf("enabled" to "false"), firstPage)

        assertEquals(listOf(disabledClaim), result.items)
    }

    @Test
    fun `listClaims - Keep the claims the end-user must provide`() = runTest {
        knownClaims(customClaim, requiredClaim)

        val result = claimCollectionManager.listClaims(criteriaOf("required" to "true"), firstPage)

        assertEquals(listOf(requiredClaim), result.items)
    }

    @Test
    fun `listClaims - Keep the claims of the origin the criterion names`() = runTest {
        knownClaims(customClaim, openIdClaim)

        val result = claimCollectionManager.listClaims(criteriaOf("origin" to "openid"), firstPage)

        assertEquals(listOf(openIdClaim), result.items)
    }

    @Test
    fun `listClaims - Keep the claims every criterion names`() = runTest {
        knownClaims(customClaim, disabledClaim, requiredClaim, openIdClaim)

        val criteria = criteriaOf("enabled" to "true", "required" to "true", "origin" to "custom")
        val result = claimCollectionManager.listClaims(criteria, firstPage)

        assertEquals(listOf(requiredClaim), result.items)
    }

    @Test
    fun `listClaims - Keep the claims an identifier is a fragment of`() = runTest {
        knownClaims(customClaim, requiredClaim, openIdClaim)

        val result = claimCollectionManager.listClaims(criteriaOf("id.contains" to "REQUIRED"), firstPage)

        assertEquals(listOf(requiredClaim), result.items)
    }

    @Test
    fun `listClaims - Keep the claims a free text search matches`() = runTest {
        knownClaims(customClaim, requiredClaim, openIdClaim)

        val result = claimCollectionManager.listClaims(criteriaOf(query = "mail"), firstPage)

        assertEquals(listOf(openIdClaim), result.items)
    }

    @Test
    fun `listClaims - Order the claims this deployment serves first, then by identifier`() = runTest {
        val disabledFirstByIdentifier = claim("a_disabled", enabled = false)
        val enabledSecond = claim("b")
        val enabledFirst = claim("a")
        knownClaims(disabledFirstByIdentifier, enabledSecond, enabledFirst)

        val result = claimCollectionManager.listClaims(criteriaOf(), firstPage)

        assertEquals(listOf("a", "b", "a_disabled"), result.items.map { it.id })
    }

    @Test
    fun `listClaims - Order by the keys the caller named, then by identifier`() = runTest {
        knownClaims(claim("b", required = true), claim("a"), claim("c", required = true))

        val result = claimCollectionManager.listClaims(criteriaOf(sort = "-required"), firstPage)

        assertEquals(listOf("b", "c", "a"), result.items.map { it.id })
    }

    @Test
    fun `listClaims - Return the page the parameters name, out of everything the criteria kept`() = runTest {
        knownClaims(claim("a"), claim("b"), claim("c"))

        val result = claimCollectionManager.listClaims(criteriaOf(), PageParams(1, 2))

        assertEquals(listOf("c"), result.items.map { it.id })
        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(3, result.total)
    }

    private fun knownClaims(vararg claims: Claim) {
        every { claimManager.listAllClaims() } returns claims.toList()
        every { audienceManager.listAudiences() } returns emptyList()
    }

    private suspend fun criteriaOf(
        vararg filters: Pair<String, String>,
        sort: String? = null,
        query: String? = null
    ) = claimCollectionManager.capabilities().criteriaOf(*filters, sort = sort, query = query)
}
