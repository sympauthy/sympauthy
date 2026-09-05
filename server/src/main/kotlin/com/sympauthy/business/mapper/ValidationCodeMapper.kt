package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.data.model.ValidationCodeEntity
import org.mapstruct.Mapper

/**
 * Handle the mapping from the [ValidationCodeEntity] to the [ValidationCode] business model.
 *
 * If the row holds a media or a reason the model's enums do not name, an internal [BusinessException]
 * "mapper.validation_code.invalid_property" is thrown: a row this server wrote and cannot read back
 * is its own failure rather than the caller's.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class ValidationCodeMapper {

    abstract fun toValidationCode(entity: ValidationCodeEntity): ValidationCode

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
        cause: Throwable
    ): BusinessException {
        return internalBusinessExceptionOf(
            detailsId = "mapper.validation_code.invalid_property",
            throwable = cause,
            values = arrayOf("property" to invalidProperty)
        )
    }
}
