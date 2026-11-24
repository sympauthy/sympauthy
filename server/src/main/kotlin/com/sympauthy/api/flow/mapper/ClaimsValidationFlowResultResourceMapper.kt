package com.sympauthy.api.flow.mapper

import com.sympauthy.api.flow.resource.ClaimsValidationFlowResource
import com.sympauthy.api.flow.resource.ClientReferenceResource
import com.sympauthy.api.flow.resource.ResendClaimsValidationCodeResultResource
import com.sympauthy.api.flow.resource.ValidationCodeResource
import com.sympauthy.business.validationcode.model.ValidationCode
import com.sympauthy.business.validationcode.model.ValidationCodeMedia
import com.sympauthy.platform.util.wireName
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class ClaimsValidationFlowResultResourceMapper {

    fun toFlowResource(
        validationCode: ValidationCode,
        client: ClientReferenceResource?,
        cancelUrl: String?
    ): ClaimsValidationFlowResource {
        return ClaimsValidationFlowResource(
            media = validationCode.media.wireName,
            code = toResource(validationCode),
            client = client,
            cancelUrl = cancelUrl,
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
