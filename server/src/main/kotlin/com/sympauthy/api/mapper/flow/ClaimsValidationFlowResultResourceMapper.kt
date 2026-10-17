package com.sympauthy.api.mapper.flow

import com.sympauthy.api.resource.flow.ClaimsValidationFlowResource
import com.sympauthy.api.resource.flow.ResendClaimsValidationCodeResultResource
import com.sympauthy.api.resource.flow.ValidationCodeResource
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.util.wireName
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class ClaimsValidationFlowResultResourceMapper {

    fun toFlowResource(validationCode: ValidationCode): ClaimsValidationFlowResource {
        return ClaimsValidationFlowResource(
            media = validationCode.media.wireName,
            code = toResource(validationCode),
            redirectUrl = null,
        )
    }

    fun toFlowResource(
        media: ValidationCodeMedia,
        redirectUri: URI,
    ): ClaimsValidationFlowResource {
        return ClaimsValidationFlowResource(
            media = media.wireName,
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
            media = media.wireName,
            resent = resent,
            code = newValidationCode?.let(this::toResource)
        )
    }

    fun toResource(validationCode: ValidationCode): ValidationCodeResource {
        return ValidationCodeResource(
            id = validationCode.id.toString(),
            media = validationCode.media.wireName,
            reasons = validationCode.reasons.map { it.wireName },
            resendDate = validationCode.resendDate,
        )
    }
}
