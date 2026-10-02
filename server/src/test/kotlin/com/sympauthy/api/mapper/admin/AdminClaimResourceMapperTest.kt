package com.sympauthy.api.mapper.admin

import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace.ID_TOKEN
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace.USERINFO
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.model.EnabledAuthConfig
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminClaimResourceMapperTest {

    @MockK
    lateinit var uncheckedAuthConfig: EnabledAuthConfig

    @InjectMockKs
    lateinit var mapper: AdminClaimResourceMapper

    @BeforeEach
    fun setUp() {
        every { uncheckedAuthConfig.identifierClaims } returns listOf("email")
    }

    @Test
    fun `toResource - Report every place a claim is published in`() {
        val resource = mapper.toResource(claim("loyalty_tier", ClaimPublicationPlace.entries.toSet()))

        assertEquals(
            listOf("id_token", "userinfo", "access_token", "introspection", "discovery"),
            resource.publishedIn
        )
    }

    @Test
    fun `toResource - Report the places in the same order whichever the claim names first`() {
        val resource = mapper.toResource(claim("loyalty_tier", setOf(USERINFO, ID_TOKEN)))

        assertEquals(listOf("id_token", "userinfo"), resource.publishedIn)
    }

    @Test
    fun `toResource - Report the one place a claim is published in`() {
        val resource = mapper.toResource(claim("preferences", setOf(USERINFO)))

        assertEquals(listOf("userinfo"), resource.publishedIn)
    }

    @Test
    fun `toResource - Report no place for a claim published in none`() {
        val resource = mapper.toResource(claim("internal_note", emptySet()))

        assertEquals(emptyList<String>(), resource.publishedIn)
    }

    @Test
    fun `toResource - Report a claim the deployment signs people in with as an identifier`() {
        assertEquals(true, mapper.toResource(claim("email", setOf(ID_TOKEN))).identifier)
        assertEquals(false, mapper.toResource(claim("loyalty_tier", setOf(ID_TOKEN))).identifier)
    }

    @Test
    fun `toResource - Report whose a claim is beside where its name comes from`() {
        val personal = mapper.toResource(claim("name", setOf(ID_TOKEN)))
        val application = mapper.toResource(
            claim("loyalty_tier", setOf(ID_TOKEN), kind = ClaimKind.APPLICATION)
        )

        assertEquals("personal" to "openid", personal.kind to personal.origin)
        assertEquals("application" to "custom", application.kind to application.origin)
    }

    @Test
    fun `toResource - Report no kind for a claim this server answers for itself`() {
        val resource = mapper.toResource(claim("sub", setOf(ID_TOKEN), kind = null))

        assertEquals(null, resource.kind)
    }

    private fun claim(
        id: String,
        publishedIn: Set<ClaimPublicationPlace>,
        kind: ClaimKind? = ClaimKind.PERSONAL
    ) = Claim(
        id = id,
        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        kind = kind,
        group = null,
        required = false,
        generated = false,
        collectedInFlow = false,
        allowedValues = null,
        publishedIn = publishedIn,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = null,
                readableByPerson = true,
                collectedInFlow = false,
                writableByPerson = false,
                readableByClient = true,
                writableByClient = false,
                writeMaxAuthenticationAge = null
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = emptyList(),
                writableWithClientScopes = emptyList()
            )
        )
    )
}
