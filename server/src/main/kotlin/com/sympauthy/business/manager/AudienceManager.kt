package com.sympauthy.business.manager

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.config.model.AudiencesConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class AudienceManager(
    @Inject private val audiencesConfig: AudiencesConfig
) {

    fun listAudiences(): List<Audience> {
        return audiencesConfig.orThrow().audiences
    }

    fun findAudienceByIdOrNull(id: String): Audience? {
        return listAudiences().firstOrNull { it.id == id }
    }
}
