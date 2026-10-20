package com.sympauthy.data

import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.model.AuthorizationCodeEntity
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.model.GrantEntity
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.model.PasswordEntity
import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import com.sympauthy.data.model.TotpEnrollmentEntity
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.GrantRepository
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import com.sympauthy.data.repository.PasswordRepository
import com.sympauthy.data.repository.ProviderUserInfoRepository
import com.sympauthy.data.repository.TotpEnrollmentRepository
import com.sympauthy.data.repository.UserRepository
import kotlinx.coroutines.test.runTest
import java.time.LocalDateTime
import java.util.*

/** The instant every fixture dates its rows from, so no assertion turns on the clock. */
val BASE_DATE: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0)

/**
 * Runs [block] against [database], deleting the rows it created once it ends.
 *
 * Throwing from the `finally` is deliberate, and what the `@Suppress` is for: a cleanup that failed
 * silently would leave the next run reading another run's rows.
 */
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

    /**
     * Saves the authorization code an authorization handed back, under the grant [grantId] it opened.
     */
    suspend fun newCode(
        grantId: UUID,
        code: String,
        expirationDate: LocalDateTime = BASE_DATE.plusMinutes(10),
        exchangeDate: LocalDateTime? = null
    ) {
        val codes = database.bean<AuthorizationCodeRepository>()
        codes.save(
            AuthorizationCodeEntity(
                grantId = grantId,
                code = code,
                codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                codeChallengeMethod = "S256",
                nonce = null,
                creationDate = BASE_DATE,
                expirationDate = expirationDate,
                exchangeDate = exchangeDate
            )
        )
        deleteOnEnd { codes.deleteById(grantId) }
    }

    /**
     * Saves a grant opened for [userId] by [clientId], for [audienceId], and returns the identifier the
     * database generated. Every column a test does not name holds what an authorization code exchange would
     * have written.
     */
    @Suppress("LongParameterList")
    suspend fun newGrant(
        userId: UUID?,
        clientId: String = "client",
        audienceId: String = "audience",
        grantType: String = "authorization_code",
        sessionId: UUID? = UUID.randomUUID(),
        actorGrantId: UUID? = null,
        authenticationDate: LocalDateTime? = null,
        grantedScopes: Array<String> = emptyArray(),
        consentedScopes: Array<String> = arrayOf("openid"),
        clientScopes: Array<String> = emptyArray(),
        requestedIdTokenClaims: Array<String> = emptyArray(),
        requestedUserinfoClaims: Array<String> = emptyArray(),
        expirationDate: LocalDateTime? = null,
        revokedAt: LocalDateTime? = null,
        revokedBy: String? = null
    ): UUID {
        val grants = database.bean<GrantRepository>()
        return grants.save(
            GrantEntity(
                userId = userId,
                clientId = clientId,
                audienceId = audienceId,
                grantType = grantType,
                grantedScopes = grantedScopes,
                consentedScopes = consentedScopes,
                clientScopes = clientScopes,
                authenticationDate = authenticationDate,
                requestedIdTokenClaims = requestedIdTokenClaims,
                requestedUserinfoClaims = requestedUserinfoClaims,
                sessionId = sessionId,
                actorGrantId = actorGrantId,
                creationDate = BASE_DATE,
                expirationDate = expirationDate,
                revokedAt = revokedAt,
                revokedBy = revokedBy
            )
        ).id!!.also { id -> deleteOnEnd { grants.deleteById(id) } }
    }

    /** Saves a token issued under the grant [grantId], and returns the identifier the database generated. */
    suspend fun newToken(
        grantId: UUID,
        expirationDate: LocalDateTime? = null,
        type: String = "ACCESS",
        grantType: String = "authorization_code"
    ): UUID {
        val tokens = database.bean<AuthenticationTokenRepository>()
        return tokens.save(
            AuthenticationTokenEntity(
                type = type,
                grantId = grantId,
                grantType = grantType,
                issueDate = BASE_DATE,
                expirationDate = expirationDate
            )
        ).id!!.also { id -> deleteOnEnd { tokens.deleteById(id) } }
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

    /**
     * Collects [claim] for [userId] holding [value], provisionally when [sessionId] is given. [value] is
     * the business value, written the way the mappers write one: encoded into the column, and hashed into
     * the key the row is found by.
     */
    suspend fun newClaim(userId: UUID, claim: String, value: String, sessionId: UUID? = null) {
        val claims = database.bean<CollectedClaimRepository>()
        val values = database.bean<ClaimValueMapper>()
        val saved = claims.save(
            CollectedClaimEntity(
                userId = userId,
                claim = claim,
                value = values.toEntity(value),
                foldedEqualityHash = values.toFoldedEqualityHash(value),
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
