package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminUserClaimResourceMapper
import com.sympauthy.api.resource.admin.AdminUserClaimResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.TEST_DEFAULT_PAGE_SIZE
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.user.UserClaimSearchManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.filter.ValueFilter
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.CollectedUserClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserClaim
import com.sympauthy.business.model.user.claim.*
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.util.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminUserClaimControllerTest {

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var userClaimSearchManager: UserClaimSearchManager

    @MockK
    lateinit var userClaimMapper: AdminUserClaimResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminUserClaimController

    private val userId: UUID = UUID.randomUUID()

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

    private fun userClaim(claimId: String): UserClaim = CollectedUserClaim(
        claim = Claim(
            id = claimId,
            enabled = true,
            verifiedId = null,
            dataType = ClaimDataType.STRING,
            group = null,
            required = false,
            generated = false,
            userInputted = false,
            allowedValues = null,
            acl = acl
        ),
        identifier = false,
        collectedClaim = null
    )

    private fun mockResource(claimId: String): AdminUserClaimResource = AdminUserClaimResource(
        claimId = claimId,
        value = null,
        type = "string",
        origin = "openid",
        required = false,
        identifier = false,
        group = null,
        collectedAt = null,
        verifiedAt = null
    )

    private val defaultPage = PageParams(DEFAULT_PAGE, TEST_DEFAULT_PAGE_SIZE)

    private fun pageOf(vararg claims: UserClaim) = Page(
        items = claims.toList(),
        page = DEFAULT_PAGE,
        size = TEST_DEFAULT_PAGE_SIZE,
        total = claims.size
    )

    private fun foundUser() {
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
    }

    private fun searchAnswers(vararg claims: UserClaim) {
        coEvery {
            userClaimSearchManager.listUserClaims(
                userId, null, null, null, null, null, ValueFilter.Unfiltered, defaultPage
            )
        } returns pageOf(*claims)
    }

    @Test
    fun `listUserClaims - Map every claim the page holds, in the order it holds them`() = runTest {
        val email = userClaim("email")
        val name = userClaim("name")
        val emailResource = mockResource("email")
        val nameResource = mockResource("name")

        foundUser()
        searchAnswers(email, name)
        every { userClaimMapper.toResource(email) } returns emailResource
        every { userClaimMapper.toResource(name) } returns nameResource

        val result = controller.listUserClaims(userId, null, null, null, null, null, null, null, null)

        assertEquals(listOf(emailResource, nameResource), result.claims)
    }

    @Test
    fun `listUserClaims - Ask the manager for the claims the parameters name, on the page they name`() = runTest {
        val custom = userClaim("custom_field")
        val resource = mockResource("custom_field")

        foundUser()
        coEvery {
            userClaimSearchManager.listUserClaims(
                userId, "custom_field", false, true, true, false,
                ValueFilter.Matching(ClaimOrigin.CUSTOM), PageParams(1, 2)
            )
        } returns pageOf(custom)
        every { userClaimMapper.toResource(custom) } returns resource

        val result = controller.listUserClaims(
            userId, 1, 2, "custom_field", false, true, true, false, "custom"
        )

        assertSame(resource, result.claims.single())
    }

    @Test
    fun `listUserClaims - Ask the manager for nothing when the origin names no origin`() = runTest {
        foundUser()
        coEvery {
            userClaimSearchManager.listUserClaims(
                userId, null, null, null, null, null, ValueFilter.MatchesNothing, defaultPage
            )
        } returns pageOf()

        val result = controller.listUserClaims(userId, null, null, null, null, null, null, null, "openid_connect")

        assertEquals(0, result.total)
        assertTrue(result.claims.isEmpty())
    }

    @Test
    fun `listUserClaims - Publish the page the manager answered, not the one that was asked for`() = runTest {
        foundUser()
        coEvery {
            userClaimSearchManager.listUserClaims(
                userId, null, null, null, null, null, ValueFilter.Unfiltered, defaultPage
            )
        } returns Page(items = emptyList(), page = 3, size = 7, total = 42)

        val result = controller.listUserClaims(userId, null, null, null, null, null, null, null, null)

        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }

    @Test
    fun `listUserClaims - Throw 404 when user not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.listUserClaims(userId, null, null, null, null, null, null, null, null)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}
