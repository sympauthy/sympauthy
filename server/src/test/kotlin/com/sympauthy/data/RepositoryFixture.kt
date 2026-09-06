package com.sympauthy.data

import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.model.AuthorizationCodeEntity
import com.sympauthy.data.model.ConsentEntity
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.model.InvitationEntity
import com.sympauthy.data.model.PasswordEntity
import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import com.sympauthy.data.model.TotpEnrollmentEntity
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.model.ValidationCodeEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.data.repository.ConsentRepository
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import com.sympauthy.data.repository.InvitationRepository
import com.sympauthy.data.repository.PasswordRepository
import com.sympauthy.data.repository.ProviderUserInfoRepository
import com.sympauthy.data.repository.TotpEnrollmentRepository
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.repository.ValidationCodeRepository
import kotlinx.coroutines.test.runTest
import java.time.LocalDateTime
import java.util.*

/** The instant every fixture dates its rows from, so no assertion turns on the clock. */
val BASE_DATE: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0)

/** Runs [block] against [database], deleting the rows it created once it ends. */
// The throw inside the finally is the point rather than an oversight: it only fires where the test
// itself passed, so there is no earlier failure for it to discard, and a cleanup that failed
// silently would leave the next run reading another run's rows.
@Suppress("ThrowingExceptionFromFinally")
fun withFixture(database: Database, block: suspend RepositoryFixture.() -> Unit) {
    val fixture = RepositoryFixture(database)
    runTest {
        var failure: Throwable? = null
        try {
            fixture.block()
        } catch (thrown: Throwable) {
            failure = thrown
            throw thrown
        } finally {
            // Attached rather than thrown: a cleanup that fails while the test was already failing must
            // not replace the assertion that explains why.
            runCatching { fixture.deleteCreatedRows() }
                .onFailure { failure?.addSuppressed(it) ?: throw it }
        }
    }
}

/**
 * The rows one test created, and the parents it needed to create them.
 *
 * Deletion runs in reverse order of registration, so a row registered after the row it references is
 * removed before it and no foreign key is left dangling.
 */
class RepositoryFixture(val database: Database) {

    /**
     * Resolved here rather than on first use: it starts the context, which for PostgreSQL pulls an
     * image and migrates a schema, and neither belongs inside the coroutine test's timeout.
     */
    private val users = database.bean<UserRepository>()

    private val deletions = ArrayDeque<suspend () -> Unit>()

    inline fun <reified T : Any> repository(): T = database.bean()

    /** Registers [delete] to run when the test ends, ahead of everything registered before it. */
    fun deleteOnEnd(delete: suspend () -> Unit) = deletions.addFirst(delete)

    /**
     * Saves a user to hang rows off, and returns the identifier the database generated. Pass [sessionId]
     * to save one that is still provisional for that session.
     */
    suspend fun newUser(status: String = "enabled", sessionId: UUID? = null): UUID = users
        .save(UserEntity(status = status, creationDate = BASE_DATE, sessionId = sessionId))
        .id!!
        .also { id -> deleteOnEnd { users.deleteById(id) } }

    /**
     * Saves a session to hang rows off. It expires an hour from now rather than after [BASE_DATE],
     * which is long past: a session dated from there is already expired against the database's clock,
     * and any query gated on one still being valid would be handed the opposite of what it asked for.
     */
    suspend fun newSession(
        purposes: Array<String> = arrayOf("OAUTH2_AUTHORIZE"),
        expirationDate: LocalDateTime = LocalDateTime.now().plusHours(1),
        userId: UUID? = null
    ): InteractiveFlowSessionEntity {
        val sessions = database.bean<InteractiveFlowSessionRepository>()
        return sessions.save(
            InteractiveFlowSessionEntity(
                purposes = purposes,
                initiatingPurpose = purposes.first(),
                sessionDate = BASE_DATE,
                flowId = "flow",
                expirationDate = expirationDate,
                userId = userId
            )
        ).also { session -> deleteOnEnd { sessions.deleteById(session.id!!) } }
    }

    /** Saves the authorization code a session was handed back. */
    suspend fun newCode(sessionId: UUID, code: String) {
        val codes = database.bean<AuthorizationCodeRepository>()
        codes.save(
            AuthorizationCodeEntity(
                sessionId = sessionId,
                code = code,
                creationDate = BASE_DATE,
                expirationDate = BASE_DATE.plusMinutes(10)
            )
        )
        deleteOnEnd { codes.deleteBySessionIdIn(listOf(sessionId)) }
    }

    /** Saves a password for [userId], provisional when [sessionId] is given. */
    suspend fun newPassword(userId: UUID, sessionId: UUID? = null) {
        val passwords = database.bean<PasswordRepository>()
        val saved = passwords.save(
            PasswordEntity(
                userId = userId,
                salt = byteArrayOf(1),
                hashedPassword = byteArrayOf(2),
                creationDate = BASE_DATE,
                expirationDate = null,
                sessionId = sessionId
            )
        )
        deleteOnEnd { saved.id?.let { passwords.deleteById(it) } }
    }

    /** Collects [claim] for [userId] holding [value], provisionally when [sessionId] is given. */
    suspend fun newClaim(userId: UUID, claim: String, value: String, sessionId: UUID? = null) {
        val claims = database.bean<CollectedClaimRepository>()
        val saved = claims.save(
            CollectedClaimEntity(
                userId = userId,
                claim = claim,
                value = value,
                verified = null,
                collectionDate = BASE_DATE,
                verificationDate = null,
                sessionId = sessionId
            )
        )
        deleteOnEnd { saved.id?.let { claims.deleteById(it) } }
    }

    /** Enrolls a second factor for [userId], provisionally when [sessionId] is given. */
    suspend fun newTotpEnrollment(userId: UUID, sessionId: UUID? = null) {
        val enrollments = database.bean<TotpEnrollmentRepository>()
        val saved = enrollments.save(
            TotpEnrollmentEntity(
                userId = userId,
                secret = byteArrayOf(3),
                creationDate = BASE_DATE,
                confirmedDate = null,
                sessionId = sessionId
            )
        )
        deleteOnEnd { saved.id?.let { enrollments.deleteById(it) } }
    }

    /** Links a user to a provider under [subject], provisionally when [sessionId] is given. */
    suspend fun newProviderLink(providerId: String, userId: UUID, subject: String, sessionId: UUID? = null) {
        val links = database.bean<ProviderUserInfoRepository>()
        links.save(
            ProviderUserInfoEntity(
                id = ProviderUserInfoEntityId(providerId = providerId, userId = userId),
                linkDate = BASE_DATE,
                fetchDate = BASE_DATE,
                changeDate = BASE_DATE,
                subject = subject,
                sessionId = sessionId
            )
        )
        deleteOnEnd { links.deleteByProviderIdAndUserId(providerId, userId) }
    }

    /** Sends [userId] a validation code, against the live session [sessionId]. */
    suspend fun newValidationCode(userId: UUID, sessionId: UUID) {
        val codes = database.bean<ValidationCodeRepository>()
        val saved = codes.save(
            ValidationCodeEntity(
                code = "000000",
                userId = userId,
                media = "EMAIL",
                reasons = arrayOf("EMAIL_CLAIM"),
                sessionId = sessionId,
                creationDate = BASE_DATE,
                resendDate = null,
                expirationDate = BASE_DATE.plusMinutes(10)
            )
        )
        deleteOnEnd { saved.id?.let { codes.deleteById(it) } }
    }

    /** Records [userId] consenting to [audienceId]. */
    suspend fun newConsent(userId: UUID, audienceId: String) {
        val consents = database.bean<ConsentRepository>()
        val saved = consents.save(
            ConsentEntity(
                userId = userId,
                audienceId = audienceId,
                promptedByClientId = "client",
                scopes = arrayOf("openid"),
                consentedAt = BASE_DATE
            )
        )
        deleteOnEnd { saved.id?.let { consents.deleteById(it) } }
    }

    /**
     * Records an invitation to [audienceId] that [userId] consumed.
     *
     * The lookup hash is derived from [userId] rather than fixed: `invitations__token_lookup_hash` is a
     * unique index, so two invitations sharing a constant would collide inside the fixture.
     */
    suspend fun newConsumedInvitation(userId: UUID, audienceId: String): UUID {
        val invitations = database.bean<InvitationRepository>()
        val saved = invitations.save(
            InvitationEntity(
                audienceId = audienceId,
                tokenLookupHash = userId.toString().toByteArray(),
                hashedToken = byteArrayOf(5),
                salt = byteArrayOf(6),
                tokenPrefix = "prefix",
                status = "CONSUMED",
                createdBy = "ADMIN",
                consumedByUserId = userId,
                createdAt = BASE_DATE,
                expiresAt = BASE_DATE.plusDays(1),
                consumedAt = BASE_DATE
            )
        )
        deleteOnEnd { saved.id?.let { invitations.deleteById(it) } }
        return saved.id!!
    }

    /** Issues an access token to [userId]. */
    suspend fun newAuthenticationToken(userId: UUID) {
        val tokens = database.bean<AuthenticationTokenRepository>()
        val saved = tokens.save(
            AuthenticationTokenEntity(
                type = "ACCESS",
                userId = userId,
                clientId = "client",
                grantedScopes = arrayOf("openid"),
                consentedScopes = emptyArray(),
                clientScopes = emptyArray(),
                sessionId = null,
                grantType = "authorization_code",
                issueDate = BASE_DATE,
                expirationDate = BASE_DATE.plusHours(1)
            )
        )
        deleteOnEnd { saved.id?.let { tokens.deleteById(it) } }
    }

    /**
     * Runs every registered deletion before rethrowing, so one failure does not leak the rows behind it
     * into a database the whole run shares.
     */
    suspend fun deleteCreatedRows() {
        var failure: Throwable? = null
        deletions.forEach { delete ->
            runCatching { delete() }.onFailure { failure?.addSuppressed(it) ?: run { failure = it } }
        }
        failure?.let { throw it }
    }
}
