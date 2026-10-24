package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimAclChallenge
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.ClaimAclExpression
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.GeneratedClaim
import com.sympauthy.business.model.user.claim.PersonalClaim
import com.sympauthy.expression.AuthenticationIsYoungerThanFunction.Companion.FUNCTION_NAME as AUTHENTICATION_IS_YOUNGER_THAN
import com.sympauthy.expression.ScopeIsConsentedFunction.Companion.FUNCTION_NAME as SCOPE_IS_CONSENTED
import com.sympauthy.expression.ScopeIsGrantedFunction.Companion.FUNCTION_NAME as SCOPE_IS_GRANTED
import com.sympauthy.expression.TokenIsBoundFunction.Companion.FUNCTION_NAME as TOKEN_IS_BOUND
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime

class ClaimAclManagerTest {

    private val manager = ClaimAclManager()

    private val client = mockk<Client>()

    private fun claim(acl: ClaimAcl) = PersonalClaim(
        id = "phone_number",
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        allowedValues = null,
        publishedIn = ClaimPublicationPlace.entries.toSet(),
        publishedInWhenRequested = emptySet(),
        acl = acl
    )

    private fun context(
        consentedScopes: List<String> = emptyList(),
        grantedScopes: List<String> = emptyList(),
        authenticationDate: LocalDateTime? = NOW,
        bound: Boolean = false
    ) = ClaimAclContext(client, consentedScopes, grantedScopes, authenticationDate, bound)

    private fun consentedTo(scope: String) =
        ClaimAclExpression("$SCOPE_IS_CONSENTED(\"$scope\")", consentedScopes = setOf(scope))

    @Test
    fun `canBeReadByClient - Open the door in a context its expression is true for`() {
        val claim = claim(
            ClaimAcl.SHUT.copy(
                readableByClient = ClaimAclExpression(
                    """$SCOPE_IS_CONSENTED("profile") || $SCOPE_IS_GRANTED("users:claims:read")""",
                    consentedScopes = setOf("profile")
                )
            )
        )

        assertTrue(manager.canBeReadByClient(claim, context(consentedScopes = listOf("profile")), NOW))
        assertTrue(manager.canBeReadByClient(claim, context(grantedScopes = listOf("users:claims:read")), NOW))
        assertFalse(manager.canBeReadByClient(claim, context(consentedScopes = listOf("email")), NOW))
    }

    @Test
    fun `canBeReadByPerson - Keep a door shut that no expression opens`() {
        val claim = claim(ClaimAcl.SHUT.copy(readableByClient = ClaimAclExpression("true")))

        assertFalse(manager.canBeReadByPerson(claim, context(), NOW))
    }

    @Test
    fun `canBeWrittenByPerson - Read only the door it is asked about`() {
        val claim = claim(
            ClaimAcl.SHUT.copy(
                readableByPerson = consentedTo("profile"),
                writableByPerson = consentedTo("profile:write")
            )
        )
        val readOnly = context(consentedScopes = listOf("profile"))

        assertTrue(manager.canBeReadByPerson(claim, readOnly, NOW))
        assertFalse(manager.canBeWrittenByPerson(claim, readOnly, NOW))
        assertTrue(manager.canBeWrittenByPerson(claim, context(consentedScopes = listOf("profile:write")), NOW))
    }

    @Test
    fun `canBeWrittenByPerson - Answer from the age the expression names`() {
        val claim = claim(
            ClaimAcl.SHUT.copy(
                writableByPerson = ClaimAclExpression(
                    """$AUTHENTICATION_IS_YOUNGER_THAN("5m")""",
                    authenticationAges = setOf(Duration.ofMinutes(5))
                )
            )
        )

        assertTrue(manager.canBeWrittenByPerson(claim, context(authenticationDate = NOW.minusMinutes(4)), NOW))
        assertFalse(manager.canBeWrittenByPerson(claim, context(authenticationDate = NOW.minusMinutes(6)), NOW))
    }

    @Test
    fun `isCollectedInFlow - Answer from the consent the flow carries`() {
        val claim = claim(ClaimAcl.SHUT.copy(collectedInFlow = consentedTo("profile")))

        assertTrue(manager.isCollectedInFlow(claim, client, listOf("profile")))
        assertFalse(manager.isCollectedInFlow(claim, client, listOf("email")))
    }

    @Test
    fun `isCollectedInFlow - Never collect a generated claim`() {
        val claim = GeneratedClaim(
            id = "sub",
            verifiedId = null,
            dataType = ClaimDataType.STRING,
            group = null,
            publishedIn = emptySet(),
            acl = ClaimAcl.SHUT.copy(collectedInFlow = ClaimAclExpression("true"))
        )

        assertFalse(manager.isCollectedInFlow(claim, client, emptyList()))
    }

    @Test
    fun `canBeReadByClient - Fail as the server's own failure where an accepted expression no longer evaluates`() {
        val claim = claim(ClaimAcl.SHUT.copy(readableByClient = ClaimAclExpression("1 + 1")))

        val exception = assertThrows<BusinessException> { manager.canBeReadByClient(claim, context(), NOW) }

        assertEquals("expression.evaluate.invalid_return", exception.detailsId)
        assertFalse(exception.recoverable)
    }

    @Test
    fun `getPersonWriteChallenges - Name the scope the person did not consent to`() {
        val claim = claim(ClaimAcl.SHUT.copy(writableByPerson = consentedTo("profile:write")))

        val challenges = manager.getPersonWriteChallenges(claim, context(consentedScopes = listOf("profile")), NOW)

        assertEquals(listOf(ClaimAclChallenge.InsufficientScope(listOf("profile:write"))), challenges)
    }

    @Test
    fun `getPersonWriteChallenges - Carry every condition that failed under an or`() {
        val claim = claim(
            ClaimAcl.SHUT.copy(
                writableByPerson = ClaimAclExpression(
                    """$AUTHENTICATION_IS_YOUNGER_THAN("5m") || $TOKEN_IS_BOUND()""",
                    authenticationAges = setOf(Duration.ofMinutes(5)),
                    asksForBoundToken = true
                )
            )
        )

        val challenges = manager.getPersonWriteChallenges(
            claim, context(authenticationDate = NOW.minusHours(1), bound = false), NOW
        )

        assertEquals(
            listOf(
                ClaimAclChallenge.InsufficientUserAuthentication(Duration.ofMinutes(5)),
                ClaimAclChallenge.UnboundToken
            ),
            challenges
        )
    }

    @Test
    fun `getPersonWriteChallenges - Leave out a condition the token met`() {
        val claim = claim(
            ClaimAcl.SHUT.copy(
                writableByPerson = ClaimAclExpression(
                    """$SCOPE_IS_CONSENTED("profile") && $TOKEN_IS_BOUND()""",
                    consentedScopes = setOf("profile"),
                    asksForBoundToken = true
                )
            )
        )

        val challenges = manager.getPersonWriteChallenges(
            claim, context(consentedScopes = listOf("profile"), bound = false), NOW
        )

        assertEquals(listOf(ClaimAclChallenge.UnboundToken), challenges)
    }

    @Test
    fun `getPersonWriteChallenges - Demand the smallest of the ages refused`() {
        val claim = claim(
            ClaimAcl.SHUT.copy(
                writableByPerson = ClaimAclExpression(
                    """$AUTHENTICATION_IS_YOUNGER_THAN("1h") && $AUTHENTICATION_IS_YOUNGER_THAN("5m")""",
                    authenticationAges = setOf(Duration.ofHours(1), Duration.ofMinutes(5))
                )
            )
        )

        val challenges = manager.getPersonWriteChallenges(claim, context(authenticationDate = null), NOW)

        assertEquals(listOf(ClaimAclChallenge.InsufficientUserAuthentication(Duration.ofMinutes(5))), challenges)
    }

    @Test
    fun `getPersonWriteChallenges - Carry none for a condition no token is re-authorized into`() {
        val claim = claim(ClaimAcl.SHUT.copy(writableByPerson = ClaimAclExpression("""$SCOPE_IS_GRANTED("premium")""")))

        assertTrue(manager.getPersonWriteChallenges(claim, context(), NOW).isEmpty())
    }

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 6, 1, 12, 0, 0)
    }
}
