package com.sympauthy.api.operator.admin.mapper

import com.sympauthy.api.operator.admin.resource.AdminUserMfaMethodResource
import com.sympauthy.business.model.mfa.MfaMethodType
import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.platform.util.wireName
import jakarta.inject.Singleton

@Singleton
class AdminUserMfaMethodResourceMapper {

    /**
     * Publish [enrollment], which must be confirmed: one not confirmed yet is not a method the user
     * holds, and has no date it was registered at.
     */
    fun toResource(enrollment: TotpEnrollment): AdminUserMfaMethodResource {
        return AdminUserMfaMethodResource(
            mfaId = enrollment.id,
            type = MfaMethodType.TOTP.wireName,
            registeredAt = enrollment.confirmedDate
                ?: error("TOTP enrollment ${enrollment.id} is not confirmed and cannot be published."),
        )
    }
}
