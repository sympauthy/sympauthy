package com.sympauthy.api.mapper.flow

import com.sympauthy.api.resource.flow.ClaimsValidationFlowResource
import com.sympauthy.api.resource.flow.ResendClaimsValidationCodeResultResource
import com.sympauthy.api.resource.flow.ValidationCodeResource
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class ClaimsValidationFlowResultResourceMapper {

    fun toFlowResource(validationCode: ValidationCode): ClaimsValidationFlowResource {
        return ClaimsValidationFlowResource(
            media = validationCode.media.name,
            code = toResource(validationCode),
            redirectUrl = null,
        )
    }

    fun toFlowResource(
        media: ValidationCodeMedia,
        redirectUri: URI,
    ): ClaimsValidationFlowResource {
        return ClaimsValidationFlowResource(
            media = media.name,
            code = null,
            redirectUrl = redirectUri.toString(),
        )
    }

    fun toResendResultResource(
        media: ValidationCodeMedia,
        resent: Boolean,
        newValidationCode: ValidationCode?
    ): ResendClaimsValidationCodeResultResource {
        return ResendClaimsValidationCodeResultResource(
            media = media.name,
            resent = resent,
            code = newValidationCode?.let(this::toResource)
        )
    }

    fun toResource(validationCode: ValidationCode): ValidationCodeResource {
        return ValidationCodeResource(
            id = validationCode.id.toString(),
            media = validationCode.media.name,
            reasons = validationCode.reasons.map { it.name },
            resendDate = validationCode.resendDate,
        )
    }
}
