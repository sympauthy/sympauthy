package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.mapper.ValidationCodeMapper
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.ValidationCodeConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.ValidationCodeEntity
import com.sympauthy.data.repository.ValidationCodeRepository
import com.sympauthy.util.min
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import kotlin.math.pow

/**
 * Component in charge of the generation of validation code that will be sent to the end-user.
 *
 * A validation code is limited in scope to the interactive flow session it is sent in, so it only has to be unique
 * among the codes of that session. It is drawn among the codes the session does not hold yet rather than drawn again
 * on a collision, so a caller may store it inside a transaction of its own: PostgreSQL aborts the transaction a
 * unique violation is raised in, and a draw retried there would be refused whatever code it carried.
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

    suspend fun generateValidationCode(
        user: User,
        session: InteractiveFlowSession,
        media: ValidationCodeMedia,
        reasons: List<ValidationCodeReason>
    ): ValidationCode {
        val heldCodes = validationCodeRepository.findBySessionId(session.id).map(ValidationCodeEntity::code)
        val creationDate = now()
        val entity = ValidationCodeEntity(
            code = generateCode(heldCodes),
            userId = user.id,
            media = media.name,
            reasons = reasons.map(ValidationCodeReason::name).toTypedArray(),
            sessionId = session.id,
            creationDate = creationDate,
            expirationDate = getExpirationDate(
                creationDate = creationDate,
                session = session
            ),
            resendDate = getResendDate(creationDate)
        )
        return validationCodeRepository.save(entity).let(validationCodeMapper::toValidationCode)
    }

    /**
     * Draw one of the [validationCodeBound] codes of [validationCodeLength] digits that is not in [heldCodes].
     *
     * The draw picks the rank of the code among the free ones, then steps over each held code at or below it, so
     * every free code is as likely to be drawn and none of the draws lands on a held one.
     */
    internal fun generateCode(heldCodes: Collection<String>): String {
        val length = validationCodeLength
        val bound = validationCodeBound
        val heldValues = heldCodes
            .filter { it.length == length }
            .mapNotNull(String::toIntOrNull)
            .filter { it in 0 until bound }
            .distinct()
            .sorted()
        check(heldValues.size < bound) {
            "The session already holds every one of the $bound codes of $length digits."
        }

        var code = randomGenerator.generateInt(
            origin = 0,
            bound = bound - heldValues.size
        )
        for (heldValue in heldValues) {
            if (heldValue > code) break
            code++
        }
        return String.format(validationCodeFormat, code)
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
}
