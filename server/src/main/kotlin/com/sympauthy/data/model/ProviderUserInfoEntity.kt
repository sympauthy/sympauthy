package com.sympauthy.data.model

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Embeddable
data class ProviderUserInfoEntityId(
    @MappedProperty("provider_id") val providerId: String,
    @MappedProperty("user_id") val userId: UUID,
)

@Serdeable
@MappedEntity("provider_user_info")
data class ProviderUserInfoEntity(
    @EmbeddedId val id: ProviderUserInfoEntityId,
    val fetchDate: LocalDateTime,
    val changeDate: LocalDateTime,

    val subject: String,

    val name: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val middleName: String? = null,
    val nickname: String? = null,

    val preferredUsername: String? = null,
    val profile: String? = null,
    val picture: String? = null,
    val website: String? = null,

    val email: String? = null,
    val emailVerified: Boolean? = null,

    val gender: String? = null,
    val birthDate: LocalDate? = null,

    val zoneInfo: String? = null,
    val locale: String? = null,

    val phoneNumber: String? = null,
    val phoneNumberVerified: Boolean? = null,

    val streetAddress: String? = null,
    val locality: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val country: String? = null,

    val updatedAt: LocalDateTime? = null
)
