package com.sympauthy.business.manager.provider.oidc

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.user.RawProviderClaims
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Singleton
class ProviderIdTokenClaimsExtractor {

    fun extractClaims(providerId: String, idTokenClaims: Map<String, Any>): RawProviderClaims {
        val subject = idTokenClaims["sub"]?.toString()
            ?: throw businessExceptionOf(
                "provider.user_info.missing_subject",
                "providerId" to providerId
            )

        return RawProviderClaims(
            subject = subject,
            name = idTokenClaims["name"]?.toString(),
            givenName = idTokenClaims["given_name"]?.toString(),
            familyName = idTokenClaims["family_name"]?.toString(),
            middleName = idTokenClaims["middle_name"]?.toString(),
            nickname = idTokenClaims["nickname"]?.toString(),
            preferredUsername = idTokenClaims["preferred_username"]?.toString(),
            profile = idTokenClaims["profile"]?.toString(),
            picture = idTokenClaims["picture"]?.toString(),
            website = idTokenClaims["website"]?.toString(),
            email = idTokenClaims["email"]?.toString(),
            emailVerified = (idTokenClaims["email_verified"] as? Boolean),
            gender = idTokenClaims["gender"]?.toString(),
            birthDate = idTokenClaims["birthdate"]?.toString()?.let { parseDateOrNull(it) },
            zoneInfo = idTokenClaims["zoneinfo"]?.toString(),
            locale = idTokenClaims["locale"]?.toString(),
            phoneNumber = idTokenClaims["phone_number"]?.toString(),
            phoneNumberVerified = (idTokenClaims["phone_number_verified"] as? Boolean),
            updatedAt = (idTokenClaims["updated_at"] as? Number)?.toLong()?.let { epochSeconds ->
                LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.of("UTC"))
            }
        )
    }

    private fun parseDateOrNull(value: String): LocalDate? {
        return try {
            LocalDate.parse(value, DateTimeFormatter.ISO_DATE)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
