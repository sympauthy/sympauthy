package com.sympauthy.api.mapper.flow

import com.sympauthy.api.mapper.config.OutputResourceMapperConfig
import com.sympauthy.api.resource.flow.ClaimsValidationFlowResultResource
import com.sympauthy.api.resource.flow.ResendClaimsValidationCodeResultResource
import com.sympauthy.api.resource.flow.ValidationCodeResource
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import org.mapstruct.Mapper
import java.net.URI

@Mapper(
    config = OutputResourceMapperConfig::class
)
abstract class ClaimsValidationFlowResultResourceMapper {

    fun toFlowResultResource(validationCode: ValidationCode): ClaimsValidationFlowResultResource {
        return ClaimsValidationFlowResultResource(
            media = validationCode.media.name,
            code = toResource(validationCode)
        )
    }

    fun toFlowResultResource(
        media: ValidationCodeMedia,
        redirectUri: URI,
    ): ClaimsValidationFlowResultResource {
        return ClaimsValidationFlowResultResource(
            media = media.name,
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

    abstract fun toResource(validationCode: ValidationCode): ValidationCodeResource
}
