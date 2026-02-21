package com.sympauthy.api.mapper

import com.sympauthy.api.resource.error.ErrorResource
import com.sympauthy.exception.LocalizedException
import com.sympauthy.exception.mapper.LocalizedErrorMapper
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
            properties = null
        )
    }
}
