package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimDataType.*
import com.sympauthy.util.wireName
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Component in charge of validating and cleaning claim value received from end-users.
 *
 * A value submitted as a string is trimmed by [validateAndCleanStringForClaim] before its type is looked at,
 * so every check below it is given a value carrying no surrounding whitespace and none of them trims again.
 *
 * Beyond the trim, cleaning belongs to the claim's data type and runs beside that type's own validation: a
 * type with one canonical spelling answers with it rather than with what was submitted — an email
 * lowercased, a boolean folded, a number read as a [Long]. What this returns is what gets stored, and a
 * caller comparing against a stored value asks [cleanValueForClaimOrNull] for the same spelling.
 */
@Singleton
class ClaimValueValidator {

    companion object {
        private val E164_PATTERN = Regex("^\\+[0-9]{1,15}$")
    }

    /**
     * Validate the [value] submitted for [claim] and return it as the primitive
     * [ClaimDataType.typeClass] names, or an empty optional where the value is blank and the claim is
     * therefore being cleared.
     *
     * Every claim but a number one is submitted as a string, and one submitted as anything else throws
     * `user.claim_value_validator.invalid_type`. A number one may arrive as a string or as the number a JSON
     * body carries, and either way it is read as a number rather than type-checked, so a value that is not
     * one throws `user.claim_value_validator.invalid_number` whichever it arrived as. A value that does not
     * satisfy its own type throws the code that type is checked with, and one outside [Claim.allowedValues]
     * throws `user.claim_value_validator.invalid_value`.
     *
     * The allowed values are compared against the cleaned value rather than the submitted one, because the
     * two representations a number arrives in are one value and only the cleaned one is comparable to what
     * the configuration holds. Where cleaning changes a value at all — a string trimmed, a boolean
     * lowercased — that comparison also stops it being refused over what was about to be removed.
     */
    fun validateAndCleanValueForClaim(claim: Claim, value: Any?): Optional<Any> {
        val cleanedValue = when {
            value == null -> Optional.empty()
            value is String -> validateAndCleanStringForClaim(claim, value)
            claim.dataType == NUMBER -> validateAndCleanNumberForClaim(value)
            else -> throw recoverableBusinessExceptionOf(
                "user.claim_value_validator.invalid_type",
                "description.user.claim_value_validator.invalid_type",
                "claim" to claim.id,
                "type" to claim.dataType.wireName
            )
        }
        val cleaned = cleanedValue.orElse(null)
        if (claim.allowedValues != null && cleaned != null && !claim.allowedValues.contains(cleaned)) {
            throw recoverableBusinessExceptionOf(
                "user.claim_value_validator.invalid_value",
                "description.user.claim_value_validator.invalid_value"
            )
        }
        return cleanedValue
    }

    /**
     * The [value] as [claim] holds it, or null where [claim] could hold no such value at all — it does not
     * satisfy the claim's own type, it falls outside [Claim.allowedValues], or it carries nothing but
     * whitespace and so is no value.
     *
     * This is [validateAndCleanValueForClaim] for a caller comparing rather than writing. A stored value
     * was cleaned on the way in, so a caller looking one up has to clean what it offers the same way or it
     * asks for a spelling no row was ever written in. What that caller cannot do is fail: a value the claim
     * would refuse matches no row of it either, since every row of it holds a value this validator accepted,
     * so being unable to clean it is an answer of none rather than a refusal to raise.
     */
    fun cleanValueForClaimOrNull(claim: Claim, value: Any?): Any? = try {
        validateAndCleanValueForClaim(claim, value).getOrNull()
    } catch (_: BusinessException) {
        null
    }

    /**
     * Validate the [value] submitted for [claim] against the claim's own type and return it cleaned, or an
     * empty optional where it holds nothing but whitespace and the claim is therefore being cleared.
     *
     * The value is trimmed here, once, and every check below is given the trimmed one. Whitespace a person
     * typed around a value is never part of the value: an email or a date padded with it would otherwise be
     * stored padded, and a phone number or a time zone refused for a reason that names the wrong thing. It
     * settles that a `string` claim cannot hold a deliberately padded value, which nothing asks for and which
     * would want a type saying so rather than this one keeping the padding by omission.
     */
    internal fun validateAndCleanStringForClaim(claim: Claim, value: String): Optional<Any> {
        val trimmedValue = value.trim()
        if (trimmedValue.isEmpty()) {
            return Optional.empty()
        }
        return when (claim.dataType) {
            BOOLEAN -> validateBooleanForClaim(trimmedValue)
            DATE -> validateDateForClaim(trimmedValue)
            EMAIL -> validateEmailForClaim(trimmedValue)
            NUMBER -> validateAndCleanNumberForClaim(trimmedValue)
            PHONE_NUMBER -> validatePhoneNumberForClaim(trimmedValue)
            STRING -> Optional.of(trimmedValue)
            TIMEZONE -> validateTimeZoneForClaim(trimmedValue)
        }
    }

    /**
     * Validate the [value] is a number and return it as a [Long].
     *
     * A number claim is a [Long] everywhere else in this server — it is the type its column is read back as
     * and the type its configured allowed values are parsed into — so a value carrying a fraction or one past
     * the range of a [Long] throws `user.claim_value_validator.invalid_number` rather than being rounded or
     * truncated into one.
     *
     * What decides that is the number itself and not how it happened to be boxed on the way in. A body
     * carrying `42.0` and one carrying `42` are the same whole number, and a client that spells it either
     * way should not be refused by whichever type the JSON parser reached for.
     */
    internal fun validateAndCleanNumberForClaim(value: Any): Optional<Any> {
        val text = value.toString()
        val number = text.toLongOrNull()
            ?: text.toBigDecimalOrNull()?.toLongOrNull()
            ?: throw recoverableBusinessExceptionOf(
                "user.claim_value_validator.invalid_number",
                "description.user.claim_value_validator.invalid_number"
            )
        return Optional.of(number)
    }

    internal fun validateBooleanForClaim(value: String): Optional<Any> {
        val normalized = value.lowercase()
        if (normalized != "true" && normalized != "false") {
            throw recoverableBusinessExceptionOf(
                "user.claim_value_validator.invalid_boolean",
                "description.user.claim_value_validator.invalid_boolean"
            )
        }
        return Optional.of(normalized)
    }

    /**
     * Validate the [value] is a date, and return it as the `yyyy-MM-dd` it was submitted as.
     *
     * A date claim is exchanged and stored as that string rather than as a date, so a value the parser
     * accepts is one a client reads back. It is parsed strictly and in full: a lenient parser rolls the
     * thirty-first of February into March and reads a date off the front of a value carrying anything
     * after it, and either way what is stored is the value as submitted rather than what was parsed.
     */
    internal fun validateDateForClaim(value: String): Optional<Any> {
        try {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: DateTimeParseException) {
            throw recoverableBusinessExceptionOf(
                "user.claim_value_validator.invalid_date",
                "description.user.claim_value_validator.invalid_date"
            )
        }
        return Optional.of(value)
    }

    /**
     * Validate the [value] is an email, and return it lowercased.
     *
     * According to the [OpenID](https://openid.net/specs/openid-connect-core-1_0.html#Claims), the email claim MUST
     * conform to the
     * [RFC5322 Addr-Spec Specification](https://www.rfc-editor.org/rfc/rfc5322.html#section-3.4.1).
     *
     * However, for simplicity, we will only validate the value:
     * - contains a single '@' characters.
     * - it separates 2 non-empty parts.
     *
     * The whole address is folded, and not only the domain RFC 5321 makes case-insensitive. The local part
     * is the receiving host's business and two spellings of it may in principle be two mailboxes, but none
     * of the providers a deployment will meet treats them as two — and a server that honoured the
     * distinction would refuse to recognise a person who capitalised their own name at sign-in. An address
     * is compared exactly as it is stored, so what is not folded here is two identities that never meet.
     */
    internal fun validateEmailForClaim(value: String): Optional<Any> {
        val parts = value.split("@")
        if (parts.size != 2 || parts.getOrNull(0).isNullOrBlank() || parts.getOrNull(1).isNullOrBlank()) {
            throw recoverableBusinessExceptionOf(
                "user.claim_value_validator.invalid_email",
                "description.user.claim_value_validator.invalid_email"
            )
        }
        return Optional.of(value.lowercase())
    }

    /**
     * Validate the [value] is a phone number.
     *
     * According to the
     * [OpenID Connect Core specification](https://openid.net/specs/openid-connect-core-1_0.html#Claims),
     * the phone_number claim MUST conform to [E.164](https://www.itu.int/rec/T-REC-E.164-201011-I/en) format.
     *
     * E.164 numbers start with a '+' prefix followed by up to 15 digits.
     */
    internal fun validatePhoneNumberForClaim(value: String): Optional<Any> {
        if (!E164_PATTERN.matches(value)) {
            throw recoverableBusinessExceptionOf(
                "user.claim_value_validator.invalid_phone_number",
                "description.user.claim_value_validator.invalid_phone_number"
            )
        }
        return Optional.of(value)
    }

    internal fun validateTimeZoneForClaim(value: String): Optional<Any> {
        try {
            ZoneId.of(value)
        } catch (_: DateTimeException) {
            throw recoverableBusinessExceptionOf(
                "user.claim_value_validator.invalid_time_zone",
                "description.user.claim_value_validator.invalid_time_zone"
            )
        }
        return Optional.of(value)
    }
}

/**
 * This decimal as a [Long], or null where it carries a fraction or does not fit in one.
 */
private fun BigDecimal.toLongOrNull(): Long? = try {
    toBigIntegerExact().longValueExact()
} catch (_: ArithmeticException) {
    null
}
