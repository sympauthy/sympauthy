package com.sympauthy.business.validationcode.manager

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.flow.model.InteractiveFlowSession
import com.sympauthy.business.user.model.User
import com.sympauthy.business.validationcode.mapper.ValidationCodeMapper
import com.sympauthy.business.validationcode.model.ValidationCode
import com.sympauthy.business.validationcode.model.ValidationCodeMedia
import com.sympauthy.business.validationcode.model.ValidationCodeReason
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.ValidationCodeConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.repository.ValidationCodeRepository
import com.sympauthy.platform.random.RandomGenerator
import com.sympauthy.platform.util.min
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.Locale
import kotlin.math.pow

/**
 * Component in charge of the generation of validation code that will be sent to the end-user.
 *
 * A validation code is limited in scope to the interactive flow session it is sent in, so it only has to be unique
 * among the codes of that session. A code drawn twice is one the session already holds, and is answered with another
 * draw.
 */
@Singleton
class ValidationCodeGenerator(
    @Inject private val validationCodeRepository: ValidationCodeRepository,
    @Inject private val validationCodeMapper: ValidationCodeMapper,
    @Inject private val randomGenerator: RandomGenerator,
    @Inject private val advancedConfig: AdvancedConfig
) {

    internal val validationCodeLength: Int
        get() = advancedConfig.orThrow().validationCode.length

    internal val validationCodeBound: Int
        get() = 10.0.pow(validationCodeLength - 1).toInt()

    internal val validationCodeFormat: String
        get() = "%0${validationCodeLength}d"

    /**
     * Draw a code the [session] does not hold yet, store it for the [user] to receive through [media] for the
     * [reasons], and return it.
     *
     * A draw the session already holds is answered with another, up to [MAX_ATTEMPTS] of them, and stores nothing,
     * so a caller may hold a transaction open around this. Once every draw was held, it throws
     * `validationcode.every_draw_held`.
     */
    suspend fun generateValidationCode(
        user: User,
        session: InteractiveFlowSession,
        media: ValidationCodeMedia,
        reasons: List<ValidationCodeReason>
    ): ValidationCode {
        val creationDate = now()
        val expirationDate = getExpirationDate(
            creationDate = creationDate,
            session = session
        )
        val resendDate = getResendDate(creationDate)

        repeat(MAX_ATTEMPTS) {
            val code = generateCode()
            val inserted = validationCodeRepository.insertUnlessCodeHeld(
                code = code,
                userId = user.id,
                media = media.name,
                reasons = reasons.map(ValidationCodeReason::name).toTypedArray(),
                sessionId = session.id,
                creationDate = creationDate,
                resendDate = resendDate,
                expirationDate = expirationDate
            )
            if (inserted > 0) {
                return validationCodeRepository.findBySessionIdAndCode(sessionId = session.id, code = code)
                    ?.let(validationCodeMapper::toValidationCode)
                    ?: throw internalBusinessExceptionOf("validationcode.inserted_missing", "media" to media.name)
            }
        }
        throw internalBusinessExceptionOf(
            "validationcode.every_draw_held",
            "attempts" to MAX_ATTEMPTS.toString(),
            "media" to media.name
        )
    }

    internal fun generateCode(): String {
        val code = randomGenerator.generateInt(
            origin = 0,
            bound = validationCodeBound
        )
        return String.format(Locale.ROOT, validationCodeFormat, code)
    }

    /**
     * Return the expiration date for a newly created validation code at [creationDate].
     *
     * It is the minimum between the [ValidationCodeConfig.expiration] delay in the server configuration.
     * and the [InteractiveFlowSession.expirationDate] of the provided [session].
     */
    internal fun getExpirationDate(
        session: InteractiveFlowSession,
        creationDate: LocalDateTime
    ): LocalDateTime {
        val expiration = advancedConfig.orThrow().validationCode.expiration
        return min(
            session.expirationDate,
            creationDate.plus(expiration)
        )
    }

    internal fun getResendDate(creationDate: LocalDateTime): LocalDateTime? {
        val resendDelay = advancedConfig.orThrow().validationCode.resendDelay ?: return null
        return creationDate.plus(resendDelay)
    }

    companion object {
        /**
         * Number of codes drawn before giving up on finding one the session does not hold yet.
         */
        internal const val MAX_ATTEMPTS = 10
    }
}
