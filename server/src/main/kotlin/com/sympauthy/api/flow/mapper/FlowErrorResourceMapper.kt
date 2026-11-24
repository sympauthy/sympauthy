package com.sympauthy.api.flow.mapper

import com.sympauthy.api.flow.resource.ClientReferenceResource
import com.sympauthy.api.flow.resource.FlowErrorResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.exception.mapper.LocalizedErrorMapper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.util.*

@Singleton
class FlowErrorResourceMapper(
    @Inject private val localizedErrorMapper: LocalizedErrorMapper
) {

    fun toResource(
        exception: BusinessException,
        locale: Locale,
        client: ClientReferenceResource?,
        cancelUrl: String?
    ): FlowErrorResource {
        val localizedError = localizedErrorMapper.toLocalizedError(exception, locale)
        return FlowErrorResource(
            errorCode = localizedError.errorCode,
            description = localizedError.description,
            details = localizedError.details,
            client = client,
            cancelUrl = cancelUrl,
        )
    }

    fun toResource(redirectUri: URI): FlowErrorResource {
        return FlowErrorResource(
            redirectUrl = redirectUri.toString(),
        )
    }
}
