package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminClaimResourceMapper
import com.sympauthy.api.resource.admin.AdminClaimResource
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.user.claim.*
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminClaimControllerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var claimMapper: AdminClaimResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminClaimController

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

    private fun claim(id: String, enabled: Boolean) = Claim(
        id = id,
        enabled = enabled,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        generated = false,
        userInputted = false,
        allowedValues = null,
        acl = acl
    )

    private fun mockResource(claimId: String, enabled: Boolean) = AdminClaimResource(
        id = claimId,
        type = "string",
        origin = "custom",
        enabled = enabled,
        required = false,
        identifier = false,
        allowedValues = null,
        group = null
    )

    @Test
    fun `listClaims - Order the enabled claims first, then by identifier`() {
        val enabledFirst = claim("a", enabled = true)
        val enabledSecond = claim("b", enabled = true)
        // Sorts first by identifier, and last all the same because it is disabled.
        val disabled = claim("_disabled", enabled = false)

        every { claimManager.listAllClaims() } returns listOf(disabled, enabledSecond, enabledFirst)
        listOf(enabledFirst, enabledSecond, disabled).forEach {
            every { claimMapper.toResource(it) } returns mockResource(it.id, it.enabled)
        }

        val result = controller.listClaims(null, null, null, null, null)

        assertEquals(listOf("a", "b", "_disabled"), result.claims.map { it.id })
    }
}
