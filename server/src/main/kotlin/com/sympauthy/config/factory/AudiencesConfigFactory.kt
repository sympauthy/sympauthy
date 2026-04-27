package com.sympauthy.config.factory

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.AudiencesConfig
import com.sympauthy.config.model.DisabledAudiencesConfig
import com.sympauthy.config.model.EnabledAudiencesConfig
import com.sympauthy.config.properties.AudienceConfigurationProperties
import com.sympauthy.config.properties.AudienceConfigurationProperties.Companion.AUDIENCES_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AudiencesConfigFactory(
    @Inject private val parser: ConfigParser
) {

    @Singleton
    fun provideAudiences(
        propertiesList: List<AudienceConfigurationProperties>
    ): AudiencesConfig {
        val errors = mutableListOf<ConfigurationException>()

        if (propertiesList.isEmpty()) {
            errors.add(
                configExceptionOf(
                    AUDIENCES_KEY,
                    "config.audiences.empty"
                )
            )
            return DisabledAudiencesConfig(errors)
        }

        val audiences = propertiesList.mapNotNull { properties ->
            parseAudience(properties, errors)
        }

        // Validate uniqueness of audience IDs.
        val duplicateIds = audiences.groupBy { it.id }
            .filter { it.value.size > 1 }
            .keys
        for (duplicateId in duplicateIds) {
            errors.add(
                configExceptionOf(
                    "$AUDIENCES_KEY.$duplicateId",
                    "config.audiences.duplicate_id",
                    "audience" to duplicateId
                )
            )
        }

        return if (errors.isEmpty()) {
            EnabledAudiencesConfig(audiences)
        } else {
            DisabledAudiencesConfig(errors)
        }
    }

    private fun parseAudience(
        properties: AudienceConfigurationProperties,
        errors: MutableList<ConfigurationException>
    ): Audience? {
        val audienceErrors = mutableListOf<ConfigurationException>()

        val id = try {
            parser.getStringOrThrow(
                properties,
                "$AUDIENCES_KEY.${properties.id}",
                AudienceConfigurationProperties::id
            )
        } catch (e: ConfigurationException) {
            audienceErrors.add(e)
            null
        }

        val tokenAudience = try {
            parser.getString(
                properties,
                "$AUDIENCES_KEY.${properties.id}.token-audience",
                AudienceConfigurationProperties::tokenAudience
            )
        } catch (e: ConfigurationException) {
            audienceErrors.add(e)
            null
        }

        return if (audienceErrors.isEmpty()) {
            Audience(
                id = id!!,
                tokenAudience = tokenAudience ?: id
            )
        } else {
            errors.addAll(audienceErrors)
            null
        }
    }
}
