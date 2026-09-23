package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserClaimResourceMapper
import com.sympauthy.api.resource.admin.AdminUserClaimResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.TEST_DEFAULT_PAGE_SIZE
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.api.util.collectionRequest
import com.sympauthy.business.manager.collection.UserClaimCollectionManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionField
import com.sympauthy.business.model.collection.CollectionFieldType
import com.sympauthy.business.model.collection.CollectionOperator
import com.sympauthy.business.model.collection.enumFieldValues
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.manager.collection.UserClaimCollectionManager.CollectedUserClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.manager.collection.UserClaimCollectionManager.UserClaim
import com.sympauthy.business.model.user.claim.*
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
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
    lateinit var userClaimCollectionManager: UserClaimCollectionManager

    @MockK
    lateinit var userClaimMapper: AdminUserClaimResourceMapper

    @MockK
    lateinit var capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminUserClaimController

    private val userId: UUID = UUID.randomUUID()

    private val originField = CollectionField(
        name = "origin",
        type = CollectionFieldType.ENUM,
        key = "fields.claim_origin",
        operators = setOf(CollectionOperator.EQ),
        values = enumFieldValues<ClaimOrigin>("fields.claim_origin")
    )

    private val capabilities = CollectionCapabilities(listOf(originField), emptyList())

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
            publishedIn = ClaimPublication.entries.toSet(),
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
        coEvery { userClaimCollectionManager.capabilities() } returns capabilities
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
    }

    private fun managerAnswers(vararg claims: UserClaim) {
        coEvery { userClaimCollectionManager.listUserClaims(userId, any(), defaultPage) } returns pageOf(*claims)
    }

    @Test
    fun `listUserClaims - Map every claim the page holds, in the order it holds them`() = runTest {
        val email = userClaim("email")
        val name = userClaim("name")
        val emailResource = mockResource("email")
        val nameResource = mockResource("name")

        foundUser()
        managerAnswers(email, name)
        every { userClaimMapper.toResource(email) } returns emailResource
        every { userClaimMapper.toResource(name) } returns nameResource

        val result = controller.listUserClaims(collectionRequest(), userId, null, null, null, null)

        assertEquals(listOf(emailResource, nameResource), result.claims)
    }

    @Test
    fun `listUserClaims - Hand the manager the criteria the request carries, on the page it names`() = runTest {
        val custom = userClaim("custom_field")
        val resource = mockResource("custom_field")
        val criteria = slot<CollectionCriteria>()

        foundUser()
        coEvery {
            userClaimCollectionManager.listUserClaims(userId, capture(criteria), PageParams(1, 2))
        } returns pageOf(custom)
        every { userClaimMapper.toResource(custom) } returns resource

        val result = controller.listUserClaims(collectionRequest("origin=custom"), userId, 1, 2, null, null)

        assertSame(resource, result.claims.single())
        assertEquals(listOf("custom"), criteria.captured.filters.single().values)
    }

    @Test
    fun `listUserClaims - Refuse an origin the set does not hold`() = runTest {
        coEvery { userClaimCollectionManager.capabilities() } returns capabilities
        // Neither the user nor the manager is stubbed on purpose: reaching the assertion is proof the
        // criteria are resolved before either is read.
        val exception = assertThrows<LocalizedHttpException> {
            controller.listUserClaims(collectionRequest("origin=openid_connect"), userId, null, null, null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `listUserClaims - Publish the page the manager answered, not the one that was asked for`() = runTest {
        foundUser()
        coEvery { userClaimCollectionManager.listUserClaims(userId, any(), defaultPage) } returns
                Page(items = emptyList(), page = 3, size = 7, total = 42)

        val result = controller.listUserClaims(collectionRequest(), userId, null, null, null, null)

        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }

    @Test
    fun `listUserClaims - Throw 404 when user not found`() = runTest {
        coEvery { userClaimCollectionManager.capabilities() } returns capabilities
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.listUserClaims(collectionRequest(), userId, null, null, null, null)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}
