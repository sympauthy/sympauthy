package com.sympauthy.business.manager.user

import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimDataType.*
import com.sympauthy.exception.localizedExceptionOf
import jakarta.inject.Singleton
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Component in charge of validating and cleaning claim value received from clients.
 */
@Singleton
class ClaimValueValidator {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd")

    /**
     * Validate the [value] provided can be assigned to the [claim] and return a cleaned [value].
     *
     * The [value] is not valid for the [claim] if:
     * - the type of [value] do not match the [ClaimDataType.typeClass] expected by the [Claim.dataType].
     * -
     */
    fun validateAndCleanValueForClaim(claim: Claim, value: Any?): Optional<Any> {
        if (value != null && claim.dataType.typeClass != value::class) {
            throw localizedExceptionOf(
                "claim.validate.invalid_type", "claim" to claim, "type" to claim.dataType
            )
        }
        return when (value) {
            null -> Optional.empty()
            is String -> validateAndCleanStringForClaim(claim, value)
            else -> throw localizedExceptionOf(
                "claim.validate.unsupported_type", "claim" to claim
            )
        }
    }

    internal fun validateAndCleanStringForClaim(claim: Claim, value: String): Optional<Any> {
        val trimmedValue = value.trim()
        if (value.isBlank()) {
            return Optional.empty()
        }
        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        return when (claim.dataType) {
            DATE -> validateDateForClaim(value)
            EMAIL -> validateEmailForClaim(value)
            PHONE_NUMBER -> validatePhoneNumberForClaim(value)
            STRING -> Optional.of(trimmedValue)
            else -> throw IllegalArgumentException("Claim of type ${claim.dataType} do not expect string value.}")
        }
    }

    internal fun validateDateForClaim(value: String): Optional<Any> {
        try {
            dateFormat.parse(value)
        } catch (e: ParseException) {
            throw throw localizedExceptionOf("claim.validate.invalid_date")
        }
        return Optional.of(value)
    }

    /**
     * Validate the [value] is an email.
     *
     * According to the [OpenID](https://openid.net/specs/openid-connect-core-1_0.html#Claims), the email claim MUST
     * conform to the
     * [RFC5322 Addr-Spec Specification](https://www.rfc-editor.org/rfc/rfc5322.html#section-3.4.1).
     *
     * However, for simplicity, we will only validate the value:
     * - contains a single '@' characters.
     * - it separates 2 non-empty parts.
     */
    internal fun validateEmailForClaim(value: String): Optional<Any> {
        val parts = value.split("@")
        if (parts.size != 2 || parts.getOrNull(0).isNullOrBlank() || parts.getOrNull(1).isNullOrBlank()) {
            throw localizedExceptionOf("claim.validate.invalid_email")
        }
        return Optional.of(value)
    }

    internal fun validatePhoneNumberForClaim(value: String): Optional<Any> {
        return Optional.of(value) // FIXME
    }
}
