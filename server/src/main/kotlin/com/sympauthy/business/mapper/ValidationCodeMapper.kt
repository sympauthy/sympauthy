package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.data.model.ValidationCodeEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [ValidationCodeEntity] to the [ValidationCode] business model.
 *
 * If the row holds no identifier, or a media or a reason the model's enums do not name, an internal
 * [BusinessException] "mapper.validation_code.invalid_property" is thrown: a row this server wrote and
 * cannot read back is its own failure rather than the caller's.
 */
@Singleton
class ValidationCodeMapper {

    fun toValidationCode(entity: ValidationCodeEntity): ValidationCode {
        return ValidationCode(
            id = entity.id ?: throw invalidBusinessException("id"),
            code = entity.code,
            userId = entity.userId,
            media = toValidationCodeMedia(entity.media),
            reasons = entity.reasons.map(::toValidationCodeReason),
            sessionId = entity.sessionId,
            creationDate = entity.creationDate,
            resendDate = entity.resendDate,
            validationDate = entity.validationDate,
            expirationDate = entity.expirationDate,
        )
    }

    /**
     * The media [media] names, refusing the row where it names none.
     */
    fun toValidationCodeMedia(media: String): ValidationCodeMedia {
        return try {
            ValidationCodeMedia.valueOf(media)
        } catch (e: IllegalArgumentException) {
            throw invalidBusinessException("media", e)
        }
    }

    /**
     * The reason [reason] names, one call per element of the row's reasons, refusing the row where it
     * names none.
     */
    fun toValidationCodeReason(reason: String): ValidationCodeReason {
        return try {
            ValidationCodeReason.valueOf(reason)
        } catch (e: IllegalArgumentException) {
            throw invalidBusinessException("reasons", e)
        }
    }

    private fun invalidBusinessException(
        invalidProperty: String,
        cause: Throwable? = null
    ): BusinessException {
        return internalBusinessExceptionOf(
            detailsId = "mapper.validation_code.invalid_property",
            throwable = cause,
            values = arrayOf("property" to invalidProperty)
        )
    }
}
