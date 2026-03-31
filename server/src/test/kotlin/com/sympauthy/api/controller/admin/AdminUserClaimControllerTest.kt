package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminUserClaimResourceMapper
import com.sympauthy.api.resource.admin.AdminUserClaimResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.DEFAULT_PAGE_SIZE
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.CustomClaim
import com.sympauthy.business.model.user.claim.OpenIdClaim
import com.sympauthy.business.model.user.claim.StandardClaim
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.EnabledAuthConfig
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class AdminUserClaimControllerTest {

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var uncheckedAuthConfig: AuthConfig

    @MockK
    lateinit var userClaimMapper: AdminUserClaimResourceMapper

    @InjectMockKs
    lateinit var controller: AdminUserClaimController

    private val userId = UUID.randomUUID()

    private val emailClaim = StandardClaim(
        openIdClaim = OpenIdClaim.EMAIL,
        enabled = true,
        required = true,
        allowedValues = null
    )

    private val nameClaim = StandardClaim(
        openIdClaim = OpenIdClaim.NAME,
        enabled = true,
        required = false,
        allowedValues = null
    )

    // email_verified — should be filtered out
    private val emailVerifiedClaim = CustomClaim(
        id = "email_verified",
        dataType = ClaimDataType.STRING,
        required = false,
        allowedValues = null
    )

    private val customClaim = CustomClaim(
        id = "custom_field",
        dataType = ClaimDataType.STRING,
        required = false,
        allowedValues = null
    )

    private fun mockUser(): User = mockk {
        every { id } returns userId
    }

    private fun mockEnabledAuthConfig(identifierClaimIds: List<OpenIdClaim>) {
        val enabledConfig = mockk<EnabledAuthConfig> {
            every { identifierClaims } returns identifierClaimIds
        }
        every { uncheckedAuthConfig.let { any<(AuthConfig) -> EnabledAuthConfig>().invoke(it) } } returns enabledConfig
        // Use the orThrow extension properly by mocking the sealed class cast
    }

    private fun setupAuthConfig(identifierOpenIdClaims: List<OpenIdClaim> = listOf(OpenIdClaim.EMAIL)) {
        val enabledConfig = EnabledAuthConfig(
            issuer = "test",
            audience = "test",
            token = mockk(),
            authorizationCode = mockk(),
            identifierClaims = identifierOpenIdClaims,
            userMergingEnabled = false,
            byPassword = mockk()
        )
        // uncheckedAuthConfig must be the EnabledAuthConfig itself for orThrow() to work
        // But since it's injected as AuthConfig, we need to mock it properly
        // Actually, @MockK creates a mock of AuthConfig. orThrow() checks `is EnabledAuthConfig`.
        // We can't make a MockK of AuthConfig pass `is EnabledAuthConfig` check.
        // So we replace the field directly.
    }

    private fun mockResource(claimId: String, value: Any? = null): AdminUserClaimResource = AdminUserClaimResource(
        claimId = claimId,
        value = value,
        type = "string",
        origin = "openid",
        required = false,
        identifier = false,
        group = null,
        collectedAt = null,
        verifiedAt = null
    )

    private fun mockCollectedClaim(
        claim: com.sympauthy.business.model.user.claim.Claim,
        value: Any? = "test-value",
        verificationDate: LocalDateTime? = null
    ): CollectedClaim = CollectedClaim(
        userId = userId,
        claim = claim,
        value = value,
        verified = verificationDate != null,
        collectionDate = LocalDateTime.now(),
        verificationDate = verificationDate
    )

    private fun setupDefault(
        claims: List<com.sympauthy.business.model.user.claim.Claim> = listOf(emailClaim, nameClaim),
        identifierOpenIdClaims: List<OpenIdClaim> = listOf(OpenIdClaim.EMAIL)
    ) {
        coEvery { userManager.findByIdOrNull(userId) } returns mockUser()
        every { claimManager.listEnabledClaims() } returns claims

        val enabledConfig = EnabledAuthConfig(
            issuer = "test",
            audience = "test",
            token = mockk(),
            authorizationCode = mockk(),
            identifierClaims = identifierOpenIdClaims,
            userMergingEnabled = false,
            byPassword = mockk()
        )
        // We need uncheckedAuthConfig to be an EnabledAuthConfig for orThrow() to work.
        // Since @MockK creates a mock of AuthConfig (sealed class), and orThrow() uses a `when` on `is EnabledAuthConfig`,
        // we must use a relaxed mock approach or directly set the field.
        // The simplest workaround: replace the controller's field via reflection or use @RelaxedMockK.
        // Actually, let's just create the controller manually for these tests.
    }

    // Helper to create controller with real EnabledAuthConfig
    private fun createController(
        identifierOpenIdClaims: List<OpenIdClaim> = listOf(OpenIdClaim.EMAIL)
    ): AdminUserClaimController {
        val enabledConfig = EnabledAuthConfig(
            issuer = "test",
            audience = "test",
            token = mockk(),
            authorizationCode = mockk(),
            identifierClaims = identifierOpenIdClaims,
            userMergingEnabled = false,
            byPassword = mockk()
        )
        return AdminUserClaimController(
            userManager = userManager,
            claimManager = claimManager,
            collectedClaimManager = collectedClaimManager,
            uncheckedAuthConfig = enabledConfig,
            userClaimMapper = userClaimMapper
        )
    }

    @Test
    fun `listUserClaims - Return paginated list with defaults`() = runTest {
        val ctrl = createController()
        coEvery { userManager.findByIdOrNull(userId) } returns mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, nameClaim)
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns emptyList()

        val emailResource = mockResource("email")
        val nameResource = mockResource("name")
        every { userClaimMapper.toResource(emailClaim, null, true) } returns emailResource
        every { userClaimMapper.toResource(nameClaim, null, false) } returns nameResource

        val result = ctrl.listUserClaims(userId, null, null, null, null, null, null, null, null)

        assertEquals(DEFAULT_PAGE, result.page)
        assertEquals(DEFAULT_PAGE_SIZE, result.size)
        assertEquals(2, result.total)
        assertEquals(2, result.claims.size)
        assertSame(emailResource, result.claims[0])
        assertSame(nameResource, result.claims[1])
    }

    @Test
    fun `listUserClaims - Verified claims are excluded`() = runTest {
        val ctrl = createController()
        coEvery { userManager.findByIdOrNull(userId) } returns mockUser()
        // emailClaim has verifiedId = "email_verified", so emailVerifiedClaim should be excluded
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, emailVerifiedClaim, nameClaim)
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns emptyList()

        val emailResource = mockResource("email")
        val nameResource = mockResource("name")
        every { userClaimMapper.toResource(emailClaim, null, true) } returns emailResource
        every { userClaimMapper.toResource(nameClaim, null, false) } returns nameResource

        val result = ctrl.listUserClaims(userId, null, null, null, null, null, null, null, null)

        assertEquals(2, result.total)
        assertEquals(listOf("email", "name"), result.claims.map { it.claimId })
    }

    @Test
    fun `listUserClaims - Filter by claimId`() = runTest {
        val ctrl = createController()
        coEvery { userManager.findByIdOrNull(userId) } returns mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, nameClaim)
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns emptyList()

        val emailResource = mockResource("email")
        every { userClaimMapper.toResource(emailClaim, null, true) } returns emailResource

        val result = ctrl.listUserClaims(userId, null, null, "email", null, null, null, null, null)

        assertEquals(1, result.total)
        assertEquals("email", result.claims[0].claimId)
    }

    @Test
    fun `listUserClaims - Filter by identifier`() = runTest {
        val ctrl = createController()
        coEvery { userManager.findByIdOrNull(userId) } returns mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, nameClaim)
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns emptyList()

        val emailResource = mockResource("email")
        every { userClaimMapper.toResource(emailClaim, null, true) } returns emailResource

        val result = ctrl.listUserClaims(userId, null, null, null, true, null, null, null, null)

        assertEquals(1, result.total)
        assertEquals("email", result.claims[0].claimId)
    }

    @Test
    fun `listUserClaims - Filter by required`() = runTest {
        val ctrl = createController()
        coEvery { userManager.findByIdOrNull(userId) } returns mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, nameClaim)
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns emptyList()

        val emailResource = mockResource("email")
        every { userClaimMapper.toResource(emailClaim, null, true) } returns emailResource

        val result = ctrl.listUserClaims(userId, null, null, null, null, true, null, null, null)

        assertEquals(1, result.total)
        assertEquals("email", result.claims[0].claimId)
    }

    @Test
    fun `listUserClaims - Filter by origin`() = runTest {
        val ctrl = createController()
        coEvery { userManager.findByIdOrNull(userId) } returns mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, customClaim)
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns emptyList()

        val customResource = mockResource("custom_field")
        every { userClaimMapper.toResource(customClaim, null, false) } returns customResource

        val result = ctrl.listUserClaims(userId, null, null, null, null, null, null, null, "custom")

        assertEquals(1, result.total)
        assertEquals("custom_field", result.claims[0].claimId)
    }

    @Test
    fun `listUserClaims - Filter by collected`() = runTest {
        val ctrl = createController()
        coEvery { userManager.findByIdOrNull(userId) } returns mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, nameClaim)

        val collectedEmail = mockCollectedClaim(emailClaim, "user@test.com")
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns listOf(collectedEmail)

        val emailResource = mockResource("email", "user@test.com")
        every { userClaimMapper.toResource(emailClaim, collectedEmail, true) } returns emailResource

        val result = ctrl.listUserClaims(userId, null, null, null, null, null, true, null, null)

        assertEquals(1, result.total)
        assertEquals("email", result.claims[0].claimId)
    }

    @Test
    fun `listUserClaims - Filter by verified`() = runTest {
        val ctrl = createController()
        coEvery { userManager.findByIdOrNull(userId) } returns mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, nameClaim)

        val verifiedDate = LocalDateTime.of(2025, 1, 1, 0, 0)
        val collectedEmail = mockCollectedClaim(emailClaim, "user@test.com", verifiedDate)
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns listOf(collectedEmail)

        val emailResource = mockResource("email", "user@test.com")
        every { userClaimMapper.toResource(emailClaim, collectedEmail, true) } returns emailResource

        val result = ctrl.listUserClaims(userId, null, null, null, null, null, null, true, null)

        assertEquals(1, result.total)
        assertEquals("email", result.claims[0].claimId)
    }

    @Test
    fun `listUserClaims - Claims without collected values show null metadata`() = runTest {
        val ctrl = createController()
        coEvery { userManager.findByIdOrNull(userId) } returns mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(nameClaim)
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns emptyList()

        val nameResource = AdminUserClaimResource(
            claimId = "name",
            value = null,
            type = "string",
            origin = "openid",
            required = false,
            identifier = false,
            group = "identity",
            collectedAt = null,
            verifiedAt = null
        )
        every { userClaimMapper.toResource(nameClaim, null, false) } returns nameResource

        val result = ctrl.listUserClaims(userId, null, null, null, null, null, null, null, null)

        assertEquals(1, result.total)
        assertNull(result.claims[0].value)
        assertNull(result.claims[0].collectedAt)
        assertNull(result.claims[0].verifiedAt)
    }

    @Test
    fun `listUserClaims - Throw 404 when user not found`() = runTest {
        val ctrl = createController()
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            ctrl.listUserClaims(userId, null, null, null, null, null, null, null, null)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `listUserClaims - Pagination works correctly`() = runTest {
        val ctrl = createController()
        coEvery { userManager.findByIdOrNull(userId) } returns mockUser()

        val claims = (1..5).map {
            CustomClaim(id = "claim_$it", dataType = ClaimDataType.STRING, required = false, allowedValues = null)
        }
        every { claimManager.listEnabledClaims() } returns claims
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns emptyList()

        val resources = claims.map { mockResource(it.id) }
        claims.forEachIndexed { i, claim ->
            every { userClaimMapper.toResource(claim, null, false) } returns resources[i]
        }

        val result = ctrl.listUserClaims(userId, 1, 2, null, null, null, null, null, null)

        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(5, result.total)
        assertEquals(2, result.claims.size)
        assertSame(resources[2], result.claims[0])
        assertSame(resources[3], result.claims[1])
    }

    @Test
    fun `listUserClaims - Empty page when page exceeds total`() = runTest {
        val ctrl = createController()
        coEvery { userManager.findByIdOrNull(userId) } returns mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        coEvery { collectedClaimManager.findByUserIdAndClaims(userId, any()) } returns emptyList()

        val result = ctrl.listUserClaims(userId, 5, 20, null, null, null, null, null, null)

        assertEquals(5, result.page)
        assertEquals(1, result.total)
        assertTrue(result.claims.isEmpty())
    }
}
