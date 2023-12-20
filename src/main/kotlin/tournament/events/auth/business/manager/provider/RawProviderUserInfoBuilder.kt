package tournament.events.auth.business.manager.provider

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import tournament.events.auth.business.exception.businessExceptionOf
import tournament.events.auth.business.model.provider.EnabledProvider
import tournament.events.auth.business.model.provider.ProviderUserInfoPathKey
import tournament.events.auth.business.model.provider.ProviderUserInfoPathKey.*
import tournament.events.auth.business.model.provider.RawProviderUserInfo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class RawProviderUserInfoBuilder {
    var subject: String? = null
    var name: String? = null
    var givenName: String? = null
    var familyName: String? = null
    var middleName: String? = null
    var nickname: String? = null
    var preferredUsername: String? = null
    var profile: String? = null
    var picture: String? = null
    var website: String? = null
    var email: String? = null
    var emailVerified: Boolean? = null
    var gender: String? = null
    var birthDate: LocalDate? = null
    var zoneInfo: String? = null
    var locale: String? = null
    var phoneNumber: String? = null
    var phoneNumberVerified: Boolean? = null
    var updatedAt: LocalDateTime? = null

    fun withUserInfo(
        document: DocumentContext,
        pathKey: ProviderUserInfoPathKey,
        path: JsonPath
    ): RawProviderUserInfoBuilder {
        when (pathKey) {
            SUB -> subject = readString(document, path)
            NAME -> name = readString(document, path)
            GIVEN_NAME -> givenName = readString(document, path)
            FAMILY_NAME -> familyName = readString(document, path)
            MIDDLE_NAME -> middleName = readString(document, path)
            NICKNAME -> nickname = readString(document, path)
            PREFERRED_USERNAME -> preferredUsername = readString(document, path)
            PROFILE -> profile = readString(document, path)
            PICTURE -> picture = readString(document, path)
            WEBSITE -> website = readString(document, path)
            EMAIL -> email = readString(document, path)
            EMAIL_VERIFIED -> emailVerified = readBoolean(document, path)
            GENDER -> gender = readString(document, path)
            BIRTH_DATE -> birthDate = readDate(document, path)
            ZONE_INFO -> zoneInfo = readString(document, path)
            LOCALE -> locale = readString(document, path)
            PHONE_NUMBER -> phoneNumber = readString(document, path)
            PHONE_NUMBER_VERIFIED -> phoneNumberVerified = readBoolean(document, path)
            UPDATED_AT -> updatedAt = readUpdatedAt(document, path)
        }
        return this
    }

    private fun readString(document: DocumentContext, path: JsonPath): String? {
        return document.read<String>(path)
    }

    private fun readBoolean(document: DocumentContext, path: JsonPath): Boolean? {
        return document.read<Boolean>(path)
    }

    private fun readDate(document: DocumentContext, path: JsonPath): LocalDate? {
        return document.read<String>(path)
            ?.let {
                try {
                    LocalDate.parse(it, DateTimeFormatter.ISO_DATE)
                } catch (e: DateTimeParseException) {
                    null
                }
            }
    }

    private fun readUpdatedAt(document: DocumentContext, path: JsonPath): LocalDateTime? {
        return document.read<Long>(path)
            ?.let(Instant::ofEpochMilli)
            ?.let { LocalDateTime.ofInstant(it, ZoneId.of("UTC")) }
    }

    fun build(provider: EnabledProvider): RawProviderUserInfo {
        return RawProviderUserInfo(
            subject = subject ?: throw businessExceptionOf(
                INTERNAL_SERVER_ERROR, "provider.user_info.missing_subject",
                "providerId" to provider.id
            ),
            name = name,
            givenName = givenName,
            familyName = familyName,
            middleName = middleName,
            nickname = nickname,
            preferredUsername = preferredUsername,
            profile = profile,
            picture = picture,
            website = website,
            email = email,
            emailVerified = emailVerified,
            gender = gender,
            birthDate = birthDate,
            zoneInfo = zoneInfo,
            locale = locale,
            phoneNumber = phoneNumber,
            phoneNumberVerified = phoneNumberVerified,
            updatedAt = updatedAt
        )
    }
}
