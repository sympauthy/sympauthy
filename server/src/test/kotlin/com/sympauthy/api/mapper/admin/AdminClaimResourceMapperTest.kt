package com.sympauthy.api.mapper.admin

import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimPublication
import com.sympauthy.business.model.user.claim.ClaimPublication.ID_TOKEN
import com.sympauthy.business.model.user.claim.ClaimPublication.USERINFO
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
    fun `toResource - Report the channels a claim is published in`() {
        val resource = mapper.toResource(claim("loyalty_tier", setOf(ID_TOKEN, USERINFO)))

        assertEquals(listOf("id_token", "userinfo"), resource.publishedIn)
    }

    @Test
    fun `toResource - Report the channels in the same order whichever the claim names first`() {
        val resource = mapper.toResource(claim("loyalty_tier", setOf(USERINFO, ID_TOKEN)))

        assertEquals(listOf("id_token", "userinfo"), resource.publishedIn)
    }

    @Test
    fun `toResource - Report the one channel a claim is published in`() {
        val resource = mapper.toResource(claim("preferences", setOf(USERINFO)))

        assertEquals(listOf("userinfo"), resource.publishedIn)
    }

    @Test
    fun `toResource - Report no channel for a claim published in neither`() {
        val resource = mapper.toResource(claim("internal_note", emptySet()))

        assertEquals(emptyList<String>(), resource.publishedIn)
    }

    @Test
    fun `toResource - Report a claim the deployment signs people in with as an identifier`() {
        assertEquals(true, mapper.toResource(claim("email", setOf(ID_TOKEN))).identifier)
        assertEquals(false, mapper.toResource(claim("loyalty_tier", setOf(ID_TOKEN))).identifier)
    }

    private fun claim(id: String, publishedIn: Set<ClaimPublication>) = Claim(
        id = id,
        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        generated = false,
        userInputted = false,
        allowedValues = null,
        publishedIn = publishedIn,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = null,
                readableByUser = true,
                writableByUser = false,
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
