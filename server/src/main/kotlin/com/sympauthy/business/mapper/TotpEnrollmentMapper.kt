package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.data.model.TotpEnrollmentEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [TotpEnrollmentEntity] to the [TotpEnrollment] business model.
 *
 * If the row holds no identifier, an internal [BusinessException] "mapper.totp_enrollment.invalid_property"
 * is thrown.
 */
@Singleton
class TotpEnrollmentMapper {

    fun toTotpEnrollment(entity: TotpEnrollmentEntity): TotpEnrollment {
        return TotpEnrollment(
            id = entity.id ?: throw internalBusinessExceptionOf(
                detailsId = "mapper.totp_enrollment.invalid_property",
                values = arrayOf("property" to "id")
            ),
            userId = entity.userId,
            secret = entity.secret.copyOf(),
            creationDate = entity.creationDate,
            confirmedDate = entity.confirmedDate,
        )
    }
}
