package com.sympauthy.api.mapper

import com.sympauthy.api.resource.error.ErrorResource
import com.sympauthy.api.resource.error.PropertyErrorResource
import com.sympauthy.exception.LocalizedException
import com.sympauthy.exception.mapper.LocalizedErrorMapper
import com.sympauthy.exception.model.LocalizedPropertyError
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

@Singleton
class ErrorResourceMapper(
    @Inject private val localizedErrorMapper: LocalizedErrorMapper
) {

    fun toResource(
        exception: LocalizedException,
        locale: Locale
    ): ErrorResource {
        val localizedError = localizedErrorMapper.toLocalizedError(exception, locale)
        return ErrorResource(
            status = localizedError.httpStatus.code,
            errorCode = localizedError.errorCode,
            description = localizedError.description,
            details = localizedError.details,
            // A failure refusing no property in particular carries no key, rather than an empty list.
            properties = localizedError.properties.takeIf { it.isNotEmpty() }?.map(::toResource)
        )
    }

    private fun toResource(propertyError: LocalizedPropertyError) = PropertyErrorResource(
        path = propertyError.path,
        errorCode = propertyError.errorCode,
        description = propertyError.description,
        details = propertyError.details
    )
}
