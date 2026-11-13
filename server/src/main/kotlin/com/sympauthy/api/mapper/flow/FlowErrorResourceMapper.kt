package com.sympauthy.api.mapper.flow

import com.sympauthy.api.resource.flow.ClientReferenceResource
import com.sympauthy.api.resource.flow.FlowErrorResource
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
        client: ClientReferenceResource?
    ): FlowErrorResource {
        val localizedError = localizedErrorMapper.toLocalizedError(exception, locale)
        return FlowErrorResource(
            errorCode = localizedError.errorCode,
            description = localizedError.description,
            details = localizedError.details,
            client = client,
        )
    }

    fun toResource(redirectUri: URI): FlowErrorResource {
        return FlowErrorResource(
            redirectUrl = redirectUri.toString(),
        )
    }
}
