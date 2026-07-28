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
import com.sympauthy.util.loggerForClass
import com.sympauthy.util.min
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import kotlin.math.pow

/**
 * Component in charge of the generation of validation code that will be sent to the end-user.
 *
 * As the validation code are limited in scope to a single authorization flow, we do not need to enforce the
 * uniqueness of the across all our users. The only case of collision is if the generator generates a code that
 * was already sent to the user when the users ask to send another code.
 */
@Singleton
open class ValidationCodeGenerator(
    @Inject private val validationCodeRepository: ValidationCodeRepository,
    @Inject private val validationCodeMapper: ValidationCodeMapper,
    @Inject private val randomGenerator: RandomGenerator,
    @Inject private val advancedConfig: AdvancedConfig
) {

    private val logger = loggerForClass()

    internal val validationCodeLength: Int
        get() = advancedConfig.orThrow().validationCode.length

    internal val validationCodeBound: Int
        get() = 10.0.pow(validationCodeLength - 1).toInt()

    internal val validationCodeFormat: String
        get() = "%0${validationCodeLength}d"

    @Transactional
    open suspend fun generateValidationCode(
        user: User,
        session: InteractiveFlowSession,
        media: ValidationCodeMedia,
        reasons: List<ValidationCodeReason>
    ): ValidationCode {
        var tryCount = 0
        var savedEntity: ValidationCodeEntity? = null

        val creationDate = now()
        val expirationDate = getExpirationDate(
            creationDate = creationDate,
            session = session
        )
        val resendDate = getResendDate(creationDate)

        while (savedEntity == null && tryCount < MAX_ATTEMPTS) {
            try {
                val entity = ValidationCodeEntity(
                    code = generateCode(),
                    userId = user.id,
                    media = media.name,
                    reasons = reasons.map(ValidationCodeReason::name).toTypedArray(),
                    sessionId = session.id,
                    creationDate = creationDate,
                    expirationDate = expirationDate,
                    resendDate = resendDate
                )
                savedEntity = validationCodeRepository.save(entity)
            } catch (e: Exception) {
                logger.error("Failed to insert", e)
                tryCount++
            }
        }

        return savedEntity?.let(validationCodeMapper::toValidationCode) ?: throw IllegalStateException(
            "Unable to generate code in $tryCount attempts."
        )
    }

    internal fun generateCode(): String {
        val code = randomGenerator.generateInt(
            origin = 0,
            bound = validationCodeBound
        )
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

    companion object {
        /**
         * Number of times we will try to regenerate a code in case we have a collision.
         */
        private const val MAX_ATTEMPTS = 10
    }
}
